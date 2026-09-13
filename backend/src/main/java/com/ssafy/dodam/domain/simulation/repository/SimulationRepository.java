package com.ssafy.dodam.domain.simulation.repository;

import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모든 조회에 `userId`와 `deletedAt IS NULL`이 들어간다. 타인·없는·삭제된 시뮬레이션은 모두 `Optional.empty()`·빈 결과라
 * 존재 여부를 구분해 노출하지 않는다.
 */
public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    Optional<Simulation> findBySimulationIdAndUserIdAndDeletedAtIsNull(Long simulationId, Long userId);

    /** type이 null이면 필터를 생략한다. 정렬은 최신순이며 같은 시각은 id로 고정한다. */
    @Query("""
            select s from Simulation s
            where s.userId = :userId and s.deletedAt is null
              and (:type is null or s.type = :type)
            order by s.createdAt desc, s.simulationId desc
            """)
    Page<Simulation> findOwned(@Param("userId") Long userId, @Param("type") SimulationType type, Pageable pageable);

    /**
     * 요청 지문 재사용 후보. 지문은 `request_snapshot` JSON 안에 있어 H2·MySQL 공통 JPQL로 걸 수 없으므로
     * 같은 유형·같은 날의 미삭제 행만 가져와 서비스가 지문을 비교한다. 사용자·하루 단위라 양은 작다.
     */
    List<Simulation> findAllByUserIdAndTypeAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderBySimulationIdDesc(
            Long userId, SimulationType type, LocalDateTime from, LocalDateTime to);
}
