package com.ssafy.dodam.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        // JwtAuthenticationFilter가 토큰 오류를 남겨둔 경우 그 코드를 우선 사용한다.
        Object attribute = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        ErrorCode errorCode = attribute instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode.name(), errorCode.message())
        );
    }
}
