package com.ssafy.dodam.domain.coach.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 사실 목록에 실릴 값의 표기를 서버가 확정한다. 포맷을 모델에게 맡기지 않는 것이 {@link CoachNumberGuard}의
 * 전제다 — 서버가 쓴 문자열이 곧 허용 숫자 목록이기 때문이다.
 *
 * <p>정수에 항상 천단위 구분 기호를 넣는 것은 가독성 때문만이 아니다. 구분 기호가 없는 긴 숫자열은
 * {@link CoachSensitiveDataGuard}의 계좌·카드번호 규칙과 형태가 같다. 금액을 구분 기호 없이 쓰면
 * 정상 금액이 민감정보로 걸려 매번 템플릿으로 내려간다.
 */
final class CoachFormat {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CoachFormat() {
    }

    static String money(Long amount) {
        return amount == null ? null : integer(BigDecimal.valueOf(amount)) + "원";
    }

    static String count(long value) {
        return integer(BigDecimal.valueOf(value)) + "건";
    }

    static String days(Integer value) {
        return value == null ? null : integer(BigDecimal.valueOf(value)) + "일";
    }

    static String date(LocalDate value) {
        return value == null ? null : value.format(DATE);
    }

    static String dateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME);
    }

    /** 0~1 비율을 백분율로. 소수 1자리까지 두되 정수면 소수점을 붙이지 않는다. */
    static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return plain(ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)) + "%";
    }

    static String percent(Double ratio) {
        return ratio == null ? null : percent(BigDecimal.valueOf(ratio));
    }

    /** 비율이 아닌 일반 수치(군집 특징값 등). 소수 4자리까지. */
    static String number(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal scaled = value.scale() > 4 ? value.setScale(4, RoundingMode.HALF_UP) : value;
        return scaled.scale() <= 0 ? integer(scaled) : group(plain(scaled));
    }

    static String number(double value) {
        return number(BigDecimal.valueOf(value));
    }

    private static String integer(BigDecimal value) {
        return group(value.setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

    private static String plain(BigDecimal value) {
        String text = value.stripTrailingZeros().toPlainString();
        return text.isEmpty() ? "0" : text;
    }

    /** 정수부에만 구분 기호를 넣는다. 소수부에 넣으면 숫자 토큰이 두 개로 쪼개져 허용 목록이 어긋난다. */
    private static String group(String plain) {
        boolean negative = plain.startsWith("-");
        String body = negative ? plain.substring(1) : plain;
        int dot = body.indexOf('.');
        String head = dot < 0 ? body : body.substring(0, dot);
        String tail = dot < 0 ? "" : body.substring(dot);
        StringBuilder grouped = new StringBuilder();
        int written = 0;
        for (int i = head.length() - 1; i >= 0; i--) {
            grouped.append(head.charAt(i));
            if (++written % 3 == 0 && i > 0) {
                grouped.append(',');
            }
        }
        return (negative ? "-" : "") + grouped.reverse() + tail;
    }
}
