package com.ssafy.dodam.domain.card.controller;

import com.ssafy.dodam.domain.card.dto.response.CardDetailResponse;
import com.ssafy.dodam.domain.card.dto.response.CardResponse;
import com.ssafy.dodam.domain.card.dto.response.CardSummaryResponse;
import com.ssafy.dodam.domain.card.dto.response.CardTransactionResponse;
import com.ssafy.dodam.domain.card.service.CardQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
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
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public CardQueryController(
            CardQueryService cardQueryService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.cardQueryService = cardQueryService;
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
}
