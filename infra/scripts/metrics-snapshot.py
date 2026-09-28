#!/usr/bin/env python3
"""부하 테스트 기준선 스냅샷 (S15P21D105-693). Prometheus 컨테이너를 거쳐 질의한다."""
import json, subprocess, urllib.parse, sys, datetime

COMPOSE = ["docker", "compose", "-f", "/home/ubuntu/app/infra/docker-compose.yml",
           "--env-file", "/home/ubuntu/app/infra/.env"]

QUERIES = [
    ("호스트 CPU 사용률", "%",   '100 - avg(rate(node_cpu_seconds_total{mode="idle"}[5m]))*100'),
    ("호스트 메모리 여유", "%",  'node_memory_MemAvailable_bytes/node_memory_MemTotal_bytes*100'),
    # heap 사용률(%)을 쓰지 않는다. 분모인 sum(jvm_memory_max_bytes)는 GC가 Eden/Survivor/
    # Tenured 크기를 런타임에 조정하면서 측정마다 달라진다(-XX:MaxHeapSize=1g 고정인데도).
    # 분모가 움직이면 실제 사용량이 늘어도 %가 내려갈 수 있어 오히려 오해를 만든다.
    # 절대값(used)과 JVM이 확보한 양(committed)을 따로 본다.
    ("JVM heap 사용", "MB",      'sum(jvm_memory_used_bytes{area="heap"})/1024/1024'),
    ("JVM heap 확보", "MB",      'sum(jvm_memory_committed_bytes{area="heap"})/1024/1024'),
    ("JVM heap 상한(설정)", "MB", 'sum(jvm_memory_max_bytes{area="heap"})/1024/1024'),
    ("GC 정지", "초/초",         'sum(rate(jvm_gc_pause_seconds_sum[5m]))'),
    ("DB풀 대기(pending)", "",   'sum(hikaricp_connections_pending)'),
    ("DB풀 active", "",          'sum(hikaricp_connections_active)'),
    ("DB풀 max", "",             'max(hikaricp_connections_max)'),
    # 톰캣 스레드는 server.tomcat.mbeanregistry.enabled=true 가 있어야 수집된다.
    # 없으면 "데이터 없음"으로 찍히는데, 부하가 0이라서가 아니라 지표가 아예 없는 것이다.
    ("톰캣 busy 스레드", "",     'sum(tomcat_threads_busy_threads)'),
    ("톰캣 스레드 max", "",      'max(tomcat_threads_config_max_threads)'),
    ("MySQL 슬로우쿼리", "/초",   'rate(mysql_global_status_slow_queries[5m])'),
    ("MySQL threads_connected", "", 'mysql_global_status_threads_connected'),
    ("MySQL max_connections", "", 'mysql_global_variables_max_connections'),
    ("Redis 메모리", "MB",       'redis_memory_used_bytes/1024/1024'),
    ("Redis eviction", "/초",    'rate(redis_evicted_keys_total[5m])'),
    ("HTTP 요청 누적", "건",      'sum(http_server_requests_seconds_count)'),
    # 5xx가 한 건도 없으면 분자 시리즈 자체가 없어 결과가 비어 나온다("데이터 없음").
    # `or vector(0)`으로 0%로 읽히게 한다 — 부하 중에 "오류 없음"과 "측정 실패"가 구분돼야 한다.
    ("5xx 오류율", "%",          '(sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) or vector(0))/sum(rate(http_server_requests_seconds_count[5m]))*100'),
    ("지연 p95", "초",           'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))'),
    ("지연 p99", "초",           'histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))'),
]

def query(promql):
    url = "http://localhost:9090/api/v1/query?query=" + urllib.parse.quote(promql)
    try:
        out = subprocess.run(COMPOSE + ["exec", "-T", "prometheus", "wget", "-qO-", url],
                             capture_output=True, text=True, timeout=20).stdout
        result = json.loads(out)["data"]["result"]
    except Exception as exc:
        return "측정 실패(%s)" % type(exc).__name__
    if not result:
        return "데이터 없음"
    vals = [r["value"][1] for r in result]
    nums = [float(v) for v in vals if v not in ("NaN", "+Inf", "-Inf")]
    if not nums:
        return "NaN(트래픽 없음)"
    return " / ".join("%.4g" % n for n in nums)

commit = subprocess.run(["git", "-C", "/home/ubuntu/app", "log", "--oneline", "-1"],
                        capture_output=True, text=True).stdout.strip()
print("측정 시각 : " + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S KST"))
print("배포 커밋 : " + commit)
print()
print("| 지표 | 값 | 단위 |")
print("| --- | --- | --- |")
for name, unit, q in QUERIES:
    print("| %s | %s | %s |" % (name, query(q), unit))
