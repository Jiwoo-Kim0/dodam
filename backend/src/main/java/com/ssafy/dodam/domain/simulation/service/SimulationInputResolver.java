package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SavingProductInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.dto.request.LoanNewSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingProductSimulationRequest;
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
 * 계좌번호는 HMAC 해시로만 본인 계좌·대출을 찾고 없거나 타인 것이면 404다 — 존재 여부를 흘리지 않는다.
 *
 * <p>585 유형은 여기서 상품 존재(404)와 대출 소유권(404)만 확인한다. 상품 유형·옵션 적합성(400)은 Runner의 지문 조회 뒤
 * 신규 계산에서만 검증한다 — 같은 날 상품이 바뀌어도 같은 요청은 첫 결과를 재사용해야 하기 때문이다.
 */
@Component
@Transactional(readOnly = true)
public class SimulationInputResolver {
    static final String CATEGORY_CODE_NOT_FOUND_MESSAGE = "존재하지 않는 카테고리 코드입니다.";
    static final String PRODUCT_NOT_FOUND_MESSAGE = "상품을 찾을 수 없습니다.";
    static final String LOAN_NOT_FOUND_MESSAGE = "대출을 찾을 수 없습니다.";
    private final TransactionCategoryRepository categories;
    private final AccountRepository accounts;
    private final FinancialProductRepository products;
    private final LoanRepository loans;
    private final SensitiveNumberHasher hasher;

    public SimulationInputResolver(TransactionCategoryRepository categories, AccountRepository accounts,
            FinancialProductRepository products, LoanRepository loans, SensitiveNumberHasher hasher) {
        this.categories = categories;
        this.accounts = accounts;
        this.products = products;
        this.loans = loans;
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

    public SavingProductInput resolve(SavingProductSimulationRequest request) {
        return new SavingProductInput(existingProductId(request.productId()), request.monthlyAmount(), request.term());
    }

    public LoanNewInput resolve(LoanNewSimulationRequest request) {
        return new LoanNewInput(existingProductId(request.productId()), request.principal(), request.term(), request.repaymentType());
    }

    /** {@code paymentDate}는 여기서 채우지 않는다. 기준일은 Runner가 한 번 얻고 {@code withBaseDate}로 채운다. */
    public LoanPrepaymentInput resolve(Long userId, LoanPrepaymentSimulationRequest request) {
        return new LoanPrepaymentInput(ownedLoanId(userId, request.accountNo()), request.prepaymentAmount(), request.paymentDate());
    }

    public LoanRefinanceInput resolve(Long userId, LoanRefinanceSimulationRequest request) {
        return new LoanRefinanceInput(ownedLoanId(userId, request.accountNo()), existingProductId(request.targetProductId()),
                request.refinanceAmount());
    }

    private Long existingProductId(Long productId) {
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE);
        }
        return productId;
    }

    private Long ownedLoanId(Long userId, String accountNo) {
        return loans.findByUserIdAndAccountNoHash(userId, hasher.hash(accountNo.strip()))
                .orElseThrow(() -> new ResourceNotFoundException(LOAN_NOT_FOUND_MESSAGE))
                .getId();
    }

    private static int orDefault(Integer durationMonths) {
        return durationMonths == null ? SpendingReductionInput.DEFAULT_DURATION_MONTHS : durationMonths;
    }
}
