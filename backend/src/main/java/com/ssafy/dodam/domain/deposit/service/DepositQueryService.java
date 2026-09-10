package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.domain.deposit.dto.response.DepositDetailResponse;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountReader;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예금 상세 조회.
 *
 * <p><b>데이터 출처는 로컬 시연 DB다.</b> 원본 명세 11-1의 출처는 SSAFY지만 535와 같은 판단으로 외부
 * 금융망 호출과 거래고유번호 생성을 하지 않는다. 시연 데이터가 이미 DB에 적재돼 있어 금융망 왕복이
 * 같은 값을 다시 가져오는 일이 되고, 금융망 연결이 없는 사용자도 본인 데이터를 볼 수 있어야 한다.
 */
@Service
@Transactional(readOnly = true)
public class DepositQueryService {
    static final String DATA_SOURCE = "LOCAL_DEMO_DB";

    private final DepositAccountLookup lookup;
    private final FinancialProductRepository products;
    private final DepositClock clock;

    public DepositQueryService(
            DepositAccountLookup lookup, FinancialProductRepository products, DepositClock clock
    ) {
        this.lookup = lookup;
        this.products = products;
        this.clock = clock;
    }

    public DepositDetailResponse detail(Long userId, String accountNo) {
        DepositAccountRow deposit = lookup.require(userId, accountNo);
        LocalDate today = clock.today();
        // 상세행이 없어도 계좌는 존재한다. 404로 감추면 목록(535)에는 보이는 계좌가 상세에서 사라진다.
        //
        // 상품 유형을 조회 조건에 넣는다. deposit_saving의 FK는 financial_product를 가리키기만 하고
        // 유형을 제약하지 않아, 잘못 연결된 대출·카드 상품명이 예금 상세에 실릴 수 있다.
        // 이 메서드는 회사·옵션을 EntityGraph로 함께 읽어 지연 로딩 추가 조회도 없앤다.
        Optional<FinancialProduct> product = deposit.financialProductId() == null
                ? Optional.empty()
                : products.findByIdAndProductType(deposit.financialProductId(), ProductType.DEPOSIT);
        LocalDate opening = deposit.openingDate();
        LocalDate expiry = deposit.expiryDate();
        boolean hasTerm = opening != null && expiry != null;
        return new DepositDetailResponse(
                today, DATA_SOURCE, deposit.accountId(), deposit.accountName(),
                deposit.bankCode(), deposit.bankName(), deposit.accountNoMasked(),
                DepositAccountReader.DEPOSIT, deposit.status(), deposit.balance(),
                deposit.detailsAvailable(), deposit.interestRate(), opening, expiry,
                hasTerm ? (int) ChronoUnit.MONTHS.between(opening, expiry) : null,
                hasTerm ? ChronoUnit.DAYS.between(opening, expiry) : null,
                expiry == null ? null : ChronoUnit.DAYS.between(today, expiry),
                expiry == null ? null : !expiry.isAfter(today),
                product.map(FinancialProduct::getId).orElse(null),
                product.map(FinancialProduct::getProductName).orElse(null),
                product.map(item -> item.getCompany().getCompanyName()).orElse(null),
                product.map(FinancialProduct::getSavingType).orElse(null),
                product.map(FinancialProduct::getSyncedAt).orElse(null));
    }
}
