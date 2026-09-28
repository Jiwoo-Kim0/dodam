package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.dto.CreditRatingCriteriaResponse;
import com.ssafy.dodam.domain.finance.dto.CreditRatingResponse;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRating;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRatingCriterion;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API 명세 4. Finance Data — 신용등급 조회 (S15P21D105-500).
 * 금융망 실시간 조회다. 호출마다 새 기관거래고유번호를 쓴다 (SsafyHeaderFactory).
 * 내 신용등급은 사용자 단위(userKey 필요), 기준표는 앱 단위 API다.
 *
 * <p>629: 내 신용등급 조회가 성공하면 등급명(A~E)을 `user.credit_rating`에 기록한다. 응답은 여전히 금융망 실시간 값이고,
 * 저장은 GET /users/me와 분석 도메인이 최근 등급을 금융망 재호출 없이 쓰기 위한 것이다. 금융망 호출은 트랜잭션 밖에서,
 * 기록은 짧은 별도 트랜잭션에서 한다(동기화·연결 서비스와 같은 규칙).
 */
@Service
public class CreditRatingService {

    private static final Logger log = LoggerFactory.getLogger(CreditRatingService.class);

    private final UserRepository userRepository;
    private final FinanceConnectionService financeConnectionService;
    private final SsafyFinanceClient ssafyFinanceClient;
    private final TransactionTemplate transactionTemplate;

    public CreditRatingService(
            UserRepository userRepository,
            FinanceConnectionService financeConnectionService,
            SsafyFinanceClient ssafyFinanceClient,
            TransactionTemplate transactionTemplate
    ) {
        this.userRepository = userRepository;
        this.financeConnectionService = financeConnectionService;
        this.ssafyFinanceClient = ssafyFinanceClient;
        this.transactionTemplate = transactionTemplate;
    }

    public CreditRatingResponse getMyCreditRating(Long userId) {
        String userKey = userKeyOf(userId);
        SsafyCreditRating rating = SsafyCalls.call(
                "credit rating inquiry", () -> ssafyFinanceClient.inquireMyCreditRating(userKey)
        );
        LocalDateTime inquiredAt = LocalDateTime.now();
        record(userId, rating, inquiredAt);
        return CreditRatingResponse.of(rating, inquiredAt);
    }

    /**
     * 금융 데이터 동기화가 끝난 뒤 신용등급을 최신화한다(629). 동기화 결과에는 넣지 않는 부수 갱신이라 금융망 오류는
     * 로그만 남기고 삼킨다 — 등급이 갱신되지 않아도 동기화 자체는 성공이다.
     *
     * @return 기록한 등급명. 조회에 실패했으면 empty.
     */
    public Optional<String> refresh(Long userId, String userKey) {
        SsafyCreditRating rating;
        try {
            rating = SsafyCalls.call("credit rating refresh", () -> ssafyFinanceClient.inquireMyCreditRating(userKey));
        } catch (BusinessException exception) {
            log.warn("Credit rating refresh skipped for user {}: {}", userId, exception.getMessage());
            return Optional.empty();
        }
        if (rating == null || rating.ratingName() == null || rating.ratingName().isBlank()) {
            log.warn("Credit rating refresh for user {} returned no rating name", userId);
            return Optional.empty();
        }
        record(userId, rating, LocalDateTime.now());
        return Optional.of(rating.ratingName().trim());
    }

    /**
     * 신용등급 기준표는 앱 단위 API라 userKey가 필요 없다. 금융망 연결 여부와 무관하게 로그인 사용자에게 제공한다.
     */
    public CreditRatingCriteriaResponse getCreditRatingCriteria(Long userId) {
        requireActiveUser(userId);
        List<SsafyCreditRatingCriterion> criteria = SsafyCalls.call(
                "credit rating criteria inquiry", ssafyFinanceClient::inquireAssetBasedCreditRatingList
        );
        return CreditRatingCriteriaResponse.of(criteria, LocalDateTime.now());
    }

    private void record(Long userId, SsafyCreditRating rating, LocalDateTime at) {
        transactionTemplate.executeWithoutResult(status -> userRepository.findByIdAndDeletedAtIsNull(userId)
                .ifPresent(user -> user.updateCreditRating(rating.ratingName(), at)));
    }

    private String userKeyOf(Long userId) {
        requireActiveUser(userId);
        FinanceConnection connection = financeConnectionService.requireConnected(userId);
        return financeConnectionService.userKeyOf(connection);
    }

    private void requireActiveUser(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }
}
