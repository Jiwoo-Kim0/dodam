# Day 6 — 모니터링 스택: Prometheus / Grafana / Loki (P2)

일정이 밀렸다면 이 Day를 가장 먼저 축소한다 (`00-overview.md` 2번 우선순위 참고). 최소 목표는 "컨테이너가 죽었는지, 디스크/메모리가 찼는지 정도는 알 수 있게" 하는 것.

> **2026-09-07 재검토 반영**
> - `docs/dev_plan.md` 확인 결과 **Day 9~12에 김지우는 백엔드 기능 개발로 전환**한다. 그 기간에 인프라를 상시 들여다볼 수 없으므로, 이 Day를 축소하더라도 **"컨테이너 다운 / 디스크 임계치 알림"만큼은 반드시 남긴다.** 대시보드보다 알림이 우선순위가 높다.
> - EC2 사양이 16GB RAM으로 넉넉해서(Day 1 실측) 리소스 때문에 Loki를 뺄 이유는 사라졌다. 다만 백엔드 JVM 메모리 설정(Day 5)과 합쳐서 총량은 한 번 계산해볼 것.
> - 백엔드 준비 상태 확인 결과: `build.gradle`에 **`micrometer-registry-prometheus` 의존성이 없고**, `application.yml`의 `management.endpoints.web.exposure.include`가 **`health`만** 열려 있다. 즉 지금 상태로는 `/actuator/prometheus` 수집이 불가능하다 → 아래 요청 항목이 선행되어야 함.

## 체크리스트

### 1. Prometheus (리소스 모니터링)
- [x] `prometheus` 컨테이너 추가, `infra/monitoring/prometheus/prometheus.yml` 작성
- [x] `node-exporter` 추가해 EC2 자체 리소스 수집
- [x] ~~cadvisor~~ → **제거함**. Docker 29의 overlayfs 스토리지 드라이버를 지원하지 않아
      컨테이너별 메트릭이 하나도 나오지 않았다(name 라벨 0건). 4가지 우회를 시도했으나 모두 실패.
      대체: `infra/scripts/container-watch.sh` (docker에 직접 질의, 5분 cron). 상세: [[jiw/day-06.md]]
- [ ] Spring Boot Actuator `/actuator/prometheus` 수집 대상으로 등록 — **백엔드팀에 아래 두 가지를 함께 요청** (**사용자 액션**)
  1. `build.gradle`에 `implementation 'io.micrometer:micrometer-registry-prometheus'` 추가
  2. `application.yml`의 `management.endpoints.web.exposure.include`에 `prometheus` 추가 (현재 `health`만 열려 있음)
  3. **`SecurityConfig`에서 `/actuator/prometheus`를 permitAll에 추가** ← 실제 확인 결과 추가로 필요
     (`/actuator/prometheus`가 404가 아니라 **401**을 반환한다. Spring Security가 막고 있어
      의존성과 노출 설정만으로는 수집이 되지 않는다)
  - [x] actuator를 외부에 노출하지 않도록 nginx에서 차단 (404 확인)
- [ ] MySQL exporter 추가 여부는 시간 되면(P2 중에서도 후순위)

### 2. Grafana (모니터링 시각화)
- [x] `grafana` 컨테이너 추가, Nginx `/grafana/` 서브패스로 노출 (Grafana 자체 로그인, 익명/가입 차단)
- [x] Prometheus·Loki 데이터소스 파일 프로비저닝 (UI 설정은 컨테이너 재생성 시 소실되므로)
- [x] 대시보드 3종 — 서비스 개요 / 호스트 리소스 / 로그
      (컨테이너별 CPU·메모리는 cAdvisor 제거로 불가, JVM은 백엔드 선행작업 대기)
- [x] **알림 채널 연결 — Mattermost**. 시간 남으면이 아니라 **최우선으로 처리**했다.
      장애 3건이 모두 "빌드는 빨간불이었지만 아무도 안 봤다"였기 때문

### 3. Loki + Promtail (로그 모니터링)
- [x] `loki`, `promtail` 컨테이너 추가
- [x] Promtail이 Docker 서비스 디스커버리로 컨테이너 로그 수집 (파일 경로 직접 훑기보다 컨테이너 이름 라벨이 정확히 붙는다)
- [x] Grafana에 Loki 데이터소스 연결, 컨테이너 11개 로그 수집 및 조회 확인
- [x] 보관 기간 14일 설정 (compactor `retention_enabled` 포함 — 없으면 설정만 하고 실제 삭제가 안 된다)

### 4. 리소스 부담 재확인
- [x] 전 서비스에 `mem_limit` 적용 후 안정 기동 확인. 15GB 중 예약 약 7.5GB로 여유 있음
- [x] 메모리 부족 상황 없었으므로 Loki 유지

### 5. 배포 반영 (Day 6에서 추가로 발견)
- [x] 모니터링 서비스를 `deploy.sh`의 `DEPLOY_SERVICES`에 포함
      목록에 없으면 배포가 해당 컨테이너를 아예 건드리지 않아, 설정 변경이 반영되지 않고
      수동 변경은 다음 배포의 `git reset --hard`에 밀려 조용히 원복된다
- [x] compose 서비스와 배포 목록을 대조해 누락 시 배포 로그에 WARN을 남기도록 추가

## 산출물
- Grafana 대시보드 3종, Loki 로그 검색 가능 (또는 리소스 부족 시 축소된 버전 + 그 사유 문서화)
