package com.ssafy.dodam.global.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 사용자에 속한 여러 자원을 함께 변경할 때 쓰는 비관적 쓰기 잠금. */
public interface UserWriteLock extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
