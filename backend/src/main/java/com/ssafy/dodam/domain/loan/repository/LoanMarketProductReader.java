package com.ssafy.dodam.domain.loan.repository;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 금리 비교에 쓰는 대출 상품 조회 경계.
 *
 * <p><b>요청당 대출 상품 전량을 한 번 읽는다.</b> 대출 건마다, 비교군마다 조회하지 않는다. 근거는 두 가지다.
 *
 * <ul>
 *   <li>규모: 시연 데이터의 대출 상품은 76개, 옵션은 319건이다. 전량 로드가 그룹별 조회보다 단순하고
 *       고금리 API가 사용자의 대출 여러 건을 평가할 때도 조회가 1회로 끝난다.</li>
 *   <li>인덱스: {@code financial_product_option}에는 product_id 인덱스만 있고 term·repayment_type
 *       인덱스가 없다. 상품의 금리 인덱스도 (product_type, min_rate, max_rate)라 옵션 조건을 DB에서
 *       거르는 이점이 없다.</li>
 * </ul>
 *
 * <p>상품 수가 크게 늘면 비교군 조건을 DB로 내리는 것을 재검토한다. 그때의 판단 근거로 위 규모를 남긴다.
 */
@Component
public class LoanMarketProductReader {
    private final FinancialProductRepository products;

    public LoanMarketProductReader(FinancialProductRepository products) {
        this.products = products;
    }

    /**
     * 회사와 옵션을 함께 읽는다. 지연 로딩으로 옵션을 건별 조회하면 N+1이 되므로 id 목록을 먼저 얻고
     * {@code findDetailsByIdIn}으로 한 번에 채운다.
     */
    public List<FinancialProduct> findAllLoanProducts() {
        List<Long> ids = products.findAllByProductTypeOrderByIdAsc(ProductType.LOAN).stream()
                .map(FinancialProduct::getId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return products.findDetailsByIdIn(ids).stream()
                .sorted(Comparator.comparing(FinancialProduct::getId))
                .toList();
    }
}
