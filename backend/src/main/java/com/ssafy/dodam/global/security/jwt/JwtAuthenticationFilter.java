package com.ssafy.dodam.global.security.jwt;

import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더의 Access Token을 검증해 SecurityContext를 채운다.
 *
 * 토큰이 잘못돼도 여기서 바로 401을 쓰지 않는다. 오류 코드만 request attribute에 남기고 체인을 계속 태워,
 * 공개 경로(로그인 등)는 오래된 헤더가 붙어 있어도 정상 동작하게 하고, 보호 경로는 EntryPoint가
 * TOKEN_EXPIRED / INVALID_TOKEN처럼 구체적인 코드로 응답하게 한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".errorCode";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                AuthPrincipal principal = tokenProvider.parseAccessToken(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(principal.role().authority()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException exception) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ERROR_CODE_ATTRIBUTE, exception.getErrorCode());
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
