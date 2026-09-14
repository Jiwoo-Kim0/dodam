package com.ssafy.dodam.domain.notification.repository;

import com.ssafy.dodam.domain.notification.entity.UserDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByFcmTokenHash(String fcmTokenHash);

    Optional<UserDevice> findByIdAndUserId(Long id, Long userId);

    List<UserDevice> findAllByUserIdOrderByIdAsc(Long userId);

    /** 푸시 발송 대상: 해제되지 않은 기기. */
    List<UserDevice> findAllByUserIdAndEnabledTrue(Long userId);
}
