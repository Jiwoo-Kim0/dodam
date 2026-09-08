package com.ssafy.dodam.domain.user.repository;

import com.ssafy.dodam.domain.user.entity.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {
}
