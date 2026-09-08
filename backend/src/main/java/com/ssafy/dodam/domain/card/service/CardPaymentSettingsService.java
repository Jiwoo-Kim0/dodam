package com.ssafy.dodam.domain.card.service;

import com.ssafy.dodam.domain.card.dto.request.UpdateCardPaymentSettingsRequest;
import com.ssafy.dodam.domain.card.dto.response.CardPaymentSettingsResponse;
import com.ssafy.dodam.domain.card.dto.response.CardWithdrawalAccountResponse;
import com.ssafy.dodam.domain.card.entity.Card;
import com.ssafy.dodam.domain.card.entity.CardStatus;
import com.ssafy.dodam.domain.card.repository.CardPaymentAccountReader;
import com.ssafy.dodam.domain.card.repository.CardPaymentAccountRow;
import com.ssafy.dodam.domain.card.repository.CardRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardPaymentSettingsService {

    private final CardRepository cardRepository;
    private final CardPaymentAccountReader paymentAccountReader;
    private final SensitiveNumberHasher sensitiveNumberHasher;

    public CardPaymentSettingsService(
            CardRepository cardRepository,
            CardPaymentAccountReader paymentAccountReader,
            SensitiveNumberHasher sensitiveNumberHasher
    ) {
        this.cardRepository = cardRepository;
        this.paymentAccountReader = paymentAccountReader;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
    }

    @Transactional
    public CardPaymentSettingsResponse update(
            Long userId,
            String cardNo,
            UpdateCardPaymentSettingsRequest request
    ) {
        if (request.hasNoFields()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        Card card = findCard(userId, cardNo);
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new InvalidRequestException("활성 카드의 결제 설정만 변경할 수 있습니다.");
        }

        CardPaymentAccountRow requestedAccount = null;
        if (request.withdrawalAccountNo() != null) {
            requestedAccount = paymentAccountReader.findActiveDemandDeposit(
                    userId,
                    sensitiveNumberHasher.hash(request.withdrawalAccountNo())
            ).orElseThrow(() -> new ResourceNotFoundException("출금계좌를 찾을 수 없습니다."));
        }

        Long requestedAccountId = requestedAccount == null
                ? null
                : requestedAccount.accountId();
        card.updatePaymentSettings(requestedAccountId, request.withdrawalDay());

        CardWithdrawalAccountResponse accountResponse = requestedAccount == null
                ? CardWithdrawalAccountResponse.from(card.getWithdrawalAccount())
                : toResponse(requestedAccount);
        return new CardPaymentSettingsResponse(
                card.getCardNoMasked(),
                card.getWithdrawalDay(),
                accountResponse,
                card.getUpdatedAt()
        );
    }

    private Card findCard(Long userId, String cardNo) {
        return cardRepository.findByUserIdAndCardNoHash(
                userId,
                sensitiveNumberHasher.hash(cardNo)
        ).orElseThrow(() -> new ResourceNotFoundException("카드를 찾을 수 없습니다."));
    }

    private static CardWithdrawalAccountResponse toResponse(CardPaymentAccountRow account) {
        return new CardWithdrawalAccountResponse(
                account.bankCode(),
                account.bankName(),
                account.accountName(),
                account.accountNoMasked(),
                account.accountType()
        );
    }
}
