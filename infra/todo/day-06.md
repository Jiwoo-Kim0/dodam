# Day 6 — 모니터링 스택: Prometheus / Grafana / Loki (P2)

일정이 밀렸다면 이 Day를 가장 먼저 축소한다 (`00-overview.md` 2번 우선순위 참고). 최소 목표는 "컨테이너가 죽었는지, 디스크/메모리가 찼는지 정도는 알 수 있게" 하는 것.

## 체크리스트

### 1. Prometheus (리소스 모니터링)
- [ ] `prometheus` 컨테이너 추가, `infra/monitoring/prometheus/prometheus.yml` 작성
- [ ] `cadvisor` 또는 `node-exporter` 추가해 EC2 자체 리소스(CPU/메모리/디스크) 수집
- [ ] Spring Boot Actuator `/actuator/prometheus`(Micrometer) 수집 대상으로 등록 — 백엔드팀이 `micrometer-registry-prometheus` 의존성 추가 필요 (**사용자 액션: 백엔드팀에 요청**)
- [ ] MySQL exporter 추가 여부는 시간 되면(P2 중에서도 후순위)

### 2. Grafana (모니터링 시각화)
- [ ] `grafana` 컨테이너 추가, Nginx `/grafana/` 서브패스로 노출 (Day 2 기준, Basic Auth 또는 Grafana 자체 로그인)
- [ ] Prometheus를 데이터소스로 연결
- [ ] 기본 대시보드 3종 정도: (1) 컨테이너별 CPU/메모리, (2) JVM 힙/GC, (3) 디스크 사용량
- [ ] 알림 채널(이메일/Slack/Discord webhook) 연결은 시간 남으면 — 디스크 사용량 90% 이상 시 알림 정도가 실전에서 가장 유용

### 3. Loki + Promtail (로그 모니터링)
- [ ] `loki`, `promtail` 컨테이너 추가
- [ ] Promtail이 Docker 컨테이너 로그(`/var/lib/docker/containers/*/*.log`)를 수집하도록 설정
- [ ] Grafana에서 Loki를 데이터소스로 추가, 백엔드/Nginx 로그를 검색 가능하게 확인
- [ ] 로그 보관 기간(retention) 설정 — 디스크 절약을 위해 짧게(예: 7~14일)

### 4. 리소스 부담 재확인
- [ ] Day 1에서 실측한 EC2 스펙 기준으로 Prometheus/Grafana/Loki가 실제로 안정적으로 뜨는지 확인
- [ ] 메모리가 부족하면: Loki 먼저 제외 → 컨테이너 로그는 `docker logs`로만 확인하는 최소 운영으로 대체

## 산출물
- Grafana 대시보드 3종, Loki 로그 검색 가능 (또는 리소스 부족 시 축소된 버전 + 그 사유 문서화)
