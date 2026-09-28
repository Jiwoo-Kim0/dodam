package com.ssafy.dodam.domain.deposit.repository;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 금리 비교에 쓰는 예금 상품 조회 경계.
 *
 * <p>대출의 {@code LoanMarketProductReader}와 같은 이유로 <b>요청당 예금 상품 전량을 한 번 읽는다.</b>
 * 시연 데이터의 예금 상품은 15개, 옵션은 56건이라 전량 로드가 조건별 조회보다 단순하다.
 * {@code financial_product_option}에는 product_id 인덱스만 있어 term을 DB에서 거르는 이점도 없다.
 *
 * <p>상품 수가 크게 늘면 비교 조건을 DB로 내리는 것을 재검토한다. 그때의 판단 근거로 위 규모를 남긴다.
 */
@Component
public class DepositMarketProductReader {
    private final FinancialProductRepository products;

    public DepositMarketProductReader(FinancialProductRepository products) {
        this.products = products;
    }

    /** 회사와 옵션을 함께 읽는다. 지연 로딩으로 옵션을 건별 조회하면 N+1이 되므로 id를 먼저 모은다. */
    public List<FinancialProduct> findAllDepositProducts() {
        List<Long> ids = products.findAllByProductTypeOrderByIdAsc(ProductType.DEPOSIT).stream()
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
