package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 시연 계정이 "금융망 연결 + 도담 전용 계좌" 상태로 기동되게 한다(S15P21D105-705).
 *
 * <p>seed 사용자는 finance_connection이 없어 가입 시 자동 개설이 일어나지 않는다. 기동 직후 연결과 개설을 맞추고, 이미 된
 * 단계는 건너뛴다. 실패는 로그만 남기고 기동을 막지 않는다.
 *
 * <p>seed 주거래를 금융망 실계좌로 바꾸던 단계는 없앴다. seed 계좌가 낀 이체는 금융망 쪽만 입금·출금하고 seed 쪽은
 * 도담 DB에서 반영하므로(AccountTransferService) 대응이 필요 없고, 재기동마다 금융망 계좌를 만들 위험도 없앤다.
 */
@Component
public class SafeboxDemoBootstrap {
    private static final Logger log = LoggerFactory.getLogger(SafeboxDemoBootstrap.class);

    private final String bootstrapEmails;
    private final UserRepository users;
    private final FinanceConnectionRepository connectionRows;
    private final FinanceConnectionService connections;
    private final SafeboxAccountService safeboxAccounts;

    public SafeboxDemoBootstrap(@Value("${app.demo.bootstrap-user-emails:}") String bootstrapEmails,
            UserRepository users, FinanceConnectionRepository connectionRows, FinanceConnectionService connections,
            SafeboxAccountService safeboxAccounts) {
        this.bootstrapEmails = bootstrapEmails;
        this.users = users;
        this.connectionRows = connectionRows;
        this.connections = connections;
        this.safeboxAccounts = safeboxAccounts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Arrays.stream(bootstrapEmails.split(","))
                .map(String::strip)
                .filter(email -> !email.isEmpty())
                .forEach(email -> {
                    try {
                        prepare(email);
                    } catch (RuntimeException exception) {
                        log.warn("Demo safebox bootstrap failed for masked email {}: {}",
                                FieldCryptor.maskEmail(email), exception.toString());
                    }
                });
    }

    /** 한 사용자를 준비한다. 사용자가 없으면 아무것도 하지 않는다. */
    public void prepare(String email) {
        Optional<User> user = users.findByEmailAndDeletedAtIsNull(email);
        if (user.isEmpty()) {
            log.info("Demo safebox bootstrap skipped: no user for masked email {}", FieldCryptor.maskEmail(email));
            return;
        }
        Long userId = user.get().getId();
        boolean connected = connectionRows.findByUserId(userId).map(FinanceConnection::isConnected).orElse(false);
        if (!connected) {
            connections.connect(userId);
        }
        safeboxAccounts.open(userId);
        log.info("Demo safebox bootstrap ready for user {}", userId);
    }
}
