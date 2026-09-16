package com.ssafy.dodam.domain.coach.service;

/**
 * 외부 AI 호출 실패. {@code CoachService}가 잡아 템플릿 응답으로 대체하므로 사용자에게 5xx로 새지 않는다.
 *
 * <p>{@link RuntimeException#getMessage()}에는 공급자 응답 본문을 넣지 않는다. 실패 본문에 요청 내용이
 * 되비쳐 오는 경우가 있어 그대로 로그에 남기면 프롬프트가 로그로 유출된다. 상세는 구현체가 필요한
 * 만큼만 잘라 직접 로깅한다.
 */
public class CoachGenerationException extends RuntimeException {

    public CoachGenerationException(String message) {
        super(message);
    }

    public CoachGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
