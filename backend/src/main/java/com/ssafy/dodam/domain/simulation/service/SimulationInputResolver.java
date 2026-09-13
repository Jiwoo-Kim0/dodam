package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.transaction.service.SpendingQueryService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * HTTP 요청의 외부 식별자를 내부 값으로 바꾼다. 여기서 끝나야 계산기와 저장 경로에 카테고리 문자열·계좌번호 원문이 들어가지 않는다.
 *
 * <p>카테고리는 Spending API와 같은 해석(코드, 대소문자 무시, {@code UNCATEGORIZED} 허용)이며 없으면 400이다.
 * 계좌번호는 HMAC 해시로만 본인 계좌를 찾고 없거나 타인 것이면 404다 — 존재 여부를 흘리지 않는다.
 */
@Component
@Transactional(readOnly = true)
public class SimulationInputResolver {
    static final String CATEGORY_CODE_NOT_FOUND_MESSAGE = "존재하지 않는 카테고리 코드입니다.";
    private final TransactionCategoryRepository categories;
    private final AccountRepository accounts;
    private final SensitiveNumberHasher hasher;

    public SimulationInputResolver(TransactionCategoryRepository categories, AccountRepository accounts,
            SensitiveNumberHasher hasher) {
        this.categories = categories;
        this.accounts = accounts;
        this.hasher = hasher;
    }

    public SpendingReductionInput resolve(SpendingSimulationRequest request) {
        String code = null;
        if (request.category() != null && !request.category().isBlank()) {
            code = request.category().strip().toUpperCase(Locale.ROOT);
            if (!SpendingQueryService.UNCATEGORIZED_CODE.equals(code)) {
                code = categories.findByCode(code)
                        .orElseThrow(() -> new InvalidRequestException(CATEGORY_CODE_NOT_FOUND_MESSAGE)).getCode();
            }
        }
        return new SpendingReductionInput(code, request.monthlyReduction(), orDefault(request.durationMonths()));
    }

    public SavingChangeInput resolve(Long userId, SavingSimulationRequest request) {
        Long accountId = null;
        if (request.accountNo() != null && !request.accountNo().isBlank()) {
            accountId = accounts.findByUserIdAndAccountNoHash(userId, hasher.hash(request.accountNo().strip()))
                    .orElseThrow(() -> new ResourceNotFoundException("계좌를 찾을 수 없습니다."))
                    .getId();
        }
        return new SavingChangeInput(accountId, request.monthlySavingAmount(), orDefault(request.durationMonths()));
    }

    private static int orDefault(Integer durationMonths) {
        return durationMonths == null ? SpendingReductionInput.DEFAULT_DURATION_MONTHS : durationMonths;
    }
}
