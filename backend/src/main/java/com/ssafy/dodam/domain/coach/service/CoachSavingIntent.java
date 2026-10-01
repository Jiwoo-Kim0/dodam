package com.ssafy.dodam.domain.coach.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 사용자 문장의 저축 금액이 변경 후 총액인지 증가분인지 서버에서 확인한다. */
final class CoachSavingIntent {
    private static final Pattern TOTAL = Pattern.compile(
            "월\\s*([0-9][0-9,]*)\\s*(억|만|천)?\\s*원\\s*(?:으로|로|씩|(?:을|를)\\s*저축(?:하게|하면|했을|할))");
    private static final Pattern INCREASE = Pattern.compile(
            "(?:월\\s*)?([0-9][0-9,]*)\\s*(억|만|천)?\\s*원\\s*(?:을|를)?\\s*(?:더|추가(?:로)?)(?=\\s*(?:저축|모으|납입|붓|하|[?!.]|$))");

    private CoachSavingIntent() {
    }

    static Parsed parse(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher total = TOTAL.matcher(message);
        Parsed lastTotal = null;
        while (total.find()) {
            lastTotal = new Parsed(false, won(total));
        }
        if (lastTotal != null) {
            return lastTotal;
        }
        Long increase = increaseAmount(message);
        return increase == null ? null : new Parsed(true, increase);
    }

    static Long increaseAmount(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher increase = INCREASE.matcher(message);
        if (!increase.find()) {
            return null;
        }
        long amount = won(increase);
        if (increase.find()) {
            throw new CoachToolArgumentException("저축 증가액이 여러 개예요. 한 번에 하나씩 물어봐 주세요.");
        }
        return amount;
    }

    static boolean expectsIncrease(String message) {
        return message != null && (message.contains("올리") || message.contains("늘리") || increaseAmount(message) != null);
    }

    private static long won(Matcher match) {
        try {
            long units = Long.parseLong(match.group(1).replace(",", ""));
            long multiplier = switch (match.group(2) == null ? "" : match.group(2)) {
                case "억" -> 100_000_000L;
                case "만" -> 10_000L;
                case "천" -> 1_000L;
                default -> 1L;
            };
            long amount = Math.multiplyExact(units, multiplier);
            if (amount <= 0 || amount > CoachToolArguments.MAX_AMOUNT) {
                throw new CoachToolArgumentException("저축 금액은 1억 원 이하로 알려 주세요.");
            }
            return amount;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new CoachToolArgumentException("저축 금액은 1억 원 이하로 알려 주세요.");
        }
    }

    record Parsed(boolean increase, long amount) {
    }
}
