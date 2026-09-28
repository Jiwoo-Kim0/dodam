package com.ssafy.dodam.domain.coach.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API 21 3종의 공통 응답. 세 엔드포인트가 같은 형식을 쓴다 — 다른 것은 {@code topic}과 {@code facts}의 내용뿐이다.
 *
 * <p><b>{@code facts}를 응답에 그대로 싣는 이유</b>: FE가 AI 문장을 그대로 믿지 않고 수치를 직접 렌더링할 수
 * 있어야 한다. 문장은 바뀔 수 있어도 화면의 숫자는 서버 계산값이어야 하고, 이 필드가 "모델에게 준 입력 =
 * 화면에 쓸 값"이라는 것을 보장한다. 장애 조사 때 "무엇을 넘겼길래 저런 문장이 나왔나"를 응답만으로
 * 재현할 수 있다는 부수 효과도 있다.
 *
 * @param topic          어떤 API의 응답인지
 * @param source         AI | TEMPLATE
 * @param degradedReason 템플릿으로 내려간 사유. {@code source=AI}면 null
 * @param model          문장을 쓴 모델 이름. {@code source=TEMPLATE}면 null
 * @param generatedAt    응답 생성 시각(Asia/Seoul)
 * @param subject        설명 대상 한 줄
 * @param message        본문. 문단 구분은 {@code \n\n}
 * @param highlights     서버 계산값으로 만든 핵심 문장. AI 경로에서도 서버가 만든 것이며 모델을 거치지 않는다
 * @param facts          모델에 전달한 입력 전체(= 화면에 쓸 수치)
 * @param disclaimer     고지 문구. 금융 조언이 아니라는 표시
 */
public record CoachMessageResponse(
        CoachTopic topic,
        CoachSource source,
        @JsonInclude(JsonInclude.Include.ALWAYS) CoachDegradedReason degradedReason,
        @JsonInclude(JsonInclude.Include.ALWAYS) String model,
        LocalDateTime generatedAt,
        String subject,
        String message,
        List<String> highlights,
        CoachFacts facts,
        String disclaimer
) {

    public static final String DISCLAIMER =
            "이 설명은 회원님의 등록된 금융 데이터로 서버가 계산한 결과를 문장으로 옮긴 것이며, 투자·금융 자문이 아닙니다.";

    public CoachMessageResponse {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
