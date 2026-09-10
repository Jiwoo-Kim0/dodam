package com.ssafy.dodam.domain.minusaccount.service;

import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountAlertResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountDetailResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountInterestResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountTransactionResponse;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountAlertReader;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountOwnerRepository;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountRepository;
import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountTransactionReader;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class MinusAccountQueryService {

    /**
     * `minus_account`에는 상태 컬럼이 없고 ERD가 "MinusAccount·DepositSaving도 Account 상태를
     * 사용"으로 정하고 있어 명세의 status는 `account.status`로 해석한다.
     */
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "INACTIVE", "CLOSED");

    private final MinusAccountOwnerRepository ownerRepository;
    private final MinusAccountRepository minusAccountRepository;
    private final MinusAccountTransactionReader transactionReader;
    private final MinusAccountAlertReader alertReader;
    private final MinusAccountInterestCalculator interestCalculator;
    private final MinusAccountClock clock;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final QueryPeriodPolicy queryPeriodPolicy;

    public MinusAccountQueryService(
            MinusAccountOwnerRepository ownerRepository,
            MinusAccountRepository minusAccountRepository,
            MinusAccountTransactionReader transactionReader,
            MinusAccountAlertReader alertReader,
            MinusAccountInterestCalculator interestCalculator,
            MinusAccountClock clock,
            SensitiveNumberHasher sensitiveNumberHasher,
            QueryPeriodPolicy queryPeriodPolicy
    ) {
        this.ownerRepository = ownerRepository;
        this.minusAccountRepository = minusAccountRepository;
        this.transactionReader = transactionReader;
        this.alertReader = alertReader;
        this.interestCalculator = interestCalculator;
        this.clock = clock;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.queryPeriodPolicy = queryPeriodPolicy;
    }

    public List<MinusAccountResponse> getMinusAccounts(Long userId, String status) {
        List<MinusAccountOwner> owners = findOwners(userId, status);
        if (owners.isEmpty()) {
            return List.of();
        }

        Map<Long, MinusAccount> details = findDetails(owners);

        // 사용액이 큰 계좌를 먼저 보여준다. 보유 건수가 사용자당 소수라 메모리 정렬로 충분하다.
        return owners.stream()
                .filter(owner -> details.containsKey(owner.getId()))
                .map(owner -> MinusAccountResponse.of(owner, details.get(owner.getId())))
                .sorted(Comparator
                        .comparing(MinusAccountResponse::usedAmount, Comparator.reverseOrder())
                        .thenComparing(MinusAccountResponse::accountId))
                .toList();
    }

    public MinusAccountDetailResponse getMinusAccount(Long userId, String accountNo) {
        MinusAccountOwner owner = findOwner(userId, accountNo);
        return MinusAccountDetailResponse.of(owner, findDetail(owner));
    }

    public MinusAccountInterestResponse getInterest(Long userId, String accountNo) {
        MinusAccountOwner owner = findOwner(userId, accountNo);
        return interestCalculator.calculate(owner, findDetail(owner), clock.today());
    }

    public PageResponse<MinusAccountTransactionResponse> getTransactions(
            Long userId,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        MinusAccountOwner owner = findOwner(userId, accountNo);
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate);
        Pageable pageable = PageRequest.of(page, size);

        return PageResponse.from(
                transactionReader.read(userId, owner.getId(), period, pageable),
                MinusAccountTransactionResponse::from
        );
    }

    public PageResponse<MinusAccountAlertResponse> getAlerts(
            Long userId,
            String accountNo,
            boolean unreadOnly,
            int page,
            int size
    ) {
        MinusAccountOwner owner = findOwner(userId, accountNo);
        Pageable pageable = PageRequest.of(page, size);

        return PageResponse.from(
                alertReader.read(userId, owner.getId(), unreadOnly, pageable),
                MinusAccountAlertResponse::from
        );
    }

    /**
     * 경로 변수 계좌번호로 본인의 마이너스통장을 찾는다.
     *
     * <p>타인 계좌, 존재하지 않는 계좌, MINUS가 아닌 계좌를 모두 같은 예외로 처리해 리소스 존재
     * 여부를 노출하지 않는다.
     */
    MinusAccountOwner findOwner(Long userId, String accountNo) {
        String accountNoHash = sensitiveNumberHasher.hash(accountNo);
        return ownerRepository
                .findByUserIdAndAccountNoHashAndAccountType(userId, accountNoHash, MinusAccountOwner.MINUS)
                .orElseThrow(() -> new ResourceNotFoundException("마이너스통장을 찾을 수 없습니다."));
    }

    /**
     * 상위 `account` 행은 있는데 상세행이 없으면 데이터 정합이 깨진 상태다. 계좌를 노출하지 않고
     * 없는 리소스로 처리한다.
     */
    MinusAccount findDetail(MinusAccountOwner owner) {
        return minusAccountRepository.findById(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("마이너스통장을 찾을 수 없습니다."));
    }

    private List<MinusAccountOwner> findOwners(Long userId, String status) {
        if (!StringUtils.hasText(status)) {
            return ownerRepository.findByUserIdAndAccountType(userId, MinusAccountOwner.MINUS);
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new InvalidRequestException("지원하지 않는 계좌 상태입니다.");
        }
        return ownerRepository.findByUserIdAndAccountTypeAndStatus(
                userId,
                MinusAccountOwner.MINUS,
                normalized
        );
    }

    private Map<Long, MinusAccount> findDetails(List<MinusAccountOwner> owners) {
        List<Long> accountIds = owners.stream().map(MinusAccountOwner::getId).toList();
        return minusAccountRepository.findByAccountIdIn(accountIds).stream()
                .collect(Collectors.toMap(MinusAccount::getAccountId, Function.identity()));
    }
}
