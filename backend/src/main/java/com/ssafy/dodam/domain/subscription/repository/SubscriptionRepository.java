package com.ssafy.dodam.domain.subscription.repository;

import com.ssafy.dodam.domain.subscription.entity.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    /** 본인 정기결제만. 타인 것은 없는 것과 같게 취급한다. */
    Optional<Subscription> findByIdAndUserId(String id, Long userId);

    List<Subscription> findAllByUserId(Long userId);
}
