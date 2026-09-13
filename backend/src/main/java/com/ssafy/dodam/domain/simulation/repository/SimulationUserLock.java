package com.ssafy.dodam.domain.simulation.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 행을 잠가 같은 사용자의 시뮬레이션 실행을 직렬화한다. 요청 지문 중복 판정이 JSON 안 값이라 DB UNIQUE가 없으므로
 * 이 잠금이 "같은 요청을 두 번 저장하지 않는다"의 실질 방어선이다. DiagnosisUserLock과 같은 방식이지만 도메인 결합을
 * 피하려고 따로 둔다.
 */
public interface SimulationUserLock extends Repository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
