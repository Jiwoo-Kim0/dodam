package com.ssafy.dodam.domain.improvement.service;

import com.ssafy.dodam.domain.diagnosis.service.DiagnosisRunner;
import com.ssafy.dodam.domain.improvement.dto.RediagnoseResultResponse;
import org.springframework.stereotype.Service;

/**
 * 재진단 요청(API 19 #11).
 *
 * <p>진단 로직을 다시 만들지 않고 실행 API(`POST /api/v1/diagnoses`)와 <b>같은 러너</b>를 부른다. 그래서
 * 중복 방지도 그쪽 정책({@code DiagnosisExecutionStore})을 그대로 따른다.
 * <ul>
 *   <li>임대 시간 안에 진행 중인 접수가 있으면 409 {@code DIAGNOSIS_IN_PROGRESS}</li>
 *   <li>같은 날 같은 규칙 버전의 완료 진단이 있으면 그 진단을 돌려준다({@code created: false}, 200)</li>
 *   <li>그 외에는 새로 실행한다({@code created: true}, 201)</li>
 * </ul>
 *
 * <p>{@code forceRefresh}를 열지 않는다. 개선 결과 화면에서 버튼을 연타해도 같은 날 진단이 여러 건 쌓이지
 * 않게 하는 것이 이 API의 "중복 요청 방지"다. 강제 재실행이 필요하면 진단 API를 직접 쓴다.
 */
@Service
public class ImprovementRediagnoseService {

    private final DiagnosisRunner runner;
    private final ImprovementClock clock;

    public ImprovementRediagnoseService(DiagnosisRunner runner, ImprovementClock clock) {
        this.runner = runner;
        this.clock = clock;
    }

    public RediagnoseResultResponse rediagnose(Long userId, Long goalId) {
        var requestedAt = clock.now();
        DiagnosisRunner.Result result = runner.run(userId, goalId, false);
        return new RediagnoseResultResponse(result.diagnosis().diagnosisId(), result.diagnosis().goalId(),
                !result.cached(), requestedAt);
    }
}
