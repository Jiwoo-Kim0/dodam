package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.dto.request.CreateManualTransactionRequest;
import com.ssafy.dodam.domain.transaction.dto.request.UpdateManualTransactionRequest;
import com.ssafy.dodam.domain.transaction.dto.response.TransactionResponse;
import com.ssafy.dodam.domain.transaction.entity.Transaction;
import com.ssafy.dodam.domain.transaction.entity.TransactionCategory;
import com.ssafy.dodam.domain.transaction.entity.TransactionType;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.transaction.repository.TransactionRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 6 수동 거래 등록·수정·삭제(S15P21D105-505).
 *
 * <p>수동 거래는 계좌·카드에 연결되지 않은 사용자 입력 원장이다(`source_type = MANUAL`, `account_id`·`card_id`·`balance_after` null).
 * 명세 Body에 계좌 지정 필드가 없으므로 계좌 잔액·`last_transaction_date`는 건드리지 않는다.
 * 거래 시각(`transactionTime`)은 선택이며 사용자가 준 값을 그대로 저장한다. 거래일만 "오늘 이후 불가"를 검증하고 시각은 현재 시각과
 * 비교하지 않는다(앱과 서버의 시계 차이로 방금 등록한 거래가 거절되는 일을 막기 위해).
 * 금융망·seed 출처 거래는 수정·삭제할 수 없다(400 {@code TRANSACTION_NOT_MANUAL}). 그 거래의 메모·카테고리·숨김은 504 PATCH가 맡는다.
 */
@Service
@Transactional
public class ManualTransactionService {

    static final String ID_PREFIX = "MN-";
    static final String FUTURE_DATE_MESSAGE = "거래일은 오늘 이후일 수 없습니다.";
    static final String UNKNOWN_TYPE_MESSAGE = "지원하지 않는 거래 유형입니다.";

    private final TransactionRepository transactionRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final TransactionMemoPolicy transactionMemoPolicy;
    private final TransactionClock transactionClock;

    public ManualTransactionService(
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

    public TransactionResponse create(Long userId, CreateManualTransactionRequest request) {
        LocalDateTime now = transactionClock.now();
        LocalDate transactionDate = requireNotFuture(request.transactionDate(), now.toLocalDate());
        TransactionType type = parseType(request.transactionType());
        TransactionCategory category = request.categoryId() == null ? null : requireActiveCategory(request.categoryId());

        Transaction transaction = Transaction.createManual(
                newId(),
                userId,
                transactionDate,
                request.transactionTime(),
                type,
                request.amount(),
                normalizeText(request.merchantName()),
                category,
                normalizeCode(request.paymentMethod()),
                transactionMemoPolicy.normalize(request.memo()),
                now
        );
        transactionRepository.save(transaction);
        return TransactionResponse.from(transaction, category);
    }

    /**
     * 입력된 필드만 바꾼다. 모든 필드가 비어 있으면 400 {@code NO_FIELDS_TO_UPDATE}.
     * 값이 전부 같으면 아무것도 갱신하지 않고 현재 상태를 돌려준다(중복 요청 방지).
     */
    public TransactionResponse update(Long userId, String transactionId, UpdateManualTransactionRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        Transaction transaction = findOwnedManual(userId, transactionId);
        LocalDateTime now = transactionClock.now();

        LocalDate transactionDate = request.transactionDate() == null
                ? null
                : requireNotFuture(request.transactionDate(), now.toLocalDate());
        TransactionType type = request.transactionType() == null ? null : parseType(request.transactionType());
        transaction.updateManualDetails(transactionDate, request.transactionTime(), type, request.amount(), now);

        if (request.merchantName() != null) {
            transaction.changeMerchantName(normalizeText(request.merchantName()), now);
        }
        if (request.paymentMethod() != null) {
            transaction.changePaymentMethod(normalizeCode(request.paymentMethod()), now);
        }
        if (request.memo() != null) {
            transaction.changeMemo(transactionMemoPolicy.normalize(request.memo()), now);
        }
        TransactionCategory category = null;
        if (request.categoryId() != null) {
            category = requireActiveCategory(request.categoryId());
            transaction.changeCategory(category, now);
        } else if (transaction.getCategoryId() != null) {
            category = transactionCategoryRepository.findById(transaction.getCategoryId()).orElse(null);
        }
        return TransactionResponse.from(transaction, category);
    }

    public void delete(Long userId, String transactionId) {
        transactionRepository.delete(findOwnedManual(userId, transactionId));
    }

    private Transaction findOwnedManual(Long userId, String transactionId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(TransactionQueryService.TRANSACTION_NOT_FOUND_MESSAGE));
        if (!transaction.isManual()) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_MANUAL);
        }
        return transaction;
    }

    private TransactionCategory requireActiveCategory(Long categoryId) {
        return transactionCategoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new InvalidRequestException(TransactionCommandService.CATEGORY_NOT_FOUND_MESSAGE));
    }

    private static LocalDate requireNotFuture(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new InvalidRequestException(FUTURE_DATE_MESSAGE);
        }
        return date;
    }

    private static TransactionType parseType(String value) {
        try {
            return TransactionType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(UNKNOWN_TYPE_MESSAGE);
        }
    }

    /**
     * 수동 거래 ID. 금융망 `transactionUniqueNo`(숫자)·seed(`TX-…`)와 겹치지 않도록 접두어를 붙인다. 총 39자.
     */
    private static String newId() {
        return ID_PREFIX + UUID.randomUUID();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String normalizeCode(String value) {
        String text = normalizeText(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }
}
