package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.domain.deposit.repository.DepositAccountReader;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import org.springframework.stereotype.Component;

/**
 * 경로 변수로 받은 계좌번호를 본인 예금으로 해석하는 단일 지점.
 *
 * <p>계좌번호 원문은 HMAC 해시로 바꾸는 데에만 쓰고 어디에도 남기지 않는다. 예외 메시지에도 넣지 않는다.
 * 카드·대출 서비스와 같은 {@link SensitiveNumberHasher} 주입 방식을 그대로 따르며 새 해시 방식이나
 * 평문 조회를 만들지 않는다.
 *
 * <p>없는 계좌, 타인 계좌, 예금이 아닌 계좌를 <b>모두 같은 {@code RESOURCE_NOT_FOUND}</b>로 만든다.
 * 셋을 구분하면 응답만으로 타인 계좌번호의 존재를 확인할 수 있게 된다.
 */
@Component
public class DepositAccountLookup {
    private final DepositAccountReader reader;
    private final SensitiveNumberHasher hasher;

    public DepositAccountLookup(DepositAccountReader reader, SensitiveNumberHasher hasher) {
        this.reader = reader;
        this.hasher = hasher;
    }

    DepositAccountRow require(Long userId, String accountNo) {
        return reader.findOwnedDeposit(userId, hasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException("예금 계좌를 찾을 수 없습니다."));
    }
}
