package com.ssafy.dodam.domain.coach.dto;

import java.util.List;

/**
 * AI에게 전달하는 입력 전체. <b>이 레코드 밖의 값은 프롬프트에 들어가지 않는다.</b>
 *
 * <p>명세 21의 "서버 계산 결과만 입력으로 사용"을 타입으로 강제하기 위한 구조다. 엔티티나 JSON 노드를
 * 그대로 직렬화해 넘기면 무엇이 넘어갔는지 코드에서 읽을 수 없고, 도메인에 컬럼이 하나 늘 때마다
 * 프롬프트가 조용히 넓어진다. 그래서 조립기({@code CoachFactAssembler})가 필요한 값만 골라 담는다.
 *
 * <p><b>{@code value}가 모두 {@code String}인 이유</b>: 숫자 포맷(천단위 구분, 퍼센트 자릿수, 날짜 형식)을
 * 서버가 확정해서 넘긴다. 모델에게 "0.6234를 62.3%로 바꿔라"를 시키는 순간 그것이 계산이고, 명세가
 * 금지한 "AI 자체 계산"이 된다. 동시에 이 문자열들이 {@code CoachNumberGuard}의 허용 숫자 목록이 되어
 * 모델이 지어낸 숫자를 걸러내는 기준이 된다.
 *
 * @param topic    어떤 API의 입력인지
 * @param subject  설명 대상 한 줄(예: 문제 제목). 모델이 무엇을 설명해야 하는지 고정한다
 * @param sections 사실 묶음. 순서가 프롬프트 순서다
 * @param notes    수치가 아닌 서버 판정(군집 라벨, 종합 상태 등)의 서술. 모델이 새로 판정하지 못하도록 미리 못박는다
 */
public record CoachFacts(CoachTopic topic, String subject, List<Section> sections, List<String> notes) {

    public CoachFacts {
        sections = sections == null ? List.of() : List.copyOf(sections);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** @param facts 라벨-값 쌍. 값은 이미 서버가 포맷한 문자열이다 */
    public record Section(String title, List<Fact> facts) {
        public Section {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }

    public record Fact(String label, String value) {
    }
}
