package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.dto.response.TransactionResponse;
import com.ssafy.dodam.domain.transaction.entity.Transaction;
import com.ssafy.dodam.domain.transaction.entity.TransactionCategory;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.transaction.repository.TransactionRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 6 거래 메모·카테고리·숨김 수정(S15P21D105-504).
 *
 * <p>세 API 모두 값이 이미 같으면 아무것도 갱신하지 않고 현재 상태를 200으로 돌려준다(중복 요청 방지).
 * 수정은 우리 DB에만 반영한다. 명세는 메모 수정을 "SSAFY" 연동으로 표기하지만 금융망 메모 API 반영은
 * 501(계좌 동기화)에서 거래 원장과 금융망의 대응 규약이 정해진 뒤에 붙인다(remaining-work.md).
 */
@Service
@Transactional
public class TransactionCommandService {

    static final String CATEGORY_NOT_FOUND_MESSAGE = "존재하지 않는 카테고리입니다.";

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final TransactionMemoPolicy transactionMemoPolicy;
    private final TransactionClock transactionClock;

    public TransactionCommandService(
            TransactionRepository transactionRepository,
            TransactionCategoryRepository transactionCategoryRepository,
            TransactionMemoPolicy transactionMemoPolicy,
            TransactionClock transactionClock
    ) {
        this.transactionRepository = transactionRepository;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.transactionMemoPolicy = transactionMemoPolicy;
        this.transactionClock = transactionClock;
    }

    /**
     * 비활성 카테고리나 없는 ID는 400이다. 카테고리에 기본 소비유형이 있으면 거래의 소비유형도 함께 바뀐다.
     */
    public TransactionResponse changeCategory(Long userId, String transactionId, Long categoryId) {
        Transaction transaction = findOwned(userId, transactionId);
        TransactionCategory category = transactionCategoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new InvalidRequestException(CATEGORY_NOT_FOUND_MESSAGE));

        transaction.changeCategory(category, transactionClock.now());
        return TransactionResponse.from(transaction, category);
    }

    public TransactionResponse changeMemo(Long userId, String transactionId, String memo) {
        Transaction transaction = findOwned(userId, transactionId);
        String normalized = transactionMemoPolicy.normalize(memo);

        transaction.changeMemo(normalized, transactionClock.now());
        return toResponse(transaction);
    }

    public TransactionResponse changeVisibility(Long userId, String transactionId, boolean hidden) {
        Transaction transaction = findOwned(userId, transactionId);

        transaction.changeHidden(hidden, transactionClock.now());
        return toResponse(transaction);
    }

    private Transaction findOwned(Long userId, String transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        TransactionQueryService.TRANSACTION_NOT_FOUND_MESSAGE
                ));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        TransactionCategory category = transaction.getCategoryId() == null
                ? null
                : transactionCategoryRepository.findById(transaction.getCategoryId()).orElse(null);
        return TransactionResponse.from(transaction, category);
    }
}
