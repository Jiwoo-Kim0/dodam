package com.ssafy.dodam.infrastructure.external.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClient;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 토큰 검증. 클라이언트(Expo 앱)가 카카오 SDK로 받은 access token을 서버가 사용자 정보 API(v2/user/me)로 확인한다.
 *
 * <p>카카오 REST API는 토큰이 무효하면 401과 {"msg": ..., "code": -401}을 돌려준다. 앱 ID가 설정돼 있으면 토큰 정보 API(v1/user/access_token_info)의
 * app_id를 먼저 대조해 다른 앱에서 발급한 토큰을 거른다. 이메일은 사용자가 동의한 경우에만 kakao_account.email로 오고,
 * is_email_valid·is_email_verified가 모두 true일 때만 소유 확인된 이메일로 본다.
 */
@Component
public class KakaoIdentityClient implements SocialIdentityClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoIdentityClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SocialLoginProperties.Kakao properties;

    @Autowired
    public KakaoIdentityClient(SocialLoginProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, defaultBuilder(properties));
    }

    KakaoIdentityClient(SocialLoginProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.properties = properties.kakao();
    }

    private static RestClient.Builder defaultBuilder(SocialLoginProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialIdentity verify(String accessToken) {
        if (properties.checksAppId()) {
            JsonNode tokenInfo = get(properties.tokenInfoUrl(), accessToken);
            String appId = tokenInfo.path("app_id").asText(null);
            if (appId == null || !appId.equals(properties.appId())) {
                log.warn("Kakao access token belongs to another app (app_id={})", appId);
                throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
            }
        }

        JsonNode me = get(properties.userInfoUrl(), accessToken);
        JsonNode id = me.path("id");
        if (id.isMissingNode() || id.isNull() || id.asText().isBlank()) {
            log.warn("Kakao user info has no id");
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        JsonNode account = me.path("kakao_account");
        String email = account.path("email").asText(null);
        boolean verified = account.path("is_email_valid").asBoolean(false)
                && account.path("is_email_verified").asBoolean(false);
        String name = account.path("profile").path("nickname").asText(null);
        if (name == null || name.isBlank()) {
            name = me.path("properties").path("nickname").asText(null);
        }
        return new SocialIdentity(SocialProvider.KAKAO, id.asText(), email, verified, name);
    }

    private JsonNode get(String url, String accessToken) {
        RawResponse response;
        try {
            response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchange((request, clientResponse) -> new RawResponse(
                            clientResponse.getStatusCode().value(),
                            new String(clientResponse.getBody().readAllBytes(), StandardCharsets.UTF_8)
                    ));
        } catch (ResourceAccessException exception) {
            log.warn("Kakao API unreachable: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            log.warn("Kakao API call failed: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        if (response.status() == 401 || response.status() == 403 || response.status() == 400) {
            // 무효·만료 토큰(-401), 권한 없음. 응답 본문의 카카오 오류 코드는 로그에만 남긴다.
            log.info("Kakao rejected access token: status={} body={}", response.status(), abbreviate(response.body()));
            throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }
        if (response.status() < 200 || response.status() >= 300) {
            log.warn("Kakao API error: status={} body={}", response.status(), abbreviate(response.body()));
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            log.warn("Kakao API response is not JSON: {}", abbreviate(response.body()));
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    private record RawResponse(int status, String body) {
    }
}
