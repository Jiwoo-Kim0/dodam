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
import org.springframework.stereotype.Service;

/**
 * API 명세 4. Finance Data — 신용등급 조회 (S15P21D105-500).
 * 금융망 실시간 조회이며 결과를 저장하지 않는다. 호출마다 새 기관거래고유번호를 쓴다 (SsafyHeaderFactory).
 */
@Service
public class CreditRatingService {

    private final UserRepository userRepository;
    private final FinanceConnectionService financeConnectionService;
    private final SsafyFinanceClient ssafyFinanceClient;

    public CreditRatingService(
            UserRepository userRepository,
            FinanceConnectionService financeConnectionService,
            SsafyFinanceClient ssafyFinanceClient
    ) {
        this.userRepository = userRepository;
        this.financeConnectionService = financeConnectionService;
        this.ssafyFinanceClient = ssafyFinanceClient;
    }

    public CreditRatingResponse getMyCreditRating(Long userId) {
        String userKey = userKeyOf(userId);
        SsafyCreditRating rating = SsafyCalls.call(
                "credit rating inquiry", () -> ssafyFinanceClient.inquireMyCreditRating(userKey)
        );
        return CreditRatingResponse.of(rating, LocalDateTime.now());
    }

    public CreditRatingCriteriaResponse getCreditRatingCriteria(Long userId) {
        String userKey = userKeyOf(userId);
        List<SsafyCreditRatingCriterion> criteria = SsafyCalls.call(
                "credit rating criteria inquiry", () -> ssafyFinanceClient.inquireCreditRatingList(userKey)
        );
        return CreditRatingCriteriaResponse.of(criteria, LocalDateTime.now());
    }

    private String userKeyOf(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        FinanceConnection connection = financeConnectionService.requireConnected(userId);
        return financeConnectionService.userKeyOf(connection);
    }
}
