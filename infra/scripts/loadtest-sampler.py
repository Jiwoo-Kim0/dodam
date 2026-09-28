#!/usr/bin/env python3
"""부하 실행 중 서버 측 지표를 5초마다 찍는다.

k6 요약은 클라이언트 관점(지연·처리량)만 준다. 어디서 막혔는지는 서버 측
gauge(pending·busy)를 봐야 알 수 있는데, Grafana는 rate 창이 5m이라 1분짜리
계단이 평활화된다. 그래서 직접 찍는다.

컨테이너별 CPU는 cAdvisor가 docker.sock 없이 도는 탓에 `name` 라벨이 없고
`container_id`만 준다. 매 틱 `docker ps`로 id→이름을 다시 읽어 이름을 붙인다.
매번 읽는 이유는 부하 도구 컨테이너가 샘플러보다 늦게 뜨기 때문이다(#4에서는
실행이 끝난 뒤 사라진 id를 역산해야 했다).
"""
import json, subprocess, sys, time, urllib.parse

COMPOSE = ["docker", "compose", "exec", "-T", "prometheus", "wget", "-qO-"]

METRICS = [
    ("pending",  'sum(hikaricp_connections_pending)'),
    ("active",   'sum(hikaricp_connections_active)'),
    ("busy",     'sum(tomcat_threads_busy_threads)'),
    ("heapMB",   'sum(jvm_memory_used_bytes{area="heap"})/1024/1024'),
    ("gc_s",     'sum(rate(jvm_gc_pause_seconds_sum[1m]))'),
    ("cpu_host", '100-avg(rate(node_cpu_seconds_total{mode="idle"}[1m]))*100'),
    ("mysql_run",'mysql_global_status_threads_running'),
    ("rps",      'sum(rate(http_server_requests_seconds_count[1m]))'),
    ("p95ms",    'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))*1000'),
]
CPU_BY_ID = 'sum by (container_id) (rate(container_cpu_usage_seconds_total{container_id!=""}[1m]))'

def fetch(promql):
    url = "http://localhost:9090/api/v1/query?query=" + urllib.parse.quote(promql)
    try:
        out = subprocess.run(COMPOSE + [url], capture_output=True, text=True, timeout=10).stdout
        return json.loads(out)["data"]["result"]
    except Exception:
        return []

def scalar(promql):
    r = fetch(promql)
    return round(float(r[0]["value"][1]), 3) if r else None

def names():
    """id(12자) → 컨테이너 이름. 매 틱 갱신해 늦게 뜬 컨테이너도 잡는다."""
    try:
        out = subprocess.run(["docker", "ps", "--format", "{{.ID}} {{.Names}}"],
                             capture_output=True, text=True, timeout=10).stdout
        return dict(l.split(None, 1) for l in out.strip().splitlines() if l)
    except Exception:
        return {}

def fmt(v):
    return "-" if v is None else str(v)

duration = int(sys.argv[1]) if len(sys.argv) > 1 else 300
print("t    " + " ".join(f"{n:>9}" for n, _ in METRICS) + "   | CPU 상위(코어)", flush=True)
start = time.time()
while time.time() - start < duration:
    row = [scalar(expr) for _, expr in METRICS]
    mapping = names()
    tops = []
    for r in fetch(CPU_BY_ID):
        cid = r["metric"].get("container_id", "")
        val = float(r["value"][1])
        if val >= 0.05:
            tops.append((val, mapping.get(cid, cid[:8])))
    tops.sort(reverse=True)
    top_s = " ".join(f"{n}={v:.2f}" for v, n in tops[:4])
    t = int(time.time() - start)
    print(f"{t:<5}" + " ".join(f"{fmt(v):>9}" for v in row) + f"   | {top_s}", flush=True)
    time.sleep(max(0, 5 - (time.time() - start) % 5))
