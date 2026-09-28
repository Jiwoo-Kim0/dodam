package com.ssafy.dodam.domain.pattern.repository;

import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatternAnalysisRepository extends JpaRepository<PatternAnalysis, Long> {

    Optional<PatternAnalysis> findByIdAndUserId(Long id, Long userId);

    /** 최신 결과 1건. 조회 API는 COMPLETED만 본다. */
    Optional<PatternAnalysis> findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(Long userId, PatternAnalysisStatus status);

    /** 임대 기간 안의 진행 중 접수. 중복 분석 요청 방지에 쓴다. */
    Optional<PatternAnalysis> findFirstByUserIdAndStatusAndAnalyzedAtAfterOrderByIdDesc(
            Long userId, PatternAnalysisStatus status, LocalDateTime since);

    boolean existsByUserIdAndIdGreaterThan(Long userId, Long id);
}
