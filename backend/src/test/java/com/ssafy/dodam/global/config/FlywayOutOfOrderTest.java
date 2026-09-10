package com.ssafy.dodam.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;

/**
 * 마이그레이션 번호를 담당 영역별 구간(V1~V9 BE-A, V10~ BE-B)으로 나눠 쓰면, 이미 V13이 적용된 DB에
 * 나중에 V4가 추가되는 상황이 정상적으로 일어난다. Flyway 기본값은 이를 검증 오류로 막으므로
 * local/prod 프로필에 `spring.flyway.out-of-order: true`를 둔다. 이 테스트는 그 두 동작을 H2로 재현한다.
 *
 * MR 리뷰(정현진, 2026-09-09): "V13까지 적용된 MySQL에 V4를 추가하면 FlywayValidateException이 발생".
 */
class FlywayOutOfOrderTest {

    private static final String APPLIED = "classpath:db/outoforder/applied";
    private static final String LATE = "classpath:db/outoforder/late";

    @Test
    void lowerVersionAddedAfterHigherOnesFailsValidationByDefault() {
        String url = freshDatabase();
        migrate(url, false, APPLIED);

        Flyway later = flyway(url, false, APPLIED, LATE);

        // Spring Boot 기동은 migrate()를 호출한다. 기본값에서는 낮은 버전의 pending 마이그레이션을 거절한다.
        assertThatThrownBy(later::migrate)
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("not applied to database: 2");
    }

    @Test
    void outOfOrderAppliesLowerVersionAddedLater() {
        String url = freshDatabase();
        migrate(url, false, APPLIED);

        Flyway later = flyway(url, true, APPLIED, LATE);
        // 적용 전 단독 validate()는 outOfOrder와 무관하게 pending을 오류로 본다. 기동 경로인 migrate()가 적용해야 한다.
        assertThat(later.migrate().migrationsExecuted).isEqualTo(1);

        assertThat(later.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "3", "2");
        assertThat(later.info().all())
                .filteredOn(info -> info.getVersion().toString().equals("2"))
                .singleElement()
                .extracting(info -> info.getState())
                .isEqualTo(MigrationState.OUT_OF_ORDER);
        // 다시 돌려도 검증·적용에 문제가 없어야 한다 (재기동 시나리오)
        later.validate();
        assertThat(later.migrate().migrationsExecuted).isZero();
    }

    private static void migrate(String url, boolean outOfOrder, String... locations) {
        flyway(url, outOfOrder, locations).migrate();
    }

    private static Flyway flyway(String url, boolean outOfOrder, String... locations) {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(locations)
                .outOfOrder(outOfOrder)
                .load();
    }

    private static String freshDatabase() {
        return "jdbc:h2:mem:flyway-ooo-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }
}
