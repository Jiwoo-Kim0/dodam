package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.dto.response.TransactionCategoryResponse;
import com.ssafy.dodam.domain.transaction.dto.response.TransactionResponse;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.transaction.repository.TransactionReader;
import com.ssafy.dodam.domain.transaction.repository.TransactionReader.TransactionFilter;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 6 거래 조회(S15P21D105-504). 거래 원장은 우리 DB(`transaction`)만 읽는다.
 * 금융망 거래를 원장으로 옮기는 일은 Finance Data 동기화의 ACCOUNT scope(S15P21D105-501)가 맡는다.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    static final String TRANSACTION_NOT_FOUND_MESSAGE = "거래를 찾을 수 없습니다.";
    static final String CATEGORY_CODE_NOT_FOUND_MESSAGE = "존재하지 않는 카테고리 코드입니다.";

    private final TransactionReader transactionReader;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final QueryPeriodPolicy queryPeriodPolicy;

    public TransactionQueryService(
            TransactionReader transactionReader,
            TransactionCategoryRepository transactionCategoryRepository,
            QueryPeriodPolicy queryPeriodPolicy
    ) {
        this.transactionReader = transactionReader;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.queryPeriodPolicy = queryPeriodPolicy;
    }

    /**
     * @param category      카테고리 코드(대소문자 무시). 없는 코드는 400.
     * @param paymentMethod 결제수단 코드(대소문자 무시). 저장값과 정확히 일치하는 거래만 나온다.
     * @param hidden        null이면 숨기지 않은 거래만(기본), true면 숨긴 거래만, false면 숨기지 않은 거래만.
     */
    public PageResponse<TransactionResponse> getTransactions(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            String category,
            String paymentMethod,
            Boolean hidden,
            int page,
            int size
    ) {
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate);
        TransactionFilter filter = new TransactionFilter(
                period,
                resolveCategoryCode(category),
                normalizeCode(paymentMethod),
                hidden == null ? Boolean.FALSE : hidden
        );

        return PageResponse.from(
                transactionReader.read(userId, filter, PageRequest.of(page, size)),
                TransactionResponse::from
        );
    }

    public TransactionResponse getTransaction(Long userId, String transactionId) {
        return transactionReader.findOne(userId, transactionId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(TRANSACTION_NOT_FOUND_MESSAGE));
    }

    public List<TransactionCategoryResponse> getCategories() {
        return transactionCategoryRepository.findAllByActiveTrueOrderByIdAsc()
                .stream()
                .map(TransactionCategoryResponse::from)
                .toList();
    }

    private String resolveCategoryCode(String category) {
        String code = normalizeCode(category);
        if (code == null) {
            return null;
        }
        return transactionCategoryRepository.findByCode(code)
                .orElseThrow(() -> new InvalidRequestException(CATEGORY_CODE_NOT_FOUND_MESSAGE))
                .getCode();
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
