package com.ssafy.dodam.domain.user.service;

import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.dto.FinancialProfileResponse;
import com.ssafy.dodam.domain.user.dto.UpdateFinancialProfileRequest;
import com.ssafy.dodam.domain.user.dto.UpdateUserRequest;
import com.ssafy.dodam.domain.user.dto.UpdateUserSettingRequest;
import com.ssafy.dodam.domain.user.dto.UserResponse;
import com.ssafy.dodam.domain.user.dto.UserSettingResponse;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserSetting;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 명세 2. User — 모든 메서드는 인증된 본인(userId)의 리소스만 다룬다.
 */
@Service
public class UserService {

    /** 나이 계산 기준일의 시간대. 다른 도메인 Clock과 같은 서비스 시간대다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final FinanceConnectionRepository financeConnectionRepository;
    private final AuthSessionRepository authSessionRepository;

    public UserService(
            UserRepository userRepository,
            UserSettingRepository userSettingRepository,
            FinancialProfileRepository financialProfileRepository,
            FinanceConnectionRepository financeConnectionRepository,
            AuthSessionRepository authSessionRepository
    ) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.financeConnectionRepository = financeConnectionRepository;
        this.authSessionRepository = authSessionRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = findActiveUser(userId);
        return toResponse(user);
    }

    /**
     * 이름·생년월일·성별 중 보낸 항목만 바꾼다(629). 신용등급은 금융망 조회 결과라 이 API로 바꿀 수 없다.
     */
    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        User user = findActiveUser(userId);
        if (request.name() != null) {
            user.changeName(request.name().trim());
        }
        if (request.birthDate() != null) {
            user.changeBirthDate(request.birthDate());
        }
        if (request.gender() != null) {
            user.changeGender(request.gender());
        }
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.of(user, connectionStatus(user.getId()), LocalDate.now(SERVICE_ZONE));
    }

    /**
     * 회원 탈퇴: soft delete + 모든 세션 폐기 + 금융망 연결 해제. 설정·프로필 행은 user_id FK 때문에 남겨둔다.
     */
    @Transactional
    public void withdraw(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        User user = findActiveUser(userId);
        user.withdraw(now);
        authSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(session -> session.revoke(now));
        financeConnectionRepository.findByUserId(userId)
                .ifPresent(connection -> connection.disconnect(now));
    }

    @Transactional
    public UserSettingResponse getSettings(Long userId) {
        findActiveUser(userId);
        return UserSettingResponse.from(findOrCreateSetting(userId));
    }

    @Transactional
    public UserSettingResponse updateSettings(Long userId, UpdateUserSettingRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        findActiveUser(userId);
        UserSetting setting = findOrCreateSetting(userId);
        setting.update(
                request.notificationEnabled(),
                request.dailyBriefingEnabled(),
                request.monthlyReportEnabled(),
                request.dailyBriefingTime() == null ? null : LocalTime.parse(request.dailyBriefingTime()),
                request.timezone() == null ? null : validateTimezone(request.timezone())
        );
        return UserSettingResponse.from(setting);
    }

    @Transactional
    public FinancialProfileResponse getFinancialProfile(Long userId) {
        findActiveUser(userId);
        return FinancialProfileResponse.from(findOrCreateProfile(userId));
    }

    @Transactional
    public FinancialProfileResponse updateFinancialProfile(Long userId, UpdateFinancialProfileRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        findActiveUser(userId);
        FinancialProfile profile = findOrCreateProfile(userId);
        profile.update(
                request.monthlyIncome(),
                request.annualIncome(),
                request.payday(),
                request.fixedExpense(),
                request.emergencyFundTarget()
        );
        return FinancialProfileResponse.from(profile);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }

    private FinanceConnectionStatus connectionStatus(Long userId) {
        return financeConnectionRepository.findByUserId(userId)
                .map(FinanceConnection::getStatus)
                .orElse(FinanceConnectionStatus.PENDING);
    }

    /**
     * 회원가입 때 기본 행을 만들지만, Seed로 들어온 사용자 등 행이 없는 경우를 대비해 없으면 만든다.
     */
    private UserSetting findOrCreateSetting(Long userId) {
        return userSettingRepository.findById(userId)
                .orElseGet(() -> userSettingRepository.save(UserSetting.defaults(userId)));
    }

    private FinancialProfile findOrCreateProfile(Long userId) {
        return financialProfileRepository.findById(userId)
                .orElseGet(() -> financialProfileRepository.save(FinancialProfile.empty(userId)));
    }

    private static String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_TIMEZONE);
        }
    }
}
