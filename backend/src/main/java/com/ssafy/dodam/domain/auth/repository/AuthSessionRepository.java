package com.ssafy.dodam.domain.auth.repository;

import com.ssafy.dodam.domain.auth.entity.AuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * 본인 리소스만 접근한다는 제한사항 때문에 세션 단건 조회는 항상 user_id를 함께 건다.
     */
    Optional<AuthSession> findByIdAndUserId(Long id, Long userId);
}
