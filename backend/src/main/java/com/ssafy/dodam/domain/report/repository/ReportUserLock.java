package com.ssafy.dodam.domain.report.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 행을 잠가 같은 사용자의 리포트 생성을 직렬화한다. 잠금 없이 "있으면 읽고 없으면 만든다"를 하면
 * 동시 요청이 둘 다 "없음"을 보고 INSERT해 한쪽이 `uk_report_user_type_period`에 걸린다.
 * `RecommendationUserLock`·`DiagnosisUserLock`과 같은 방식이다.
 */
public interface ReportUserLock extends Repository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
