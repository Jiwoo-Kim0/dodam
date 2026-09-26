package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.finance.event.FinanceConnectedEvent;
import com.ssafy.dodam.global.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 가입·금융망 재연결 직후 도담 전용 계좌를 연다. 금융망 장애가 가입을 막지 않는다는 기존 정책을 지키기 위해
 * 실패는 기록만 하고 삼킨다. 빠진 사용자는 목표 생성 전 `POST /api/v1/safebox/account`가 다시 연다.
 */
@Component
public class SafeboxOpenListener {
    private static final Logger log = LoggerFactory.getLogger(SafeboxOpenListener.class);

    private final SafeboxAccountService safeboxAccounts;

    public SafeboxOpenListener(SafeboxAccountService safeboxAccounts) {
        this.safeboxAccounts = safeboxAccounts;
    }

    @EventListener
    public void onFinanceConnected(FinanceConnectedEvent event) {
        try {
            safeboxAccounts.open(event.userId());
        } catch (BusinessException exception) {
            log.warn("Safebox account opening deferred for user {}: {}", event.userId(), exception.getErrorCode());
        } catch (RuntimeException exception) {
            // 가입은 이미 커밋됐다. 예상 밖 오류가 가입 응답을 실패로 바꾸지 않게 한다.
            log.error("Safebox account opening failed unexpectedly for user {}", event.userId(), exception);
        }
    }
}
