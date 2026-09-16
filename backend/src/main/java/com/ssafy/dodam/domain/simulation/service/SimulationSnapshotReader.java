package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.FinancialProductOption;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.domain.simulation.dto.CombinedInput;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.SavingProductInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 585 유형의 준비 단계. 상품·대출 엔티티를 불변 스냅샷으로 바꿔 입력에 채운다. Runner가 잠금 안, 요청 지문 조회 뒤에
 * 신규 계산일 때만 부른다 — 같은 날 같은 요청은 상품이 바뀌어도 첫 결과를 재사용해야 하므로 지문 전에 상품을 읽지 않는다.
 *
 * <p>순수 계산기에 엔티티를 넘기지 않는 이유: 지연 로딩·영속성 컨텍스트 없이 단위 테스트가 돌아야 하고, 결과에 저장하는
 * "그때의 상품 조건"이 계산에 쓴 값과 같아야 한다. 계좌번호 원문·해시·마스킹값은 스냅샷에 넣지 않는다.
 */
@Component
@Transactional(readOnly = true)
public class SimulationSnapshotReader {
    static final String PRODUCT_NOT_FOUND_MESSAGE = "상품을 찾을 수 없습니다.";
    static final String LOAN_NOT_FOUND_MESSAGE = "대출을 찾을 수 없습니다.";
    private final FinancialProductRepository products;
    private final LoanRepository loans;

    public SimulationSnapshotReader(FinancialProductRepository products, LoanRepository loans) {
        this.products = products;
        this.loans = loans;
    }

    /**
     * 585 유형이 아니면 입력을 그대로 돌려준다(기존 spending/saving은 준비 단계가 없다).
     * 복합은 자식마다 같은 준비를 거친다 — 모든 자식이 같은 기준일·같은 시점의 스냅샷을 쓴다.
     */
    public SimulationInput prepare(Long userId, SimulationInput input) {
        if (input instanceof CombinedInput combined) {
            List<SimulationInput> prepared = new ArrayList<>(combined.children().size());
            for (SimulationInput child : combined.children()) {
                prepared.add(prepare(userId, child));
            }
            return combined.withChildren(prepared);
        }
        if (input instanceof SavingProductInput saving) {
            return saving.withProduct(readProduct(saving.productId()));
        }
        if (input instanceof LoanNewInput loanNew) {
            return loanNew.withProduct(readProduct(loanNew.productId()));
        }
        if (input instanceof LoanPrepaymentInput prepayment) {
            return prepayment.withLoan(readLoan(userId, prepayment.loanId()));
        }
        if (input instanceof LoanRefinanceInput refinance) {
            return refinance.withSnapshots(readLoan(userId, refinance.loanId()), readProduct(refinance.targetProductId()));
        }
        return input;
    }

    public ProductSnapshot readProduct(Long productId) {
        FinancialProduct product = products.findDetailsByIdIn(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT_NOT_FOUND_MESSAGE));
        return toSnapshot(product);
    }

    /** 소유권 조건을 포함한다. 없거나 타인 것이면 같은 404다. 연결 상품의 loan_type은 대환 비교군 판정용으로 함께 읽는다. */
    public LoanSnapshot readLoan(Long userId, Long loanId) {
        Loan loan = loans.findById(loanId).filter(l -> userId.equals(l.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException(LOAN_NOT_FOUND_MESSAGE));
        String productLoanType = loan.getFinancialProductId() == null ? null
                : products.findById(loan.getFinancialProductId()).map(FinancialProduct::getLoanType).orElse(null);
        return new LoanSnapshot(loan.getId(), loan.getAccountName(), loan.getInstitutionName(), loan.getLoanType(),
                loan.getRepaymentType(), loan.getStatus(), loan.getBalance() == null ? 0L : loan.getBalance(),
                loan.getInterestRate(), loan.getRepaymentDay(), loan.getNextRepaymentDate(), loan.getMaturityDate(),
                loan.getFinancialProductId(), productLoanType);
    }

    /** 회사·옵션이 로드된 엔티티를 스냅샷으로 바꾼다. 진단(627)의 대환 절감 추정도 같은 변환을 쓰므로 public이다. */
    public static ProductSnapshot toSnapshot(FinancialProduct product) {
        List<ProductSnapshot.Option> options = product.getOptions().stream()
                .sorted(Comparator.comparing(FinancialProductOption::getTerm, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FinancialProductOption::getId))
                .map(o -> new ProductSnapshot.Option(o.getId(), o.getTerm(), o.getRate(), o.getRateType(), o.getMinRate(),
                        o.getMaxRate(), o.getRepaymentType(), o.getLimitAmount()))
                .toList();
        return new ProductSnapshot(product.getId(), product.getProductName(), product.getCompany().getCompanyName(),
                product.getProductType(), product.getSavingType(), product.getLoanType(), product.getMaxLimit(),
                product.getSyncedAt(), options);
    }
}
