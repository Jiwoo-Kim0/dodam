package com.ssafy.dodam.domain.diagnosis.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 사용자 행을 잠가 여러 서버에서도 진단 접수와 완료 순서를 직렬화한다. */
public interface DiagnosisUserLock extends Repository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
