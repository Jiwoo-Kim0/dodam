package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.SafeboxAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SafeboxAccountRepository extends JpaRepository<SafeboxAccount, Long> {
}
