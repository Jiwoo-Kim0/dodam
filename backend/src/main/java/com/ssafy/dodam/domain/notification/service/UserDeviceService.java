package com.ssafy.dodam.domain.notification.service;

import com.ssafy.dodam.domain.notification.dto.request.RegisterDeviceRequest;
import com.ssafy.dodam.domain.notification.dto.response.UserDeviceResponse;
import com.ssafy.dodam.domain.notification.entity.DevicePlatform;
import com.ssafy.dodam.domain.notification.entity.UserDevice;
import com.ssafy.dodam.domain.notification.repository.UserDeviceRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 기기 등록·해제·목록(S15P21D105-580). 명세 22에는 없지만 FCM 발송에 필요해 추가했다.
 *
 * <p>같은 토큰을 다시 등록하면 사용 시각만 갱신하고(해제됐던 기기는 다시 켠다), 다른 사용자가 같은 토큰을 등록하면 기기 소유자를 바꾼다.
 * 해제는 행 삭제다. 토큰은 {@link FieldCryptor}로 암호화·해시해 저장하고 응답에는 내보내지 않는다.
 */
@Service
@Transactional
public class UserDeviceService {

    private final UserDeviceRepository devices;
    private final FieldCryptor cryptor;
    private final NotificationClock clock;

    public UserDeviceService(UserDeviceRepository devices, FieldCryptor cryptor, NotificationClock clock) {
        this.devices = devices;
        this.cryptor = cryptor;
        this.clock = clock;
    }

    public UserDeviceResponse register(Long userId, RegisterDeviceRequest request) {
        DevicePlatform platform = DevicePlatform.parse(request.platform());
        String token = request.fcmToken().strip();
        String hash = cryptor.hash(token);
        byte[] encrypted = cryptor.encrypt(token);
        LocalDateTime now = clock.now();
        Optional<UserDevice> existing = devices.findByFcmTokenHash(hash);
        UserDevice device;
        if (existing.isPresent()) {
            device = existing.get();
            if (device.getUserId().equals(userId)) {
                device.touch(platform, encrypted, now);
            } else {
                device.transferTo(userId, platform, encrypted, now);
            }
        } else {
            device = devices.save(UserDevice.register(userId, encrypted, hash, platform, now));
        }
        return UserDeviceResponse.from(device);
    }

    public void unregister(Long userId, Long deviceId) {
        UserDevice device = devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("등록된 기기를 찾을 수 없습니다."));
        devices.delete(device);
    }

    @Transactional(readOnly = true)
    public List<UserDeviceResponse> list(Long userId) {
        return devices.findAllByUserIdOrderByIdAsc(userId).stream().map(UserDeviceResponse::from).toList();
    }
}
