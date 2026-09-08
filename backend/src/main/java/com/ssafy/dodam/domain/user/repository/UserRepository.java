package com.ssafy.dodam.domain.user.repository;

import com.ssafy.dodam.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 탈퇴 계정도 이메일 UNIQUE 제약에 포함되므로 삭제 여부를 조건에 넣지 않는다.
     */
    boolean existsByEmail(String email);
}
