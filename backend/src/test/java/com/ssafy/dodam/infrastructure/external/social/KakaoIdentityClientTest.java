package com.ssafy.dodam.infrastructure.external.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoIdentityClientTest {

    private static final String USER_INFO = "https://kapi.kakao.com/v2/user/me";
    private static final String TOKEN_INFO = "https://kapi.kakao.com/v1/user/access_token_info";

    private MockRestServiceServer server;

    private KakaoIdentityClient client(String appId) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        SocialLoginProperties properties = new SocialLoginProperties(
                new SocialLoginProperties.Kakao(null, null, appId), null, null, null);
        return new KakaoIdentityClient(properties, new ObjectMapper(), builder);
    }

    @Test
    void mapsUserInfoToIdentityWithVerifiedEmail() {
        KakaoIdentityClient client = client(null);
        server.expect(requestTo(USER_INFO))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("""
                        {"id": 123456789, "connected_at": "2026-09-15T00:00:00Z",
                         "properties": {"nickname": "속성닉"},
                         "kakao_account": {"profile": {"nickname": "도담이"}, "has_email": true,
                                           "is_email_valid": true, "is_email_verified": true, "email": "Dodam@Kakao.com"}}
                        """, MediaType.APPLICATION_JSON));

        SocialIdentity identity = client.verify("tok");

        assertThat(identity.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(identity.providerUserId()).isEqualTo("123456789");
        assertThat(identity.email()).isEqualTo("dodam@kakao.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.name()).isEqualTo("도담이");
        server.verify();
    }

    @Test
    void unverifiedOrMissingEmailIsNotVerifiedAndFallsBackToPropertiesNickname() {
        KakaoIdentityClient client = client(null);
        server.expect(requestTo(USER_INFO)).andRespond(withSuccess("""
                {"id": 42, "properties": {"nickname": "속성닉"},
                 "kakao_account": {"has_email": true, "is_email_valid": true, "is_email_verified": false, "email": "x@y.com"}}
                """, MediaType.APPLICATION_JSON));

        SocialIdentity identity = client.verify("tok");

        assertThat(identity.email()).isEqualTo("x@y.com");
        assertThat(identity.emailVerified()).isFalse();
        assertThat(identity.name()).isEqualTo("속성닉");
    }

    @Test
    void unauthorizedResponseMeansInvalidToken() {
        KakaoIdentityClient client = client(null);
        server.expect(requestTo(USER_INFO))
                .andRespond(withUnauthorizedRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}"));

        assertThatThrownBy(() -> client.verify("expired"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID));
    }

    @Test
    void serverErrorMeansProviderUnavailable() {
        KakaoIdentityClient client = client(null);
        server.expect(requestTo(USER_INFO)).andRespond(withServerError());

        assertThatThrownBy(() -> client.verify("tok"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));
    }

    @Test
    void checksAppIdWhenConfiguredAndRejectsTokenFromAnotherApp() {
        KakaoIdentityClient client = client("777");
        server.expect(requestTo(TOKEN_INFO))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\": 42, \"expires_in\": 3600, \"app_id\": 999}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify("tok"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID));
        server.verify(); // 사용자 정보 API는 호출하지 않는다.
    }

    @Test
    void checksAppIdWhenConfiguredAndAcceptsOwnApp() {
        KakaoIdentityClient client = client("777");
        server.expect(requestTo(TOKEN_INFO))
                .andRespond(withSuccess("{\"id\": 42, \"expires_in\": 3600, \"app_id\": 777}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO))
                .andRespond(withSuccess("{\"id\": 42, \"kakao_account\": {}}", MediaType.APPLICATION_JSON));

        SocialIdentity identity = client.verify("tok");

        assertThat(identity.providerUserId()).isEqualTo("42");
        assertThat(identity.email()).isNull();
        assertThat(identity.name()).isNull();
        server.verify();
    }
}
