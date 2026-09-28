package com.ssafy.dodam.domain.loan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanRepaymentScheduleCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 9);

    private final LoanRepaymentScheduleCalculator calculator =
            new LoanRepaymentScheduleCalculator();

    @Test
    void 원금균등은_매회차_원금이_같고_이자가_줄어든다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1200000, "6.0000", RepaymentType.EQUAL_PRINCIPAL,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2027, 9, 10)),
                AS_OF
        );

        List<LoanRepaymentScheduleItem> items = schedule.items();
        assertThat(items).hasSize(12);
        assertThat(items.get(0).principal()).isEqualTo(100000);
        assertThat(items.get(1).principal()).isEqualTo(100000);
        // 월이율 0.5% 기준 첫 회차 이자 6000원, 두 번째 회차는 잔액이 줄어 5500원이다.
        assertThat(items.get(0).interest()).isEqualTo(6000);
        assertThat(items.get(1).interest()).isEqualTo(5500);
        assertThat(items.get(0).amount()).isEqualTo(106000);
        assertThat(schedule.totalPrincipal()).isEqualTo(1200000);
        assertThat(items.get(11).remainingBalance()).isZero();
    }

    @Test
    void 원리금균등은_매회차_상환액이_거의_같고_마지막에_잔액을_모두_상환한다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1200000, "6.0000", RepaymentType.EQUAL_PAYMENT,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2027, 9, 10)),
                AS_OF
        );

        List<LoanRepaymentScheduleItem> items = schedule.items();
        assertThat(items).hasSize(12);
        // A = 1,200,000 x 0.005 x 1.005^12 / (1.005^12 - 1) = 103,279.76원 -> 반올림 103,280원
        assertThat(items.get(0).amount()).isEqualTo(103280);
        assertThat(items.get(0).interest()).isEqualTo(6000);
        assertThat(items.get(0).principal()).isEqualTo(97280);
        assertThat(schedule.totalPrincipal()).isEqualTo(1200000);
        assertThat(items.get(11).remainingBalance()).isZero();
    }

    @Test
    void 만기일시는_이자만_내다가_마지막에_원금을_상환한다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(50000000, "3.6000", RepaymentType.BULLET,
                        LocalDate.of(2026, 10, 25), LocalDate.of(2027, 3, 25)),
                AS_OF
        );

        List<LoanRepaymentScheduleItem> items = schedule.items();
        assertThat(items).hasSize(6);
        assertThat(items.get(0).principal()).isZero();
        assertThat(items.get(0).interest()).isEqualTo(150000);
        assertThat(items.get(0).remainingBalance()).isEqualTo(50000000);
        assertThat(items.get(5).principal()).isEqualTo(50000000);
        assertThat(items.get(5).remainingBalance()).isZero();
    }

    @Test
    void 다음_상환일이_없으면_상환일_기준으로_기준일_이후_첫_회차를_잡는다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1000000, "0.0000", RepaymentType.EQUAL_PRINCIPAL, 5, null,
                        LocalDate.of(2026, 12, 5)),
                AS_OF
        );

        // 기준일 9월 9일은 9월 5일을 지났으므로 첫 회차는 10월 5일이다.
        assertThat(schedule.items().get(0).repaymentDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(schedule.items()).hasSize(3);
    }

    @Test
    void 상환일이_해당_월에_없으면_말일로_보정한다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(3000000, "0.0000", RepaymentType.EQUAL_PRINCIPAL,
                        LocalDate.of(2026, 12, 31), LocalDate.of(2027, 2, 28)),
                AS_OF
        );

        assertThat(schedule.items()).hasSize(3);
        assertThat(schedule.items().get(1).repaymentDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(schedule.items().get(2).repaymentDate()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void 무이자면_이자가_0이고_원금만_상환한다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1000000, "0.0000", RepaymentType.EQUAL_PAYMENT,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1)),
                AS_OF
        );

        assertThat(schedule.totalInterest()).isZero();
        assertThat(schedule.totalPrincipal()).isEqualTo(1000000);
    }

    @Test
    void 만기일이_없으면_회차를_특정할_수_없어_빈_일정이다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1000000, "5.0000", RepaymentType.EQUAL_PRINCIPAL,
                        LocalDate.of(2026, 10, 10), null),
                AS_OF
        );

        assertThat(schedule.items()).isEmpty();
        assertThat(schedule.truncated()).isFalse();
        assertThat(schedule.nextAmount()).isZero();
    }

    @Test
    void 잔액이_0이면_빈_일정이다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(0, "5.0000", RepaymentType.EQUAL_PRINCIPAL,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2027, 9, 10)),
                AS_OF
        );

        assertThat(schedule.items()).isEmpty();
    }

    @Test
    void 만기가_지난_대출은_빈_일정이다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(1000000, "5.0000", RepaymentType.BULLET,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2026, 5, 10)),
                AS_OF
        );

        assertThat(schedule.items()).isEmpty();
    }

    @Test
    void 남은_회차가_상한을_넘으면_잘라내고_표시한다() {
        LoanRepaymentSchedule schedule = calculator.calculate(
                terms(360000000, "3.0000", RepaymentType.BULLET,
                        LocalDate.of(2026, 10, 10), LocalDate.of(2066, 10, 10)),
                AS_OF
        );

        assertThat(schedule.items()).hasSize(LoanRepaymentScheduleCalculator.MAX_SCHEDULE_SIZE);
        assertThat(schedule.truncated()).isTrue();
    }

    private static LoanRepaymentTerms terms(
            long balance,
            String annualRatePercent,
            RepaymentType repaymentType,
            LocalDate nextRepaymentDate,
            LocalDate maturityDate
    ) {
        return terms(balance, annualRatePercent, repaymentType, null, nextRepaymentDate,
                maturityDate);
    }

    @Test
    void 과거_상환일과_만기가_함께_남아있어도_만기경과는_빈_일정이다() {
        assertThat(calculator.calculate(terms(1000000, "5", RepaymentType.BULLET,
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 10)), AS_OF).items()).isEmpty();
    }

    @Test
    void 이월_말일_다음에는_약정_31일을_복원한다() {
        var result = calculator.calculate(terms(1200000, "12", RepaymentType.EQUAL_PRINCIPAL,
                31, LocalDate.of(2027, 2, 28), LocalDate.of(2027, 4, 30)), AS_OF);
        assertThat(result.items()).extracting(LoanRepaymentScheduleItem::repaymentDate)
                .containsExactly(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
        assertThat(result.totalInterest()).isEqualTo(24000);
    }

    @Test
    void 추론한_약정일보다_만기가_빠르면_만기일에_최종상환한다() {
        var result = calculator.calculate(terms(1200000, "12", RepaymentType.BULLET,
                25, null, LocalDate.of(2026, 9, 20)), AS_OF);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().repaymentDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(result.items().getFirst().amount()).isEqualTo(1212000);
    }

    @Test
    void 마지막_회차는_만기일을_넘지_않는다() {
        var result = calculator.calculate(terms(1200000, "12", RepaymentType.EQUAL_PRINCIPAL,
                25, LocalDate.of(2026, 9, 25), LocalDate.of(2026, 11, 20)), AS_OF);
        assertThat(result.items().getLast().repaymentDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(result.totalPrincipal()).isEqualTo(1200000);
    }

    private static LoanRepaymentTerms terms(
            long balance,
            String annualRatePercent,
            RepaymentType repaymentType,
            Integer repaymentDay,
            LocalDate nextRepaymentDate,
            LocalDate maturityDate
    ) {
        return new LoanRepaymentTerms(
                balance,
                new BigDecimal(annualRatePercent),
                repaymentType,
                repaymentDay,
                nextRepaymentDate,
                maturityDate
        );
    }
}
