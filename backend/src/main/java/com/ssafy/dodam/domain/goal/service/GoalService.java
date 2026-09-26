package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.goal.dto.request.CreateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.request.UpdateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.response.GoalResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalSummaryResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalHistoryEvent;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 12 Goal CRUD·요약(S15P21D105-508).
 *
 * <p>`goal`(공통) + `saving_goal` / `spending_goal`(유형별) 세 엔티티를 같은 `goal_id`로 묶어 다룬다.
 * 모으기 목표의 `currentAmount`는 여기서 바꾸지 않는다(연결 계좌 잔액 합, 509). 삭제는 `deleted_at` soft delete다.
 * 사용자당 목표는 수십 건을 넘지 않는다는 전제로 목록 필터·정렬·페이지는 메모리에서 한다.
 */
@Service
@Transactional
public class GoalService {

    static final String GOAL_NOT_FOUND_MESSAGE = "목표를 찾을 수 없습니다.";
    static final String SAVING_FIELDS_REQUIRED_MESSAGE = "모으기 목표는 목표 금액과 목표일이 필수입니다.";
    static final String SAVING_NO_BUDGET_MESSAGE = "모으기 목표에는 월 예산을 지정할 수 없습니다.";
    static final String SPENDING_BUDGET_REQUIRED_MESSAGE = "줄이기 목표는 월 예산이 필수입니다.";
    static final String SPENDING_NO_TARGET_MESSAGE = "줄이기 목표에는 목표 금액·목표일을 지정할 수 없습니다.";
    static final String TARGET_DATE_NOT_FUTURE_MESSAGE = "목표일은 오늘 이후여야 합니다.";
    static final String NAME_BLANK_MESSAGE = "목표 이름은 비어 있을 수 없습니다.";

    /** 종료되지 않은 상태. 줄이기 목표 1개 제한은 이 상태의 목표만 센다. */
    private static final Set<GoalStatus> OPEN_STATUSES = EnumSet.of(GoalStatus.ACTIVE, GoalStatus.PAUSED);

    /** 진행 중인 목표가 먼저, 같은 상태 안에서는 최근 생성 순. */
    private static final Comparator<Goal> LIST_ORDER = Comparator
            .comparing(Goal::getStatus)
            .thenComparing(Goal::getCreatedAt, Comparator.reverseOrder())
            .thenComparing(Goal::getId, Comparator.reverseOrder());

    private final GoalRepository goalRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final SpendingGoalRepository spendingGoalRepository;
    private final GoalHistoryRecorder goalHistoryRecorder;
    private final GoalClock goalClock;
    private final SafeboxAccountRepository safeboxAccountRepository;
    private final GoalAccountRepository goalAccountRepository;

    public GoalService(
            GoalRepository goalRepository,
            SavingGoalRepository savingGoalRepository,
            SpendingGoalRepository spendingGoalRepository,
            GoalHistoryRecorder goalHistoryRecorder,
            GoalClock goalClock,
            SafeboxAccountRepository safeboxAccountRepository,
            GoalAccountRepository goalAccountRepository
    ) {
        this.goalRepository = goalRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.spendingGoalRepository = spendingGoalRepository;
        this.goalHistoryRecorder = goalHistoryRecorder;
        this.goalClock = goalClock;
        this.safeboxAccountRepository = safeboxAccountRepository;
        this.goalAccountRepository = goalAccountRepository;
    }

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public PageResponse<GoalResponse> getGoals(Long userId, String status, String type, int page, int size) {
        GoalStatus statusFilter = GoalStatus.parseFilter(status);
        GoalType typeFilter = GoalType.parseFilter(type);
        LocalDate today = goalClock.today();

        List<Goal> goals = goalRepository.findAllByUserIdAndDeletedAtIsNull(userId).stream()
                .filter(goal -> statusFilter == null || goal.getStatus() == statusFilter)
                .filter(goal -> typeFilter == null || goal.getType() == typeFilter)
                .sorted(LIST_ORDER)
                .toList();

        Pageable pageable = PageRequest.of(page, size);
        int from = (int) Math.min(pageable.getOffset(), goals.size());
        int to = Math.min(from + size, goals.size());
        List<Goal> pageContent = goals.subList(from, to);
        Details details = loadDetails(pageContent);

        return PageResponse.from(
                new PageImpl<>(pageContent, pageable, goals.size()),
                goal -> toResponse(goal, details, today)
        );
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoal(Long userId, Long goalId) {
        Goal goal = findOwned(userId, goalId);
        return toResponse(goal, loadDetails(List.of(goal)), goalClock.today());
    }

    @Transactional(readOnly = true)
    public GoalSummaryResponse getSummary(Long userId) {
        List<Goal> goals = goalRepository.findAllByUserIdAndDeletedAtIsNull(userId);
        Details details = loadDetails(goals);

        long activeSavingTarget = 0;
        long activeSavingCurrent = 0;
        long activeBudget = 0;
        for (Goal goal : goals) {
            if (goal.getStatus() != GoalStatus.ACTIVE) {
                continue;
            }
            if (goal.getType() == GoalType.SAVING) {
                SavingGoal saving = details.saving().get(goal.getId());
                activeSavingTarget += saving == null ? 0 : saving.getTargetAmount();
                activeSavingCurrent += goal.getCurrentAmount();
            } else {
                SpendingGoal spending = details.spending().get(goal.getId());
                activeBudget += spending == null ? 0 : spending.getMonthlyBudget();
            }
        }
        return new GoalSummaryResponse(
                goals.size(),
                countByStatus(goals, GoalStatus.ACTIVE),
                countByStatus(goals, GoalStatus.PAUSED),
                countByStatus(goals, GoalStatus.ACHIEVED),
                countByStatus(goals, GoalStatus.CANCELED),
                goals.stream().filter(goal -> goal.getType() == GoalType.SAVING).count(),
                activeSavingTarget,
                activeSavingCurrent,
                percent(activeSavingCurrent, activeSavingTarget),
                goals.stream().filter(goal -> goal.getType() == GoalType.SPENDING).count(),
                activeBudget
        );
    }

    // ---------- 생성 ----------

    public GoalResponse create(Long userId, CreateGoalRequest request) {
        GoalType type = GoalType.parse(request.type());
        LocalDateTime now = goalClock.now();
        String name = normalizeName(request.name());

        SavingGoal savingGoal = null;
        SpendingGoal spendingGoal = null;
        if (type == GoalType.SAVING) {
            if (request.targetAmount() == null || request.targetDate() == null) {
                throw new InvalidRequestException(SAVING_FIELDS_REQUIRED_MESSAGE);
            }
            if (request.monthlyBudget() != null) {
                throw new InvalidRequestException(SAVING_NO_BUDGET_MESSAGE);
            }
            requireFuture(request.targetDate(), now.toLocalDate());
        } else {
            if (request.monthlyBudget() == null) {
                throw new InvalidRequestException(SPENDING_BUDGET_REQUIRED_MESSAGE);
            }
            if (request.targetAmount() != null || request.targetDate() != null) {
                throw new InvalidRequestException(SPENDING_NO_TARGET_MESSAGE);
            }
            requireNoOpenSpendingGoal(userId);
        }

        Goal goal = goalRepository.save(Goal.create(userId, name, type, now));
        if (type == GoalType.SAVING) {
            // goal_account가 saving_goal을 외래키로 참조하므로 세이프박스 칸보다 먼저 확정한다.
            savingGoal = savingGoalRepository.saveAndFlush(
                    SavingGoal.create(goal.getId(), request.targetAmount(), request.targetDate()));
            attachSafebox(userId, goal.getId());
        } else {
            spendingGoal = spendingGoalRepository.save(SpendingGoal.create(goal.getId(), request.monthlyBudget()));
        }
        goalHistoryRecorder.record(goal, savingGoal, GoalHistoryEvent.GOAL_CREATED, now);
        return GoalResponse.from(goal, savingGoal, spendingGoal, now.toLocalDate());
    }

    /**
     * 도담 전용 계좌가 있으면 새 모으기 목표에 0원 세이프박스 칸(`goal_account.contribution_amount = 0`)을 만든다(705).
     * 금액이 0이라 확보액·이동 원장(`safebox_movement`)은 바뀌지 않는다. 전용 계좌가 없는 사용자는 칸 없이 만들고,
     * 이후 첫 담기(`PUT /api/v1/safebox/goals/{goalId}`)가 칸을 만든다. 금융망 호출은 하지 않는다.
     */
    private void attachSafebox(Long userId, Long goalId) {
        safeboxAccountRepository.findById(userId).ifPresent(ref ->
                goalAccountRepository.save(GoalAccount.allocate(userId, goalId, ref.getAccountId(), 0L)));
    }

    /**
     * 줄이기 목표는 사용자당 하나만 진행한다. 월 예산이 한 사람의 "이번 달 소비 한도"라 여러 개가 있으면 진행률·요약(월 예산 합)·
     * 진단의 줄이기 목표 초과 판정이 어느 목표를 기준으로 해야 하는지 정할 수 없다. 종료(ACHIEVED·CANCELED)되거나 삭제된 목표는
     * 세지 않으므로, 기존 목표를 끝낸 뒤에는 새로 만들 수 있다. PAUSED는 아직 진행 중인 목표로 본다.
     */
    private void requireNoOpenSpendingGoal(Long userId) {
        if (goalRepository.existsByUserIdAndTypeAndStatusInAndDeletedAtIsNull(
                userId, GoalType.SPENDING, OPEN_STATUSES)) {
            throw new BusinessException(ErrorCode.SPENDING_GOAL_ALREADY_EXISTS);
        }
    }

    // ---------- 수정 ----------

    /**
     * 입력된 필드만 바꾼다. 종료 상태(ACHIEVED·CANCELED) 목표는 어떤 필드도 바꿀 수 없고, 상태 전이는 허용된 경로만 가능하다.
     * 값이 전부 같으면 `updated_at`을 건드리지 않고 현재 상태를 돌려준다(중복 요청 방지).
     */
    public GoalResponse update(Long userId, Long goalId, UpdateGoalRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        Goal goal = findOwned(userId, goalId);
        if (goal.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.GOAL_NOT_EDITABLE);
        }
        LocalDateTime now = goalClock.now();
        GoalStatus nextStatus = request.status() == null ? null : GoalStatus.parse(request.status());
        if (nextStatus != null && nextStatus != goal.getStatus() && !goal.getStatus().canTransitionTo(nextStatus)) {
            throw new BusinessException(ErrorCode.GOAL_STATUS_TRANSITION_NOT_ALLOWED);
        }

        Details details = loadDetails(List.of(goal));
        SavingGoal savingGoal = details.saving().get(goal.getId());
        SpendingGoal spendingGoal = details.spending().get(goal.getId());

        // 유형에 맞지 않는 필드를 먼저 걸러 부분 반영이 없게 한다
        if (goal.getType() == GoalType.SAVING) {
            if (request.monthlyBudget() != null) {
                throw new InvalidRequestException(SAVING_NO_BUDGET_MESSAGE);
            }
            if (request.targetDate() != null) {
                requireFuture(request.targetDate(), now.toLocalDate());
            }
        } else if (request.targetAmount() != null || request.targetDate() != null) {
            throw new InvalidRequestException(SPENDING_NO_TARGET_MESSAGE);
        }

        boolean changed = false;
        if (request.name() != null) {
            changed |= goal.rename(normalizeName(request.name()), now);
        }
        if (savingGoal != null && savingGoal.update(request.targetAmount(), request.targetDate())) {
            changed = true;
        }
        if (spendingGoal != null && spendingGoal.update(request.monthlyBudget())) {
            changed = true;
        }
        boolean statusChanged = nextStatus != null && goal.changeStatus(nextStatus, now);
        if (changed || statusChanged) {
            goal.touch(now);
            goalHistoryRecorder.record(goal, savingGoal,
                    statusChanged ? GoalHistoryEvent.STATUS_CHANGED : GoalHistoryEvent.GOAL_UPDATED, now);
        }
        return GoalResponse.from(goal, savingGoal, spendingGoal, now.toLocalDate());
    }

    // ---------- 삭제 ----------

    public void delete(Long userId, Long goalId) {
        Goal goal = findOwned(userId, goalId);
        LocalDateTime now = goalClock.now();
        goal.delete(now);
        goalHistoryRecorder.record(goal, savingGoalRepository.findById(goal.getId()).orElse(null),
                GoalHistoryEvent.GOAL_DELETED, now);
    }

    // ---------- helpers ----------

    private Goal findOwned(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GOAL_NOT_FOUND_MESSAGE));
    }

    private Details loadDetails(List<Goal> goals) {
        List<Long> ids = goals.stream().map(Goal::getId).toList();
        if (ids.isEmpty()) {
            return new Details(Map.of(), Map.of());
        }
        return new Details(
                savingGoalRepository.findAllByGoalIdIn(ids).stream()
                        .collect(Collectors.toMap(SavingGoal::getGoalId, Function.identity())),
                spendingGoalRepository.findAllByGoalIdIn(ids).stream()
                        .collect(Collectors.toMap(SpendingGoal::getGoalId, Function.identity()))
        );
    }

    private static GoalResponse toResponse(Goal goal, Details details, LocalDate today) {
        return GoalResponse.from(goal, details.saving().get(goal.getId()), details.spending().get(goal.getId()), today);
    }

    private static void requireFuture(LocalDate targetDate, LocalDate today) {
        if (!targetDate.isAfter(today)) {
            throw new InvalidRequestException(TARGET_DATE_NOT_FUTURE_MESSAGE);
        }
    }

    private static String normalizeName(String name) {
        String stripped = name == null ? "" : name.strip();
        if (stripped.isEmpty()) {
            throw new InvalidRequestException(NAME_BLANK_MESSAGE);
        }
        return stripped;
    }

    private static long countByStatus(List<Goal> goals, GoalStatus status) {
        return goals.stream().filter(goal -> goal.getStatus() == status).count();
    }

    private static double percent(long amount, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.min(100.0, BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue());
    }

    private record Details(Map<Long, SavingGoal> saving, Map<Long, SpendingGoal> spending) {
    }
}
