package com.ssafy.dodam.domain.subscription.repository;

import com.ssafy.dodam.domain.subscription.entity.SubscriptionPaymentHistory;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentHistoryRepository extends JpaRepository<SubscriptionPaymentHistory, Long> {

    /** 최신순(같은 날이면 나중에 기록된 것 먼저). */
    Page<SubscriptionPaymentHistory> findAllBySubscriptionIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(
            String subscriptionId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /** 상세 집계용. 정기결제 한 건의 이력은 많아도 수십 건이다. */
    List<SubscriptionPaymentHistory> findAllBySubscriptionId(String subscriptionId);
}
