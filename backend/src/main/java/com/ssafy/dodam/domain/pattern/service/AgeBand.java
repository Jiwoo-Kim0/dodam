package com.ssafy.dodam.domain.pattern.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * 동년배 비교의 나이대(632). 기준일 현재 만 나이의 10세 단위. Python `train_cluster_model.py`의 `age_band`와 같은 정의라
 * 아티팩트의 집단 키와 Java 배정이 어긋나지 않는다. 20세 미만은 `10S`, 60세 이상은 `60S_PLUS` 하나로 묶는다(표본이 적다).
 */
public final class AgeBand {
    public static final List<String> ORDERED = List.of("10S", "20S", "30S", "40S", "50S", "60S_PLUS");

    private AgeBand() {
    }

    /** 생년월일이 없으면 null. */
    public static String of(LocalDate birthDate, LocalDate referenceDate) {
        if (birthDate == null || referenceDate == null) {
            return null;
        }
        int age = Period.between(birthDate, referenceDate).getYears();
        if (age < 20) {
            return "10S";
        }
        if (age >= 60) {
            return "60S_PLUS";
        }
        return (age / 10 * 10) + "S";
    }

    /** 문장용 표기: 20S → "20대", 60S_PLUS → "60대 이상", 10S → "10대". */
    public static String display(String ageBand) {
        if (ageBand == null) {
            return null;
        }
        if ("60S_PLUS".equals(ageBand)) {
            return "60대 이상";
        }
        return ageBand.replace("S", "대");
    }
}
