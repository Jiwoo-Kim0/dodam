package com.ssafy.dodam.domain.transaction.dto.response;

import com.ssafy.dodam.domain.transaction.entity.Transaction;
import com.ssafy.dodam.domain.transaction.entity.TransactionCategory;
import com.ssafy.dodam.domain.transaction.repository.TransactionRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 거래 한 건. 목록·상세·수정 응답이 같은 형태를 쓴다.
 *
 * <p>카드(8)·마이너스통장(10-1) 거래내역 응답과 공통 필드 이름을 맞췄다
 * (transactionId, transactionDate, transactionTime, transactionType, amount, merchantName, paymentMethod,
 * categoryCode, categoryName, summary, memo). 계좌·카드 번호는 각 도메인 API에서 조회하며 여기서는 ID만 준다.
 *
 * @param amount       항상 양수. 입출금 방향은 transactionType이 정한다.
 * @param balanceAfter 거래 후 잔액. 카드 거래·수동 거래는 null.
 * @param hidden       숨김 여부. 목록 기본 조회에서는 숨긴 거래가 빠진다.
 */
public record TransactionResponse(
        String transactionId,
        Long accountId,
        Long cardId,
        Long categoryId,
        String categoryCode,
        String categoryName,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String transactionType,
        long amount,
        Long balanceAfter,
        String merchantName,
        String paymentMethod,
        String spendingType,
        String summary,
        String memo,
        boolean hidden,
        String sourceType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TransactionResponse from(TransactionRow row) {
        return new TransactionResponse(
                row.transactionId(),
                row.accountId(),
                row.cardId(),
                row.categoryId(),
                row.categoryCode(),
                row.categoryName(),
                row.transactionDate(),
                row.transactionTime(),
                row.transactionType(),
                row.amount(),
                row.balanceAfter(),
                row.merchantName(),
                row.paymentMethod(),
                row.spendingType(),
                row.summary(),
                row.memo(),
                row.hidden(),
                row.sourceType(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    /**
     * 수정 직후 응답. 같은 트랜잭션 안에서 JDBC로 다시 읽으면 flush 전 값이 보일 수 있어 엔티티에서 직접 만든다.
     *
     * @param category 거래에 연결된 카테고리. 없으면 null.
     */
    public static TransactionResponse from(Transaction transaction, TransactionCategory category) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getCardId(),
                transaction.getCategoryId(),
                category == null ? null : category.getCode(),
                category == null ? null : category.getName(),
                transaction.getTransactionDate(),
                transaction.getTransactionTime(),
                transaction.getTransactionType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getMerchantName(),
                transaction.getPaymentMethod(),
                transaction.getSpendingType() == null ? null : transaction.getSpendingType().name(),
                transaction.getSummary(),
                transaction.getMemo(),
                transaction.isHidden(),
                transaction.getSourceType().name(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
