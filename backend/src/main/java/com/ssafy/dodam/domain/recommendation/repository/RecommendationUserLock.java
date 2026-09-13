package com.ssafy.dodam.domain.recommendation.repository;

import com.ssafy.dodam.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 행을 잠가 같은 사용자의 추천 생성을 직렬화한다.
 *
 * <p>`DiagnosisUserLock`과 같은 방식이지만 진단 패키지의 Repository를 가져다 쓰지 않는다. 도메인 간
 * 결합을 피하기 위해서다. 같은 `user` 행을 잠그므로 진단 완료와 추천 저장이 서로 직렬화되는 것은
 * 의도된 동작이다. 잠금 없이 `generationKey` 조회 → 저장을 하면 동시 요청이 둘 다 "없음"을 보고
 * INSERT해 한쪽이 UNIQUE 예외로 실패한다.
 */
public interface RecommendationUserLock extends Repository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId and u.deletedAt is null")
    Optional<User> lock(@Param("userId") Long userId);
}
