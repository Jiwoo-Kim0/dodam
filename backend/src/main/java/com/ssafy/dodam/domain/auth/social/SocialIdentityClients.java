package com.ssafy.dodam.domain.auth.social;

import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 등록된 {@link SocialIdentityClient}를 공급자별로 찾는다. 구현 빈이 없는 공급자는 SOCIAL_PROVIDER_NOT_SUPPORTED.
 */
@Component
public class SocialIdentityClients {

    private final Map<SocialProvider, SocialIdentityClient> clients = new EnumMap<>(SocialProvider.class);

    public SocialIdentityClients(List<SocialIdentityClient> registered) {
        for (SocialIdentityClient client : registered) {
            SocialIdentityClient previous = clients.putIfAbsent(client.provider(), client);
            if (previous != null) {
                throw new IllegalStateException("SocialIdentityClient가 중복 등록됐습니다: " + client.provider());
            }
        }
    }

    public SocialIdentityClient get(SocialProvider provider) {
        SocialIdentityClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED);
        }
        return client;
    }
}
