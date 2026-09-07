# Day 6 — 모니터링 스택: Prometheus / Grafana / Loki (P2)

일정이 밀렸다면 이 Day를 가장 먼저 축소한다 (`00-overview.md` 2번 우선순위 참고). 최소 목표는 "컨테이너가 죽었는지, 디스크/메모리가 찼는지 정도는 알 수 있게" 하는 것.

> **2026-09-07 재검토 반영**
> - `docs/dev_plan.md` 확인 결과 **Day 9~12에 김지우는 백엔드 기능 개발로 전환**한다. 그 기간에 인프라를 상시 들여다볼 수 없으므로, 이 Day를 축소하더라도 **"컨테이너 다운 / 디스크 임계치 알림"만큼은 반드시 남긴다.** 대시보드보다 알림이 우선순위가 높다.
> - EC2 사양이 16GB RAM으로 넉넉해서(Day 1 실측) 리소스 때문에 Loki를 뺄 이유는 사라졌다. 다만 백엔드 JVM 메모리 설정(Day 5)과 합쳐서 총량은 한 번 계산해볼 것.
> - 백엔드 준비 상태 확인 결과: `build.gradle`에 **`micrometer-registry-prometheus` 의존성이 없고**, `application.yml`의 `management.endpoints.web.exposure.include`가 **`health`만** 열려 있다. 즉 지금 상태로는 `/actuator/prometheus` 수집이 불가능하다 → 아래 요청 항목이 선행되어야 함.

## 체크리스트

### 1. Prometheus (리소스 모니터링)
- [ ] `prometheus` 컨테이너 추가, `infra/monitoring/prometheus/prometheus.yml` 작성
- [ ] `cadvisor` 또는 `node-exporter` 추가해 EC2 자체 리소스(CPU/메모리/디스크) 수집
- [ ] Spring Boot Actuator `/actuator/prometheus` 수집 대상으로 등록 — **백엔드팀에 아래 두 가지를 함께 요청** (**사용자 액션**)
  1. `build.gradle`에 `implementation 'io.micrometer:micrometer-registry-prometheus'` 추가
  2. `application.yml`의 `management.endpoints.web.exposure.include`에 `prometheus` 추가 (현재 `health`만 열려 있음)
  - 주의: actuator 엔드포인트는 nginx `/api/` 프록시 밖(`/actuator/`)에 있으므로 **외부에 노출되지 않도록** nginx에서 막고, Prometheus는 컨테이너 네트워크로만 접근하게 할 것
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
