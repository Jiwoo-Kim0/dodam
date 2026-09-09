package com.ssafy.dodam.domain.loan.service;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class LoanAnalysisClock {
    public LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
