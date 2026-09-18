package com.ssafy.dodam.domain.coach.service;

/**
 * 모델이 채운 도구 인자가 실행할 수 없는 값일 때(640 A-2). 메시지는 사용자에게 그대로 보여도 되는 한국어 문장이며,
 * 도구 결과의 {@code error}로 2차 호출에 전달돼 모델이 "카페 월평균 소비보다 큰 금액이에요"처럼 되묻는 근거가 된다.
 * HTTP 오류가 아니다 — 채팅 응답은 200이다.
 */
public class CoachToolArgumentException extends RuntimeException {

    public CoachToolArgumentException(String message) {
        super(message);
    }
}
