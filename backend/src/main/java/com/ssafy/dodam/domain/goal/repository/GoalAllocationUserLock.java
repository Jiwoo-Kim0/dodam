package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 같은 사용자의 목표 연결·할당 변경을 직렬화한다.
 *
 * <p>입출금 한 계좌를 여러 목표가 공유할 수 있으므로, 계좌별 할당 가능액 검증과 저장 사이에 다른 요청이 끼면 총 할당액이
 * 잔액을 넘을 수 있다. 모든 변경 경로가 사용자 행을 먼저 잠그면 계좌·목표 수와 관계없이 잠금 순서가 일정해 교착도 피한다.
 */
public interface GoalAllocationUserLock extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
