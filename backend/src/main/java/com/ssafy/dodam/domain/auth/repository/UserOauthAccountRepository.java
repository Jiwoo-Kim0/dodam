package com.ssafy.dodam.domain.auth.repository;

import com.ssafy.dodam.domain.auth.entity.UserOauthAccount;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, Long> {

    Optional<UserOauthAccount> findByProviderAndProviderUserIdHash(SocialProvider provider, String providerUserIdHash);

    boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);

    List<UserOauthAccount> findAllByUserIdOrderByIdAsc(Long userId);
}
