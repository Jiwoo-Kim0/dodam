package com.ssafy.dodam.domain.card.controller;

import com.ssafy.dodam.domain.card.dto.request.UpdateCardPaymentSettingsRequest;
import com.ssafy.dodam.domain.card.dto.response.CardBenefitUsageResponse;
import com.ssafy.dodam.domain.card.dto.response.CardBenefitsResponse;
import com.ssafy.dodam.domain.card.dto.response.CardBillingStatementResponse;
import com.ssafy.dodam.domain.card.dto.response.CardCategorySummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardDetailResponse;
import com.ssafy.dodam.domain.card.dto.response.CardLimitResponse;
import com.ssafy.dodam.domain.card.dto.response.CardMonthlySummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentBurdenResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentScheduleResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentSchedulesResponse;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentSettingsResponse;
import com.ssafy.dodam.domain.card.dto.response.CardResponse;
import com.ssafy.dodam.domain.card.dto.response.CardSummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardTransactionResponse;
import com.ssafy.dodam.domain.card.dto.response.LowUsageCardsResponse;
import com.ssafy.dodam.domain.card.service.CardAnalysisService;
import com.ssafy.dodam.domain.card.service.CardPaymentSettingsService;
import com.ssafy.dodam.domain.card.service.CardQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cards")
public class CardQueryController {

    private static final String CODE_PATTERN = "^\s*$|[A-Za-z0-9_]+$";
    private static final String CARD_NO_PATTERN = "^[0-9]{15,19}$";

    private final CardQueryService cardQueryService;
    private final CardAnalysisService cardAnalysisService;
    private final CardPaymentSettingsService cardPaymentSettingsService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public CardQueryController(
            CardQueryService cardQueryService,
            CardAnalysisService cardAnalysisService,
            CardPaymentSettingsService cardPaymentSettingsService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.cardQueryService = cardQueryService;
        this.cardAnalysisService = cardAnalysisService;
        this.cardPaymentSettingsService = cardPaymentSettingsService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @GetMapping
    public ApiResponse<List<CardResponse>> getCards(
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String issuerCode,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String status,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getCards(userId, issuerCode, status));
    }

    @GetMapping("/summary")
    public ApiResponse<CardSummaryResponse> getSummary(Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getSummary(userId));
    }

    @GetMapping("/{cardNo}")
    public ApiResponse<CardDetailResponse> getCard(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getCard(userId, cardNo));
    }

    @GetMapping("/{cardNo}/transactions")
    public ApiResponse<PageResponse<CardTransactionResponse>> getCardTransactions(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getCardTransactions(
                userId,
                cardNo,
                startDate,
                endDate,
                page,
                size
        ));
    }

    @GetMapping("/{cardNo}/payment-schedule")
    public ApiResponse<CardPaymentScheduleResponse> getPaymentSchedule(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getPaymentSchedule(userId, cardNo));
    }

    @GetMapping("/{cardNo}/billing-statements")
    public ApiResponse<List<CardBillingStatementResponse>> getBillingStatements(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getBillingStatements(
                userId,
                cardNo,
                startMonth,
                endMonth
        ));
    }

    @GetMapping("/{cardNo}/limit")
    public ApiResponse<CardLimitResponse> getLimit(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardQueryService.getLimit(userId, cardNo));
    }

    @GetMapping("/{cardNo}/monthly-summary")
    public ApiResponse<CardMonthlySummaryResponse> getMonthlySummary(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false) String yearMonth,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getMonthlySummary(
                userId,
                cardNo,
                yearMonth
        ));
    }

    @GetMapping("/payment-schedules")
    public ApiResponse<CardPaymentSchedulesResponse> getPaymentSchedules(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getPaymentSchedules(
                userId,
                startDate,
                endDate
        ));
    }

    @GetMapping("/payment-burden")
    public ApiResponse<CardPaymentBurdenResponse> getPaymentBurden(
            @RequestParam(required = false) String yearMonth,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getPaymentBurden(userId, yearMonth));
    }

    @GetMapping("/low-usage")
    public ApiResponse<LowUsageCardsResponse> getLowUsageCards(
            @RequestParam(defaultValue = "3") @Min(1) @Max(12) int lookbackMonths,
            @RequestParam(defaultValue = "100000") @Min(0) long thresholdAmount,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getLowUsageCards(
                userId,
                lookbackMonths,
                thresholdAmount
        ));
    }

    @GetMapping("/{cardNo}/categories")
    public ApiResponse<CardCategorySummaryResponse> getCategories(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false) String yearMonth,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getCategories(userId, cardNo, yearMonth));
    }

    @GetMapping("/{cardNo}/benefits")
    public ApiResponse<CardBenefitsResponse> getBenefits(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false) @Size(max = 50)
            @Pattern(regexp = CODE_PATTERN) String category,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getBenefits(userId, cardNo, category));
    }

    @GetMapping("/{cardNo}/benefit-usage")
    public ApiResponse<CardBenefitUsageResponse> getBenefitUsage(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @RequestParam(required = false) String yearMonth,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardAnalysisService.getBenefitUsage(
                userId,
                cardNo,
                yearMonth
        ));
    }

    @PatchMapping("/{cardNo}/payment-settings")
    public ApiResponse<CardPaymentSettingsResponse> updatePaymentSettings(
            @PathVariable @Pattern(regexp = CARD_NO_PATTERN) String cardNo,
            @Valid @RequestBody UpdateCardPaymentSettingsRequest request,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cardPaymentSettingsService.update(userId, cardNo, request));
    }
}
