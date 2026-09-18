package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 모델 문장에 서버가 준 적 없는 숫자가 있는지 검사한다. 명세 21의 "AI 자체 계산 금지"를 프롬프트 문구가
 * 아니라 코드로 강제하는 자리다.
 *
 * <p><b>왜 프롬프트로 충분하지 않은가</b>: "계산하지 마라"는 지시는 지켜지는 편이지만 보장이 아니다.
 * 모델이 월 절감액 12,000원에서 "연 144,000원"을 스스로 만들어 내면, 그 문장은 그럴듯하고 검토자가
 * 알아채기 어렵다. 우리 도메인에서 연 절감액은 서버가 이미 계산해 저장하는 값이므로
 * ({@code RecommendationEffectResponse.ANNUAL_IS_MONTHLY_TIMES_12}) 모델이 그것을 만들어 낼 이유가 없다.
 * 검사에 걸리면 문장을 버리고 템플릿으로 내려간다 — 틀린 숫자를 보여주느니 덜 매끄러운 문장을 보여준다.
 *
 * <p><b>허용 목록</b>은 {@link CoachFacts}의 값 문자열에서 뽑은 숫자다. 모델이 반올림해 쓰는 것까지
 * 막으면(62.5% → "약 63%") 정상 문장이 계속 탈락하므로, 각 숫자의 정수부·소수 1자리 반올림·
 * 버림·올림까지 같은 숫자로 인정한다. 목록 번호로 흔히 쓰이는 1~3은 항상 허용한다.
 *
 * <p>검사 대상은 <b>아라비아 숫자뿐</b>이다. "세 가지", "절반" 같은 한글 수량 표현은 통과한다. 금액·비율을
 * 한글로 지어내는 경우는 관측되지 않았고, 막으려다 정상 문장을 대부분 떨어뜨린다.
 */
@Component
public class CoachNumberGuard {

    /** 천단위 구분 기호가 들어간 형태까지 한 덩어리로 잡는다: {@code 1,250,000}, {@code 62.5}, {@code 9} */
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?");
    /**
     * 한국어 단위가 붙은 금액: {@code 5만원}, {@code 100만}, {@code 1.5억}, {@code 3천원}. 사용자가 "5만원"이라 말한 것을 모델이
     * "50,000원"으로 되풀이하는 것은 지어낸 숫자가 아니므로 단위를 곱한 값도 허용한다(640 A-3). 서버 facts는 천단위
     * 구분 기호로 포맷하므로 여기 걸리는 일이 거의 없다.
     */
    private static final Pattern KOREAN_UNIT_AMOUNT = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(억|만|천)");
    private static final Set<String> ALWAYS_ALLOWED = Set.of("1", "2", "3");

    /**
     * @return 허용 목록에 없는 첫 숫자. 비어 있으면 모델 문장을 그대로 써도 된다
     */
    public Optional<String> findUnverifiedNumber(String text, CoachFacts facts) {
        return findUnverifiedNumber(text, facts, List.of());
    }

    /**
     * 채팅(640 A-3)용. 허용 목록에 {@code extraSources}의 숫자를 더한다 — 사용자가 직접 말한 값("5만원", "100만원")과
     * 같은 대화의 앞선 턴에서 서버가 준 값은 되풀이해도 지어낸 숫자가 아니다.
     */
    public Optional<String> findUnverifiedNumber(String text, CoachFacts facts, Collection<String> extraSources) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Set<String> allowed = allowedNumbers(facts);
        if (extraSources != null) {
            extraSources.forEach(source -> addAll(allowed, source));
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String normalized = normalize(matcher.group());
            if (normalized != null && !allowed.contains(normalized)) {
                return Optional.of(matcher.group());
            }
        }
        return Optional.empty();
    }

    /** 사실 목록의 모든 값에서 뽑은 허용 숫자. 테스트가 직접 들여다볼 수 있도록 공개한다. */
    public Set<String> allowedNumbers(CoachFacts facts) {
        Set<String> allowed = new LinkedHashSet<>(ALWAYS_ALLOWED);
        if (facts == null) {
            return allowed;
        }
        addAll(allowed, facts.subject());
        facts.notes().forEach(note -> addAll(allowed, note));
        facts.sections().forEach(section -> {
            addAll(allowed, section.title());
            section.facts().forEach(fact -> {
                addAll(allowed, fact.label());
                addAll(allowed, fact.value());
            });
        });
        return allowed;
    }

    private static void addAll(Set<String> allowed, String source) {
        if (source == null) {
            return;
        }
        Matcher matcher = NUMBER.matcher(source);
        while (matcher.find()) {
            String normalized = normalize(matcher.group());
            if (normalized == null) {
                continue;
            }
            allowed.add(normalized);
            BigDecimal value = new BigDecimal(normalized);
            allowed.add(trim(value.setScale(0, RoundingMode.HALF_UP)));
            allowed.add(trim(value.setScale(0, RoundingMode.FLOOR)));
            allowed.add(trim(value.setScale(0, RoundingMode.CEILING)));
            allowed.add(trim(value.setScale(1, RoundingMode.HALF_UP)));
        }
        Matcher units = KOREAN_UNIT_AMOUNT.matcher(source);
        while (units.find()) {
            String normalized = normalize(units.group(1));
            if (normalized == null) {
                continue;
            }
            BigDecimal multiplier = switch (units.group(2)) {
                case "억" -> BigDecimal.valueOf(100_000_000L);
                case "만" -> BigDecimal.valueOf(10_000L);
                default -> BigDecimal.valueOf(1_000L);
            };
            allowed.add(trim(new BigDecimal(normalized).multiply(multiplier).setScale(0, RoundingMode.HALF_UP)));
        }
    }

    /** 천단위 구분 기호와 무의미한 0을 지워 {@code 1,250,000}과 {@code 1250000}, {@code 09}와 {@code 9}를 같게 본다. */
    private static String normalize(String token) {
        String digits = token.replace(",", "");
        if (digits.isEmpty() || digits.endsWith(".")) {
            return null;
        }
        try {
            return trim(new BigDecimal(digits));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
