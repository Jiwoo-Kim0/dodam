# 모니터링 · 부하 테스트 지표 (S15P21D105-693)

작성일: 2026-09-22. 부하 테스트(구동재 215 / 정현진 317, 수·토)의 기준 지표와 수집 경로를 정리한다.

## 1. 수집 경로

| 대상 | 수집기 | 잡 이름 | 보는 것 |
| --- | --- | --- | --- |
| 호스트 | node-exporter | `node` | CPU, 메모리, 디스크, 네트워크 |
| 애플리케이션 | backend `/actuator/prometheus` | `backend` | JVM heap·GC·스레드, **HikariCP 풀**, **HTTP 지연·오류**, Lettuce(Redis) 명령 |
| MySQL 서버 | mysqld-exporter | `mysql` | 슬로우 쿼리, 커넥션, InnoDB 락 대기·버퍼풀 |
| Redis 서버 | redis-exporter | `redis` | 메모리, eviction, hit/miss, 명령 처리량 |

보관 기간은 Prometheus `--storage.tsdb.retention.time=15d`.

### backend 지표는 2026-09-22까지 한 번도 수집되지 않았다

`prometheus.yml`에 `job: backend`가 Day 6부터 있었지만 `up{job="backend"}`는 계속 **0**이었다.
세 곳이 동시에 막혀 있었다.

1. `micrometer-registry-prometheus` 의존성 없음 → 엔드포인트 자체가 생기지 않는다
2. `management.endpoints.web.exposure.include: health` → 노출되지 않는다
3. `SecurityConfig`가 `/actuator/prometheus`를 인증 대상으로 잡음 → **401**

**설정 파일만 보면 정상으로 보였다.** 실제로 긁히는지는 `up` 값을 봐야 안다.
회귀 방지 테스트는 `HealthApiIntegrationTest.prometheusEndpointIsScrapableWithoutAuthentication()`.

> 테스트에는 `@AutoConfigureObservability`가 필요하다. Spring Boot는 테스트에서 메트릭
> 익스포터를 기본으로 끄기 때문에, 없으면 운영에서 정상인 엔드포인트가 테스트에서만 404가 난다.

### 외부 노출

`/actuator/`는 nginx가 `location ^~ /actuator/ { return 404; }`로 막는다(2026-09-22 실제 응답 확인).
exporter 2종도 외부 포트를 열지 않고 `app-net` 안에서만 접근된다.

## 2. mysqld-exporter 전용 계정

앱 계정(`MYSQL_USER`)을 쓰지 않는다. 지표 수집에 필요한 권한만 준다 — **앱 데이터에 대한 SELECT는 없다.**

```sql
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY '<MYSQL_EXPORTER_PASSWORD>'
  WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'exporter'@'%';
GRANT SELECT ON performance_schema.* TO 'exporter'@'%';
FLUSH PRIVILEGES;
```

비밀번호는 `infra/.env`의 `MYSQL_EXPORTER_PASSWORD`. 2026-09-22 생성 완료(28자 무작위).
`MAX_USER_CONNECTIONS 3`은 수집기가 부하 중에 커넥션을 잠식하지 않게 하는 상한이다.

## 3. 부하 테스트 기준 지표

측정은 **부하 전 / 부하 중 / 개선 후** 세 시점에서 같은 쿼리로 뜬다. 버전을 함께 기록한다.

| 기준 | PromQL | 임계 |
| --- | --- | --- |
| 호스트 CPU | `100 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m]))*100` | 80% 지속 시 포화 |
| 호스트 메모리 | `node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes` | 15% 미만 위험 |
| JVM heap | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}` | 90% 지속 + GC 증가 = 힙 부족 |
| GC 정지 | `rate(jvm_gc_pause_seconds_sum[1m])` | 초당 0.1s 초과면 응답 지연 원인 |
| **DB 풀 대기** | `hikaricp_connections_pending` | **0 초과가 지속되면 풀 고갈 = 1순위 병목 신호** |
| DB 풀 사용 | `hikaricp_connections_active / hikaricp_connections_max` | 90% 지속 시 상한 검토 |
| MySQL 슬로우 | `rate(mysql_global_status_slow_queries[1m])` | 증가 시 쿼리·인덱스 문제 |
| MySQL 커넥션 | `mysql_global_status_threads_connected` | `max_connections` 대비 |
| Redis 메모리 | `redis_memory_used_bytes / redis_config_maxmemory` | 100%면 eviction 시작 |
| Redis eviction | `rate(redis_evicted_keys_total[1m])` | 0 초과 = 캐시가 밀려나는 중 |
| **오류율** | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))` | **1% 초과 시 중단하고 원인 확인** |
| **지연 p95** | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, uri))` | 엔드포인트별 비교 |
| 지연 p99 | 위에서 0.99 | 꼬리 지연 |

`http.server.requests`에 히스토그램·SLO 버킷(100ms~5s)을 켜 두었다. 평균만으로는 **느린 소수 요청이
평균에 묻혀** 개선 전후 차이가 보이지 않는다.

## 4. 측정 절차

1. **부하 전 스냅샷** — 위 지표를 1회 기록하고 배포 커밋 해시를 같이 적는다
2. **DB 적재와 부하 실행 시간대를 분리한다** — seed 적재 중에 측정하면 디스크·CPU가 적재 쪽에
   먹혀 부하 결과가 오염된다
3. **부하 중** — 오류율 1% 초과 또는 `hikaricp_connections_pending` 지속 상승이면 중단하고 원인부터 본다
4. **개선 후 재측정** — 같은 쿼리, 같은 시간창(1m rate), 새 커밋 해시 기록
5. 전후 결과 + 커밋 해시 + 병목 원인·잔여 한계를 PPT(315)에 전달

**결과와 버전을 반드시 같이 적는다.** 어떤 코드에서 난 수치인지 없으면 비교가 성립하지 않는다.

## 5. 남은 한계

- **Grafana 대시보드가 이 지표들에 맞춰져 있지 않다.** 백엔드 지표가 없던 동안 만들어진 것이라
  JVM·Hikari·MySQL 패널이 없다. 부하 테스트 전에 패널을 추가하거나 Explore로 직접 쿼리한다.
- 애플리케이션 로그는 Loki에 있으나 **오류율 지표와 로그를 잇는 상관 ID가 없다.** 5xx가 늘면
  시간대로 로그를 찾아야 한다.
- 부하 생성 도구·시나리오는 215·317 담당이 정한다. 이 문서는 관측 쪽만 다룬다.
