package com.ssafy.dodam.domain.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.card.repository.CardRepository;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.domain.simulation.dto.CashAdvanceInput;
import com.ssafy.dodam.domain.simulation.dto.CombinedInput;
import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SavingProductInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.dto.request.CashAdvanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.CombinedSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.InstallmentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanNewSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.RevolvingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingProductSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.transaction.service.SpendingQueryService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
 *
 * <p>586: 리볼빙 cardId는 소유권 조건이 들어간 조회로 확인한다(없거나 타인이면 같은 404). 복합은 자식마다 같은 해석·검증을
 * 거쳐 모든 자식의 자원 존재·소유권을 지문 조회 <b>전</b>에 끝낸 뒤 자식 간 중복·충돌을 검사한다 — 재사용 반환 경로로
 * 소유권 검증을 우회할 수 없다.
 *
 * <p>619: 저축 시뮬레이션의 {@code goalId}는 소유권(404)과 함께 SAVING·ACTIVE(400)까지 여기서 확인한다. 기준선이 ACTIVE 목표만
 * 읽으므로 그 밖의 목표는 효과를 계산할 수 없고, 조용히 전체 비례 배분으로 바꾸면 사용자가 고른 목표가 무시된 줄 모르게 된다.
 */
@Component
@Transactional(readOnly = true)
public class SimulationInputResolver {
    static final String CATEGORY_CODE_NOT_FOUND_MESSAGE = "존재하지 않는 카테고리 코드입니다.";
    static final String PRODUCT_NOT_FOUND_MESSAGE = "상품을 찾을 수 없습니다.";
    static final String LOAN_NOT_FOUND_MESSAGE = "대출을 찾을 수 없습니다.";
    static final String CARD_NOT_FOUND_MESSAGE = "카드를 찾을 수 없습니다.";
    static final String GOAL_NOT_FOUND_MESSAGE = "목표를 찾을 수 없습니다.";
    static final String GOAL_AND_ACCOUNT_BOTH_MESSAGE = "goalId와 accountNo는 함께 지정할 수 없습니다.";
    static final String LOAN_ACCOUNT_OR_ID_MESSAGE = "accountNo와 loanId 중 하나만 지정해야 합니다.";
    static final String GOAL_NOT_SAVING_MESSAGE = "모으기 목표만 지정할 수 있습니다.";
    static final String GOAL_NOT_ACTIVE_MESSAGE = "진행 중인 목표만 지정할 수 있습니다.";
    private final TransactionCategoryRepository categories;
    private final AccountRepository accounts;
    private final FinancialProductRepository products;
    private final LoanRepository loans;
    private final CardRepository cards;
    private final GoalRepository goals;
    private final SensitiveNumberHasher hasher;
    private final CombinedScenarioParser scenarioParser;

    public SimulationInputResolver(TransactionCategoryRepository categories, AccountRepository accounts,
            FinancialProductRepository products, LoanRepository loans, CardRepository cards, GoalRepository goals,
            SensitiveNumberHasher hasher, CombinedScenarioParser scenarioParser) {
        this.categories = categories;
        this.accounts = accounts;
        this.products = products;
        this.loans = loans;
        this.cards = cards;
        this.goals = goals;
        this.hasher = hasher;
        this.scenarioParser = scenarioParser;
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
        boolean hasAccount = request.accountNo() != null && !request.accountNo().isBlank();
        if (hasAccount && request.goalId() != null) {
            throw new InvalidRequestException(GOAL_AND_ACCOUNT_BOTH_MESSAGE);
        }
        Long accountId = null;
        if (hasAccount) {
            accountId = accounts.findByUserIdAndAccountNoHash(userId, hasher.hash(request.accountNo().strip()))
                    .orElseThrow(() -> new ResourceNotFoundException("계좌를 찾을 수 없습니다."))
                    .getId();
        }
        Long goalId = request.goalId() == null ? null : ownedActiveSavingGoalId(userId, request.goalId());
        return new SavingChangeInput(accountId, goalId, request.monthlySavingAmount(), orDefault(request.durationMonths()));
    }

    /** 소유권은 진단(goalId 지정)과 같은 조회로 404, 유형·상태는 소유권 확인 뒤라 존재 노출 없이 400이다. */
    private Long ownedActiveSavingGoalId(Long userId, Long goalId) {
        Goal goal = goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND_MESSAGE));
        if (goal.getType() != GoalType.SAVING) {
            throw new InvalidRequestException(GOAL_NOT_SAVING_MESSAGE);
        }
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new InvalidRequestException(GOAL_NOT_ACTIVE_MESSAGE);
        }
        return goal.getId();
    }

    public SavingProductInput resolve(SavingProductSimulationRequest request) {
        return new SavingProductInput(existingProductId(request.productId()), request.monthlyAmount(), request.term());
    }

    public LoanNewInput resolve(LoanNewSimulationRequest request) {
        return new LoanNewInput(existingProductId(request.productId()), request.principal(), request.term(), request.repaymentType());
    }

    /** {@code paymentDate}는 여기서 채우지 않는다. 기준일은 Runner가 한 번 얻고 {@code withBaseDate}로 채운다. */
    public LoanPrepaymentInput resolve(Long userId, LoanPrepaymentSimulationRequest request) {
        return new LoanPrepaymentInput(ownedLoanId(userId, request.accountNo(), request.loanId()), request.prepaymentAmount(),
                request.paymentDate());
    }

    public LoanRefinanceInput resolve(Long userId, LoanRefinanceSimulationRequest request) {
        return new LoanRefinanceInput(ownedLoanId(userId, request.accountNo(), request.loanId()), existingProductId(request.targetProductId()),
                request.refinanceAmount());
    }

    public InstallmentInput resolve(InstallmentSimulationRequest request) {
        return InstallmentInput.of(request.purchaseAmount(), request.months(), request.feeRate());
    }

    public RevolvingInput resolve(Long userId, RevolvingSimulationRequest request) {
        Long cardId = request.cardId();
        if (cardId != null && !cards.existsByIdAndUserId(cardId, userId)) {
            throw new ResourceNotFoundException(CARD_NOT_FOUND_MESSAGE);
        }
        return new RevolvingInput(cardId, request.balance(), request.paymentRatio(), request.rate());
    }

    public CashAdvanceInput resolve(CashAdvanceSimulationRequest request) {
        return CashAdvanceInput.of(request.amount(), request.rate(), request.repaymentMonths());
    }

    /** 자식은 입력 순서대로 단독 API와 같은 해석을 거친다. 자식 하나의 오류(400/404)는 전체 요청 오류다. */
    public CombinedInput resolve(Long userId, CombinedSimulationRequest request) {
        List<SimulationInput> children = new ArrayList<>(request.scenarios().size());
        for (int i = 0; i < request.scenarios().size(); i++) {
            JsonNode scenario = request.scenarios().get(i);
            children.add(resolveChild(userId, scenarioParser.parse(i, scenario)));
        }
        CombinedScenarioValidator.validate(children);
        return new CombinedInput(children);
    }

    private SimulationInput resolveChild(Long userId, CombinedScenarioParser.ParsedScenario scenario) {
        return switch (scenario.type()) {
            case SPENDING -> resolve((SpendingSimulationRequest) scenario.request());
            case SAVING -> resolve(userId, (SavingSimulationRequest) scenario.request());
            case SAVING_PRODUCT -> resolve((SavingProductSimulationRequest) scenario.request());
            case LOAN_NEW -> resolve((LoanNewSimulationRequest) scenario.request());
            case LOAN_PREPAYMENT -> resolve(userId, (LoanPrepaymentSimulationRequest) scenario.request());
            case LOAN_REFINANCE -> resolve(userId, (LoanRefinanceSimulationRequest) scenario.request());
            case INSTALLMENT -> resolve((InstallmentSimulationRequest) scenario.request());
            case REVOLVING -> resolve(userId, (RevolvingSimulationRequest) scenario.request());
            case CASH_ADVANCE -> resolve((CashAdvanceSimulationRequest) scenario.request());
            case COMBINED -> throw new IllegalStateException("파서가 중첩 복합을 거르지 못했습니다.");
        };
    }

    private Long existingProductId(Long productId) {
        if (!products.existsById(productId)) {
            throw new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE);
        }
        return productId;
    }

    /**
     * 계좌번호 원문 또는 내부 {@code loanId} 중 정확히 하나로 본인 대출을 찾는다. 두 경로 모두 타인·미존재는 같은 404라
     * 식별자 방식에 따라 존재 노출 정도가 달라지지 않는다.
     */
    private Long ownedLoanId(Long userId, String accountNo, Long loanId) {
        boolean hasAccount = accountNo != null && !accountNo.isBlank();
        if (hasAccount == (loanId != null)) {
            throw new InvalidRequestException(LOAN_ACCOUNT_OR_ID_MESSAGE);
        }
        Optional<Loan> loan = hasAccount
                ? loans.findByUserIdAndAccountNoHash(userId, hasher.hash(accountNo.strip()))
                : loans.findByIdAndUserId(loanId, userId);
        return loan.orElseThrow(() -> new ResourceNotFoundException(LOAN_NOT_FOUND_MESSAGE)).getId();
    }

    private static int orDefault(Integer durationMonths) {
        return durationMonths == null ? SpendingReductionInput.DEFAULT_DURATION_MONTHS : durationMonths;
    }
}
