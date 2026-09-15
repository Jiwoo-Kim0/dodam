package com.ssafy.dodam.domain.pattern.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 행을 잠가 여러 서버에서도 분석 접수와 완료 순서를 직렬화한다(명세 "중복 분석 요청 방지").
 * Diagnosis의 `DiagnosisUserLock`과 같은 방식이다. 계산 동안에는 잠금을 잡지 않는다.
 */
public interface PatternUserLock extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
