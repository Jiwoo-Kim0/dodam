package com.ssafy.dodam.domain.coach.service;

/**
 * 생성형 AI 호출 포트. 구현은 {@code infrastructure.external.ai}에 있다.
 *
 * <p>도메인이 OpenAI를 직접 알지 않게 분리한다. 공급자를 바꿔도 {@code CoachService}의 실패 처리·숫자
 * 검증 흐름은 그대로다. 테스트는 이 인터페이스만 대체해 "호출 실패 → 템플릿" 경로를 검증한다.
 */
public interface CoachTextGenerator {

    /** 설정이 갖춰져 실제 호출이 가능한 상태인지. false면 {@code CoachService}는 호출 없이 템플릿으로 간다. */
    boolean isAvailable();

    /** 문장을 쓴 모델 이름. 응답의 {@code model}에 그대로 실린다. */
    String modelName();

    /**
     * @return 모델이 쓴 본문
     * @throws CoachGenerationException 타임아웃·연결 실패·비정상 응답 등 외부 호출에서 발생한 모든 실패.
     *         호출자는 이 예외 하나만 처리하면 되며, 공급자별 예외 타입이 도메인으로 새지 않는다
     */
    String generate(String systemPrompt, String userPrompt);
}
