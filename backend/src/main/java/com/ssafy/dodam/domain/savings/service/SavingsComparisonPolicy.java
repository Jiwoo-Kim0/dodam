package com.ssafy.dodam.domain.savings.service;

import java.util.List;

/**
 * 보유 적금과 상품 DB를 같은 조건으로 묶는 비교 정책.
 *
 * <p>비교군은 <b>연결 상품의 saving_type 문자열</b>로 정한다. 예금(536)과 같은 구조지만 판정 방식이 다르다.
 * 예금의 saving_type은 5종이라 값 집합으로 나눌 수 있었는데, <b>적금은 53개 상품에 36종이 흩어져 있어</b>
 * 대부분의 값이 상품 1개짜리다. 값마다 군을 만들면 전부 표본 부족이 된다.
 *
 * <p>그래서 값 전체가 아니라 <b>키워드 우선순위</b>로 접는다. 순서에 의미가 있다.
 *
 * <ol>
 *   <li>자격·목적 한정을 먼저 걸러낸다. 청년·군인·아동·정책 상품은 가입 자격이 좁은 대신 금리가 높아,
 *       적립 방식으로 먼저 묶으면 일반 상품의 기준금리를 끌어올린다. {@code GROUP_FLEXIBLE}처럼 두 축의
 *       키워드를 모두 가진 값이 있어 순서가 결과를 바꾼다.</li>
 *   <li>남은 것을 적립 방식으로 나눈다. 정액적립과 자유적립은 금리 체계가 다르다.</li>
 *   <li>챌린지·미션형은 소액·단기 구조라 따로 둔다.</li>
 * </ol>
 *
 * <p><b>순서를 보장하려고 Map이 아니라 List를 쓴다.</b> {@code Map.copyOf}는 입력이 LinkedHashMap이어도
 * 순서를 유지하지 않는 불변 맵을 돌려주므로, 맵으로 두면 우선순위가 실행마다 달라질 수 있다.
 *
 * <p>시연 데이터 기준 분포는 RESTRICTED 26 / REGULAR 11 / FLEXIBLE 8 / CHALLENGE 6 / OTHER 2다.
 * OTHER는 표본이 모자라 비교가 성립하지 않으며 그 사실을 숨기지 않는다.
 */
final class SavingsComparisonPolicy {
    /** 먼저 일치하는 군을 쓴다. 선언 순서가 곧 우선순위다. */
    private static final List<GroupRule> RULES = List.of(
            new GroupRule("RESTRICTED", List.of("POLICY", "YOUTH", "MILITARY", "CHILD", "EMPLOYEE",
                    "MICROFINANCE", "SOCIAL", "FOREIGN", "BUSINESS", "GROUP", "MULTI")),
            new GroupRule("FLEXIBLE", List.of("FLEXIBLE")),
            new GroupRule("REGULAR", List.of("REGULAR")),
            new GroupRule("CHALLENGE", List.of("CHALLENGE", "MISSION")));

    /** 어느 키워드에도 걸리지 않은 상품이 모이는 군. */
    private static final String OTHER = "OTHER";

    private SavingsComparisonPolicy() {
    }

    /** 상품 saving_type이 속한 비교군 이름. 값이 없으면 null, 어느 키워드에도 걸리지 않으면 OTHER다. */
    static String groupOf(String productSavingType) {
        if (productSavingType == null) {
            return null;
        }
        return RULES.stream()
                .filter(rule -> rule.keywords().stream().anyMatch(productSavingType::contains))
                .map(GroupRule::name)
                .findFirst()
                .orElse(OTHER);
    }

    /** 두 saving_type이 같은 비교군인지. 모집단 판정에 쓴다. */
    static boolean sameGroup(String group, String productSavingType) {
        return group.equals(groupOf(productSavingType));
    }

    private record GroupRule(String name, List<String> keywords) {
    }
}
