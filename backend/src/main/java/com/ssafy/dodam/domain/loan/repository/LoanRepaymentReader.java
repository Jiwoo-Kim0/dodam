package com.ssafy.dodam.domain.loan.repository;

import com.ssafy.dodam.global.policy.QueryPeriod;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 대출 상환내역 조회 경계.
 *
 * <p>명세상 데이터 출처는 SSAFY 금융망이지만 현재는 사전 생성된 시연 데이터를 로컬
 * `loan_repayment`에서 읽는다. 카드 사용내역과 같은 방식이며 외부 연동이 붙으면 구현만 교체한다.
 */
public interface LoanRepaymentReader {

    // TODO(S15P21D105-531): SSAFY 대출 상환내역 API 연동 후 로컬 조회 구현을 교체한다.
    Page<LoanRepaymentRow> read(Long loanId, QueryPeriod period, Pageable pageable);

    /**
     * 사용자의 모든 대출에서 기간 안에 실제로 낸({@code PAID}) 상환액 합계. 없으면 0.
     * 예정·실패·연체 회차는 그 달 소득에서 빠져나간 돈이 아니라 넣지 않는다(월간 리포트 부채 상환 점수, 712).
     */
    long sumPaidAmount(Long userId, LocalDate startDate, LocalDate endDate);
}
