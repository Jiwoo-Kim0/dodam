package com.ssafy.dodam.domain.notification.controller;

import com.ssafy.dodam.domain.notification.dto.request.RegisterDeviceRequest;
import com.ssafy.dodam.domain.notification.dto.response.UserDeviceResponse;
import com.ssafy.dodam.domain.notification.service.UserDeviceService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 푸시 기기 토큰(S15P21D105-580). 명세 22 외 추가 API. 앱은 로그인·토큰 갱신 시 등록하고 로그아웃 시 해제한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/notifications/devices")
public class NotificationDeviceController {

    private final UserDeviceService service;
    private final AuthenticatedUserIdResolver users;

    public NotificationDeviceController(UserDeviceService service, AuthenticatedUserIdResolver users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDeviceResponse>> register(@Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.register(users.resolve(authentication), request)));
    }

    @GetMapping
    public ApiResponse<List<UserDeviceResponse>> list(Authentication authentication) {
        return ApiResponse.success(service.list(users.resolve(authentication)));
    }

    @DeleteMapping("/{deviceId}")
    public ApiResponse<Void> unregister(@PathVariable @Positive Long deviceId, Authentication authentication) {
        service.unregister(users.resolve(authentication), deviceId);
        return ApiResponse.success(null);
    }
}
