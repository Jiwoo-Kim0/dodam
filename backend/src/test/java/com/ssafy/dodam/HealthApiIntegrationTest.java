package com.ssafy.dodam;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @AutoConfigureObservability}가 필요한 이유: Spring Boot는 테스트에서 메트릭 익스포터를
 * 기본적으로 끈다(테스트가 지표를 밖으로 내보내지 않게 하려는 기본값). 그대로 두면 운영에서는
 * 정상인 {@code /actuator/prometheus}가 테스트에서만 404가 되어, 회귀 테스트가 오히려 거짓 실패를 낸다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureObservability
@SpringBootTest
class HealthApiIntegrationTest {

    private static final String TEST_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthApiReturnsCommonResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.message").value(nullValue()))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    /**
     * 부하 테스트(S15P21D105-693)의 전제. 이 엔드포인트가 없거나 인증에 막히면 Prometheus의
     * {@code up{job="backend"}}가 0이 되고 CPU·메모리·DB풀·오류 지표가 통째로 비어 버린다.
     *
     * <p>실제로 Day 6에 대시보드를 붙인 뒤 2026-09-22까지 한 번도 수집되지 않고 있었다.
     * 노출 설정(`management.endpoints.web.exposure.include`)·의존성(micrometer-registry-prometheus)·
     * SecurityConfig 세 곳 중 하나만 되돌아가도 조용히 다시 0이 되므로 여기서 잠근다.
     *
     * <p>외부 노출은 nginx가 {@code location ^~ /actuator/ { return 404; }}로 막는다.
     */
    @Test
    void prometheusEndpointIsScrapableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                // 수집기가 읽을 수 있는 형식인지와, 부하 분석에 쓸 핵심 지표가 실제로 있는지까지 본다
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hikaricp_connections")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("application=\"dodam-backend\"")));
    }

    @Test
    void actuatorHealthIsPublicAndUsesActuatorFormat() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void unexposedActuatorEndpointIsNotPubliclyAvailable() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header(HttpHeaders.ORIGIN, TEST_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        TEST_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }
}
