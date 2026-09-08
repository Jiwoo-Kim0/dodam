package com.ssafy.dodam.domain.user.repository;

import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialProfileRepository extends JpaRepository<FinancialProfile, Long> {
}
