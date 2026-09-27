package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 금융 효율 점수 산식(S15P21D105-635). 입력을 받아 점수만 만드는 순수 계산이라 단위 테스트가 곧 산식 문서다.
 *
 * <p><b>모든 임계값은 서비스 초기값</b>이며 팀에서 검증한 기준이 아니다. 바꾸면 {@link #VERSION}을 올린다 — 저장된 과거
 * 리포트 점수는 그대로 남고 새 리포트만 새 산식을 쓴다.
 *
 * <p>버전 이력: {@code efficiency-v1}(635) → {@code efficiency-v2}(680, CREDIT 입력을 A~E 등급에서 예상 신용점수 우선으로)
 * → {@code efficiency-v3}(694: FIXED_EXPENSE 임계값 0.3/0.7 → 0.4/0.8, CASHFLOW·INEFFICIENCY 입력을 "그 달 안"에서
 * "그 달 말일까지 완료된 최신, 없으면 리포트 생성 시점 최신"으로 완화. {@link EfficiencyScoreCalculator} 참고)
 * → {@code efficiency-v4}(712: CASHFLOW·INEFFICIENCY를 빼고 DEBT_REPAYMENT를 넣음. 두 항목은 사용자가 현금흐름 예측이나
 * 발견 분석을 먼저 실행해야 입력이 생겨, 실행 전에 처음 연 리포트에는 "계산 불가"로 굳었다. 새 항목은 지출·저축처럼 그 달
 * 원천 데이터만으로 늘 계산된다).
 *
 * <table>
 *   <caption>항목별 산식(0~100, 선형 보간 후 반올림)</caption>
 *   <tr><th>항목</th><th>근거 수치</th><th>100점</th><th>0점</th></tr>
 *   <tr><td>SPENDING 지출 관리</td><td>소비 / 소득</td><td>≤ 0.5</td><td>≥ 1.0</td></tr>
 *   <tr><td>SAVING 저축</td><td>저축성 지출 / 소득</td><td>≥ 0.3</td><td>0</td></tr>
 *   <tr><td>FIXED_EXPENSE 고정지출</td><td>고정지출 / 소비</td><td>≤ 0.4</td><td>≥ 0.8</td></tr>
 *   <tr><td>CREDIT 신용 관리</td><td>개인신용점수</td><td>1,000점</td><td>1점</td></tr>
 *   <tr><td>DEBT_REPAYMENT 부채 상환</td><td>그 달 대출 상환액(PAID) / 소득</td><td>≤ 0.1</td><td>≥ 0.4</td></tr>
 * </table>
 *
 * <p>상태는 GOOD ≥ 70, NORMAL 40~69, CHECK &lt; 40. 전체 점수는 계산된 항목의 단순 평균(반올림)이며 계산된 항목이
 * {@link #MINIMUM_DIMENSIONS}개 미만이면 null이다.
 */
public final class EfficiencyScorePolicy {

    public static final String VERSION = "efficiency-v4";
    public static final int MINIMUM_DIMENSIONS = 3;

    public static final String KEY_SPENDING = "SPENDING";
    public static final String KEY_SAVING = "SAVING";
    public static final String KEY_FIXED_EXPENSE = "FIXED_EXPENSE";
    public static final String KEY_CREDIT = "CREDIT";
    public static final String KEY_DEBT_REPAYMENT = "DEBT_REPAYMENT";

    public static final String STATUS_GOOD = "GOOD";
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_CHECK = "CHECK";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public static final String REASON_INCOME_MISSING = "INCOME_MISSING";
    public static final String REASON_NO_SPENDING = "NO_SPENDING";
    public static final String REASON_CREDIT_SCORE_MISSING = "CREDIT_SCORE_MISSING";

    static final String VALUE_RATIO = "RATIO";
    static final String VALUE_GRADE = "GRADE";
    static final String VALUE_SCORE = "SCORE";

    static final int GOOD_THRESHOLD = 70;
    static final int NORMAL_THRESHOLD = 40;

    private static final Map<String, Integer> CREDIT_GRADE_SCORES = Map.of("A", 100, "B", 85, "C", 70, "D", 50, "E", 30);

    private static final Map<String, String> LABELS = Map.of(
            KEY_SPENDING, "지출 관리", KEY_SAVING, "저축", KEY_FIXED_EXPENSE, "고정지출",
            KEY_CREDIT, "신용 관리", KEY_DEBT_REPAYMENT, "부채 상환");

    private EfficiencyScorePolicy() {
    }

    // ---------- 항목 ----------

    /** 소비 / 소득. 소득이 0이면 INCOME_MISSING. */
    public static Dimension spending(long totalSpending, long totalIncome) {
        if (totalIncome <= 0) {
            return unavailable(KEY_SPENDING, REASON_INCOME_MISSING, "이 달 수입이 없어 소비율을 계산하지 못했어요.");
        }
        double ratio = ratio(totalSpending, totalIncome);
        int score = linearDown(ratio, 0.5, 1.0);
        return available(KEY_SPENDING, score, "소득 대비 소비율이 " + percent(ratio) + "%예요.", ratio, VALUE_RATIO, null);
    }

    /** 저축성 지출 / 소득. 소득이 0이면 INCOME_MISSING. */
    public static Dimension saving(long totalSaving, long totalIncome) {
        if (totalIncome <= 0) {
            return unavailable(KEY_SAVING, REASON_INCOME_MISSING, "이 달 수입이 없어 저축률을 계산하지 못했어요.");
        }
        double rate = ratio(totalSaving, totalIncome);
        int score = linearUp(rate, 0.0, 0.3);
        return available(KEY_SAVING, score, "소득의 " + percent(rate) + "%를 저축했어요.", rate, VALUE_RATIO, null);
    }

    /**
     * 고정지출 / 소비. 소비가 0이면 NO_SPENDING.
     *
     * <p>임계값 0.4/0.8(v3). 고정지출에는 월세·관리비·대출 상환·보험·구독이 모두 들어가 1인 가구 시연 계정도 60%대라, v1의
     * 0.3/0.7로는 70%부터 0점이 되어 "점검 필요"만 나왔다. 40% 이하를 만점, 80% 이상을 0점으로 둔다.
     */
    public static Dimension fixedExpense(long fixedSpending, long totalSpending) {
        if (totalSpending <= 0) {
            return unavailable(KEY_FIXED_EXPENSE, REASON_NO_SPENDING, "이 달 소비가 없어 고정지출 비중을 계산하지 못했어요.");
        }
        double share = ratio(fixedSpending, totalSpending);
        int score = linearDown(share, 0.4, 0.8);
        return available(KEY_FIXED_EXPENSE, score, "전체 소비 중 고정지출이 " + percent(share) + "%예요.", share, VALUE_RATIO, null);
    }

    /**
     * 1~1,000 개인신용점수를 0~100으로 환산한다. 점수가 아직 없으면 기존 금융망 A~E 등급을 호환 입력으로 쓴다.
     */
    public static Dimension credit(Integer creditScore, String creditRating) {
        if (creditScore != null && creditScore >= 1 && creditScore <= 1000) {
            int score = (int) Math.round(creditScore / 10.0);
            String description = score >= GOOD_THRESHOLD
                    ? "예상 신용점수가 " + creditScore + "점으로 안정적이에요."
                    : "예상 신용점수가 " + creditScore + "점이에요. 관리가 필요해요.";
            return available(KEY_CREDIT, score, description, creditScore.doubleValue(), VALUE_SCORE, null);
        }
        String grade = normalizeGrade(creditRating);
        if (grade == null) {
            return unavailable(KEY_CREDIT, REASON_CREDIT_SCORE_MISSING, "신용점수 정보가 없어 계산하지 못했어요.");
        }
        int score = CREDIT_GRADE_SCORES.get(grade);
        String description = score >= GOOD_THRESHOLD ? "기존 금융망 기준 신용 상태가 안정적이에요(" + grade + "등급)."
                : "기존 금융망 신용등급이 " + grade + "등급이에요. 관리가 필요해요.";
        return available(KEY_CREDIT, score, description, null, VALUE_GRADE, grade);
    }

    /**
     * 그 달 대출 상환액(PAID) / 소득. 소득이 0이면 INCOME_MISSING. 대출이 없거나 그 달 상환이 없으면 0%라 100점이다.
     *
     * <p>임계값 0.1/0.4(v4). 0.4는 은행권 DSR 규제선이라 그 이상을 0점으로 두고, 소득의 10% 이하는 부담이 없다고 본다.
     * 사이는 다른 비율 항목과 같이 선형이다(시연 계정 8월 13.6% → 88점).
     */
    public static Dimension debtRepayment(long paidRepayment, long totalIncome) {
        if (totalIncome <= 0) {
            return unavailable(KEY_DEBT_REPAYMENT, REASON_INCOME_MISSING, "이 달 수입이 없어 대출 상환 부담을 계산하지 못했어요.");
        }
        double ratio = ratio(paidRepayment, totalIncome);
        int score = linearDown(ratio, 0.1, 0.4);
        String description = paidRepayment <= 0 ? "대출 상환액이 없어요." : "소득의 " + percent(ratio) + "%를 대출 상환에 썼어요.";
        return available(KEY_DEBT_REPAYMENT, score, description, ratio, VALUE_RATIO, null);
    }

    // ---------- 전체 ----------

    /** 항목들로 전체 점수·요약을 만든다. 전월 차이는 호출자가 {@code withPreviousMonthDifference}로 붙인다. */
    public static EfficiencyScoreResponse overall(List<Dimension> dimensions) {
        List<Dimension> scored = dimensions.stream().filter(d -> d.score() != null).toList();
        Integer score = null;
        String reason = null;
        if (scored.size() >= MINIMUM_DIMENSIONS) {
            double average = scored.stream().mapToInt(Dimension::score).average().orElse(0.0);
            score = (int) Math.round(average);
        } else {
            reason = EfficiencyScoreResponse.INSUFFICIENT_DIMENSIONS;
        }
        return new EfficiencyScoreResponse(score, EfficiencyScoreResponse.MAX_SCORE, null, summary(score, scored), VERSION,
                scored.size(), reason, dimensions);
    }

    /**
     * 한 줄 요약. 가장 높은 항목과 가장 낮은 항목으로 만든다.
     * 전체 점수가 없으면 데이터 부족 문구, 모두 GOOD이면 안정 문구, 최저 항목이 CHECK면 "…관리가 필요해요".
     */
    static String summary(Integer score, List<Dimension> scored) {
        if (score == null) {
            return "점수를 계산할 데이터가 아직 부족해요.";
        }
        Dimension best = scored.stream().max(Comparator.comparingInt(Dimension::score).thenComparing(Dimension::key)).orElseThrow();
        Dimension worst = scored.stream().min(Comparator.comparingInt(Dimension::score).thenComparing(Dimension::key)).orElseThrow();
        if (worst.score() >= GOOD_THRESHOLD) {
            return "전반적으로 금융 생활이 안정적이에요.";
        }
        if (worst.score() < NORMAL_THRESHOLD) {
            return best.label() + topic(best.label()) + " 안정적이지만 " + worst.label() + " 관리가 필요해요.";
        }
        return best.label() + topic(best.label()) + " 안정적이에요. " + worst.label() + object(worst.label()) + " 조금 더 챙겨 보세요.";
    }

    /** 은/는 — 마지막 글자에 받침이 있으면 "은". */
    static String topic(String word) {
        return hasFinalConsonant(word) ? "은" : "는";
    }

    /** 을/를 — 마지막 글자에 받침이 있으면 "을". */
    static String object(String word) {
        return hasFinalConsonant(word) ? "을" : "를";
    }

    private static boolean hasFinalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return false;
        }
        return (last - 0xAC00) % 28 != 0;
    }

    public static String status(int score) {
        if (score >= GOOD_THRESHOLD) {
            return STATUS_GOOD;
        }
        return score >= NORMAL_THRESHOLD ? STATUS_NORMAL : STATUS_CHECK;
    }

    // ---------- helpers ----------

    private static Dimension available(String key, int score, String description, Double value, String valueType, String grade) {
        return new Dimension(key, LABELS.get(key), score, EfficiencyScoreResponse.MAX_SCORE, status(score), description,
                value, valueType, grade, null);
    }

    private static Dimension unavailable(String key, String reason, String description) {
        return new Dimension(key, LABELS.get(key), null, EfficiencyScoreResponse.MAX_SCORE, STATUS_UNAVAILABLE, description,
                null, null, null, reason);
    }

    /** {@code value ≤ full}이면 100, {@code value ≥ zero}이면 0, 사이는 선형. */
    static int linearDown(double value, double full, double zero) {
        if (value <= full) {
            return EfficiencyScoreResponse.MAX_SCORE;
        }
        if (value >= zero) {
            return 0;
        }
        return (int) Math.round((zero - value) / (zero - full) * EfficiencyScoreResponse.MAX_SCORE);
    }

    /** {@code value ≥ full}이면 100, {@code value ≤ zero}이면 0, 사이는 선형. */
    static int linearUp(double value, double zero, double full) {
        if (value >= full) {
            return EfficiencyScoreResponse.MAX_SCORE;
        }
        if (value <= zero) {
            return 0;
        }
        return (int) Math.round((value - zero) / (full - zero) * EfficiencyScoreResponse.MAX_SCORE);
    }

    /** 0~1 소수 4자리(리포트 소비패턴 비율과 같은 규칙). */
    static double ratio(long amount, long total) {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String percent(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    static String normalizeGrade(String creditRating) {
        if (creditRating == null || creditRating.isBlank()) {
            return null;
        }
        String grade = creditRating.trim().toUpperCase().substring(0, 1);
        return CREDIT_GRADE_SCORES.containsKey(grade) ? grade : null;
    }

    /** 항목 순서를 고정한다(효율 점수 화면의 목록 순서). */
    public static List<Dimension> ordered(Dimension spending, Dimension saving, Dimension fixedExpense, Dimension credit,
            Dimension debtRepayment) {
        return List.of(spending, saving, fixedExpense, credit, debtRepayment);
    }
}
