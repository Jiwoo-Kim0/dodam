package com.ssafy.dodam.domain.minusaccount.repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * `notification`에서 이 마이너스통장에 속한 알림만 골라 읽는다.
 *
 * <p>연결 규약은 `source_type = 'MINUS_ACCOUNT'`이고 `source_id`에 `account_id`를 문자열로
 * 넣는 것이다. `notification.source_id`가 VARCHAR(100)이라 문자열로 비교한다.
 * <b>이 규약은 458과 아직 합의되지 않았다.</b> 합의 전까지 실제 데이터가 없어 항상 빈 페이지를
 * 반환하며, 규약이 확정되면 이 클래스의 상수만 바꾸면 된다.
 *
 * <p>소프트 삭제된 알림은 ERD 정책("소프트 삭제된 데이터는 일반 조회에서 제외")에 따라 뺀다.
 */
@Repository
public class JdbcMinusAccountAlertReader implements MinusAccountAlertReader {

    private static final String SOURCE_TYPE = "MINUS_ACCOUNT";

    private static final String WHERE_CLAUSE = """
            WHERE alert.user_id = :userId
              AND alert.source_type = :sourceType
              AND alert.source_id = :sourceId
              AND alert.deleted_at IS NULL
              AND (:unreadOnly = FALSE OR alert.is_read = FALSE)
            """;

    private static final String CONTENT_SQL = """
            SELECT
                alert.notification_id,
                alert.type,
                alert.title,
                alert.message,
                alert.is_read,
                alert.created_at,
                alert.read_at
            FROM notification alert
            """
            + WHERE_CLAUSE
            + """
            ORDER BY alert.created_at DESC, alert.notification_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM notification alert
            """
            + WHERE_CLAUSE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMinusAccountAlertReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<MinusAccountAlertRow> read(
            Long userId,
            Long accountId,
            boolean unreadOnly,
            Pageable pageable
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        parameters.put("sourceType", SOURCE_TYPE);
        parameters.put("sourceId", String.valueOf(accountId));
        parameters.put("unreadOnly", unreadOnly);

        long total = Objects.requireNonNullElse(
                jdbcTemplate.queryForObject(COUNT_SQL, parameters, Long.class),
                0L
        );
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        parameters.put("limit", pageable.getPageSize());
        parameters.put("offset", pageable.getOffset());
        List<MinusAccountAlertRow> rows = jdbcTemplate.query(
                CONTENT_SQL,
                parameters,
                (resultSet, rowNumber) -> new MinusAccountAlertRow(
                        resultSet.getLong("notification_id"),
                        resultSet.getString("type"),
                        resultSet.getString("title"),
                        resultSet.getString("message"),
                        resultSet.getBoolean("is_read"),
                        resultSet.getObject("created_at", LocalDateTime.class),
                        resultSet.getObject("read_at", LocalDateTime.class)
                )
        );
        return new PageImpl<>(rows, pageable, total);
    }
}
