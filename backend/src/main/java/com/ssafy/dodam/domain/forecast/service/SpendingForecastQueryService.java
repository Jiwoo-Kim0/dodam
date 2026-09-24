package com.ssafy.dodam.domain.forecast.service;

import com.ssafy.dodam.domain.forecast.dto.response.SpendingForecastResponse;
import com.ssafy.dodam.domain.forecast.repository.JdbcSpendingForecastReader;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.MonthlyPoint;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 변동 소비 예측 조회(S15P21D105-706).
 *
 * <p><b>조회할 때 계산하고 저장하지 않는다.</b> 결과 테이블·요청 해시·사용자 락·진행 중 만료는 전부 "결과를 저장하고
 * 재사용"하기 위한 장치인데, 한 사용자가 한 번 조회하는 데는 필요 없다. 저장이 필요해지면 그때 별도 이슈로 붙인다.
 *
 * <p>추정 입력은 <b>완료된 달 24개</b>다. 오늘이 속한 달은 아직 끝나지 않아 합계가 실제보다 작으므로 넣지 않는다. 넣으면
 * 월초에 조회할수록 수준이 내려가 예측이 매일 달라진다.
 */
@Service
public class SpendingForecastQueryService {

    /** `horizonMonths`를 주지 않았을 때 */
    static final int DEFAULT_HORIZON_MONTHS = 3;
    /** 감쇠 추세가 D(h) = φ/(1−φ)에 사실상 수렴하는 구간. 더 멀리 봐도 값이 거의 변하지 않는다 */
    static final int MAX_HORIZON_MONTHS = 12;
    /** 추정 입력 창. 계절 계수를 만들려면 달력 월마다 2회는 관측돼야 한다 */
    static final int HISTORY_MONTHS = 24;
    /** 화면에 그릴 과거 구간. 24개월을 전부 그리면 폰 화면에서 뭉갠다 */
    static final int CHART_MONTHS = 12;

    private final JdbcSpendingForecastReader reader;
    private final SpendingForecastClock clock;

    public SpendingForecastQueryService(JdbcSpendingForecastReader reader, SpendingForecastClock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpendingForecastResponse forecast(Long userId, Integer horizonMonths) {
        int horizon = resolveHorizon(horizonMonths);
        YearMonth base = clock.currentMonth();
        List<MonthlyPoint> history =
                reader.readMonthlyVariableSpending(userId, base.minusMonths(HISTORY_MONTHS), base.minusMonths(1));

        SpendingForecastCalculator.Result result = SpendingForecastCalculator.forecast(history, base, horizon);
        return SpendingForecastResponse.of(base, horizon, result, chartWindow(history, base));
    }

    static int resolveHorizon(Integer requested) {
        if (requested == null) {
            return DEFAULT_HORIZON_MONTHS;
        }
        if (requested < 1 || requested > MAX_HORIZON_MONTHS) {
            throw new InvalidRequestException("horizonMonths는 1~" + MAX_HORIZON_MONTHS + " 사이여야 합니다.");
        }
        return requested;
    }

    /** 추정에는 24개월을 쓰되 화면에는 최근 12개월만 돌려준다 */
    private static List<MonthlyPoint> chartWindow(List<MonthlyPoint> history, YearMonth base) {
        YearMonth from = base.minusMonths(CHART_MONTHS);
        return history.stream().filter(point -> !point.month().isBefore(from)).toList();
    }
}
