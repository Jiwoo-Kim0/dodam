package com.ssafy.dodam.domain.account.service;

import com.ssafy.dodam.domain.account.dto.response.AccountBalanceResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountResponse;
import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 5 계좌 목록·상세·잔액(S15P21D105-501). 우리 DB `account`만 읽는다.
 *
 * <p>명세는 세 API를 "SSAFY" 연동으로 표기하지만, 금융망 계좌 데이터를 원장으로 옮기는 일은 Finance Data 동기화의
 * ACCOUNT scope가 맡고 이 서비스는 그 결과를 조회한다. 잔액 실시간 조회(금융망 2.4 계좌 잔액 조회)를 붙일지는
 * remaining-work.md에 남겼다.
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    static final String ACCOUNT_NOT_FOUND_MESSAGE = "계좌를 찾을 수 없습니다.";

    /** 사용 중인 계좌가 먼저, 같은 상태 안에서는 은행명·계좌명·ID 순. */
    private static final Comparator<Account> LIST_ORDER = Comparator
            .comparing(Account::getStatus)
            .thenComparing(Account::getBankName)
            .thenComparing(Account::getAccountName)
            .thenComparing(Account::getId);

    private final AccountRepository accountRepository;
    private final SensitiveNumberHasher sensitiveNumberHasher;

    public AccountQueryService(AccountRepository accountRepository, SensitiveNumberHasher sensitiveNumberHasher) {
        this.accountRepository = accountRepository;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
    }

    /**
     * @param bankCode    은행 코드. 대소문자 무시, 빈 값은 전체
     * @param accountType 계좌 유형. 정의되지 않은 값은 400, 빈 값은 전체
     */
    public List<AccountResponse> getAccounts(Long userId, String bankCode, String accountType) {
        String bankCodeFilter = normalizeCode(bankCode);
        AccountType typeFilter = AccountType.parseFilter(accountType);

        return accountRepository.findAllByUserId(userId).stream()
                .filter(account -> bankCodeFilter == null || bankCodeFilter.equals(account.getBankCode()))
                .filter(account -> typeFilter == null || typeFilter == account.getAccountType())
                .sorted(LIST_ORDER)
                .map(AccountResponse::from)
                .toList();
    }

    public AccountResponse getAccount(Long userId, String accountNo) {
        return AccountResponse.from(findOwned(userId, accountNo));
    }

    public AccountBalanceResponse getBalance(Long userId, String accountNo) {
        return AccountBalanceResponse.from(findOwned(userId, accountNo));
    }

    private Account findOwned(Long userId, String accountNo) {
        return accountRepository
                .findByUserIdAndAccountNoHash(userId, sensitiveNumberHasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException(ACCOUNT_NOT_FOUND_MESSAGE));
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
