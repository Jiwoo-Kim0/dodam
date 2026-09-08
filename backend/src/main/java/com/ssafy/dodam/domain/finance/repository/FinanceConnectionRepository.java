package com.ssafy.dodam.domain.finance.repository;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceConnectionRepository extends JpaRepository<FinanceConnection, Long> {

    Optional<FinanceConnection> findByUserId(Long userId);
}
