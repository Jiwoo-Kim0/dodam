package com.ssafy.dodam.global.schema;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 엔티티 매핑과 Flyway DDL의 컬럼 타입이 맞는지 검사한다.
 *
 * <p><b>왜 있는가.</b> 운영은 `ddl-auto: validate`다. 타입이 하나라도 어긋나면 Hibernate가 <b>기동을 막는다.</b>
 * 2026-09-17에 `monthly_report.year_month`(DDL `CHAR(7)` vs 엔티티 기대 `varchar(7)`)로 실제 장애가 났다.
 * 더 나쁜 것은 validate가 <b>첫 번째 불일치에서 멈춘다</b>는 점이다 — 하나를 고쳐 배포하면 다음 컬럼에서 또
 * 같은 장애가 난다. 이 테스트는 전부를 한 번에 모아 보여 준다.
 *
 * <p><b>왜 MySQL을 띄우지 않는가.</b> MySQL 게이트 테스트(`*MySqlIntegrationTest`)는 로컬 MySQL이 있을 때만
 * 돌고 CI·개발기에서는 건너뛴다. 정작 막아야 할 시점(마이그레이션을 추가하는 순간)에 아무도 실행하지 않는다.
 * 그래서 DB 없이 도는 검사를 따로 둔다.
 *
 * <p><b>무엇을 비교하는가.</b> 양쪽을 {@link Family}(타입 계열)로 정규화해 비교한다. Hibernate 검증을 글자
 * 그대로 흉내 내려면 드라이버가 메타데이터를 어떻게 보고하는지까지 재현해야 하는데(예: MySQL 커넥터는
 * {@code TINYINT(1)}을 {@code BIT}으로, {@code @Enumerated(STRING)}은 {@code enum (...)}으로 보고한다)
 * 그 재현이 틀리면 없는 문제를 만들어 낸다. 계열 비교는 정확히 우리를 문 사고 — <b>CHAR ↔ VARCHAR</b>,
 * 누락된 컬럼 — 를 잡으면서 그런 잡음이 없다.
 *
 * <p><b>한계.</b> 길이는 보지 않는다(Hibernate 검증도 타입만 본다). 제약·인덱스·nullable도 보지 않는다 —
 * 그건 MySQL 게이트 테스트의 몫이다. {@link Family}가 모르는 타입이 나오면 조용히 넘기지 않고
 * <b>실패</b>시킨다(표를 늘려 달라는 뜻이다).
 */
class SchemaTypeConsistencyTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /**
     * 타입 계열. 같은 계열이면 통과다.
     *
     * <p>토큰은 두 방향에서 들어온다. 하나는 Hibernate가 만든 기대 타입 문자열(`varchar(7)`, `enum ('A','B')`,
     * `datetime(6)`, `bit`), 다른 하나는 마이그레이션 DDL 선언(`VARCHAR(30)`, `CHAR(7)`, `TINYINT(1)`).
     * 접두사가 겹치는 것이 있어(`DATE`/`DATETIME`, `CHAR`/`VARCHAR`) 긴 토큰을 먼저 본다.
     */
    private enum Family {
        // `@Enumerated(STRING)`은 MySQL에서 enum(...)으로 선언되지만 저장·검증은 varchar과 같은 계열이다.
        TEXT_VARIABLE("varchar", "enum", "tinytext", "mediumtext", "longtext", "text"),
        TEXT_FIXED("char"),
        BOOLEAN("boolean", "bit", "tinyint"),
        INTEGER("bigint", "smallint", "mediumint", "integer", "int"),
        DECIMAL("decimal", "numeric", "double", "float", "real"),
        TIMESTAMP("datetime", "timestamp"),
        DATE("date"),
        TIME("time"),
        JSON("json"),
        BINARY("varbinary", "binary", "longblob", "blob");

        private final List<String> tokens;

        Family(String... tokens) {
            this.tokens = List.of(tokens);
        }

        /** 접두사가 겹치므로 긴 토큰부터 맞춰 본다(`datetime`이 `date`보다 먼저). */
        static Family of(String declaration) {
            String lower = declaration.trim().toLowerCase(Locale.ROOT);
            return Stream.of(values())
                    .flatMap(family -> family.tokens.stream().map(token -> Map.entry(token, family)))
                    .sorted(Comparator.comparingInt((Map.Entry<String, Family> entry) -> entry.getKey().length())
                            .reversed())
                    .filter(entry -> lower.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Test
    void everyMappedColumnMatchesTheMigrationType() throws Exception {
        Map<String, String> declared = parseMigrations();
        assertThat(declared).as("마이그레이션에서 컬럼을 하나도 읽지 못했다면 파서가 깨진 것이다").isNotEmpty();

        List<String> problems = new ArrayList<>();
        int checked = 0;
        Metadata metadata = entityMetadata();
        for (Table table : metadata.collectTableMappings()) {
            if (table.getName() == null) {
                continue;
            }
            for (Column column : table.getColumns()) {
                checked++;
                String key = key(table.getName(), column.getName());
                String ddl = declared.get(key);
                String expected = column.getSqlType(metadata);
                if (ddl == null) {
                    problems.add("%s — 마이그레이션에 컬럼이 없다(엔티티 기대: %s)".formatted(key, expected));
                    continue;
                }
                Family ddlFamily = Family.of(ddl);
                Family expectedFamily = Family.of(expected);
                if (ddlFamily == null || expectedFamily == null) {
                    problems.add("%s — 모르는 타입이다(DDL=%s, 엔티티 기대=%s). Family에 토큰을 추가해야 한다"
                            .formatted(key, ddl, expected));
                    continue;
                }
                if (ddlFamily != expectedFamily) {
                    problems.add("%s — DDL은 %s(%s)인데 엔티티는 %s(%s)을 기대한다"
                            .formatted(key, ddl, ddlFamily, expected, expectedFamily));
                }
            }
        }

        assertThat(checked).as("엔티티 컬럼을 하나도 읽지 못했다면 스캔이 깨진 것이다").isGreaterThan(300);
        problems.sort(Comparator.naturalOrder());
        assertThat(problems)
                .as("엔티티와 DDL의 타입이 어긋나면 운영(ddl-auto: validate)이 기동하지 못한다.\n"
                        + "고치는 방향은 둘 중 하나다. (1) DDL을 정답으로 보고 엔티티에 "
                        + "columnDefinition 또는 @JdbcTypeCode를 붙인다(이 저장소의 CHAR 컬럼이 쓰는 방식), "
                        + "(2) 엔티티를 정답으로 보고 새 마이그레이션으로 컬럼 타입을 바꾼다. "
                        + "이미 적용된 마이그레이션 파일은 고치지 않는다(Flyway 체크섬).\n"
                        + "불일치 목록:")
                .isEmpty();
    }

    private static Metadata entityMetadata() throws ClassNotFoundException {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<String> names = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.ssafy.dodam")) {
            names.add(definition.getBeanClassName());
        }
        names.sort(Comparator.naturalOrder());
        for (String name : names) {
            sources.addAnnotatedClass(Class.forName(name));
        }
        return sources.buildMetadata();
    }

    // ---------- 마이그레이션 파싱 ----------

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE TABLE\\s+`?(\\w+)`?\\s*\\((.*?)\\n\\);", Pattern.DOTALL);
    private static final Pattern ALTER_TABLE =
            Pattern.compile("ALTER TABLE\\s+`?(\\w+)`?(.*?);", Pattern.DOTALL);
    private static final Pattern COLUMN_LINE =
            Pattern.compile("^`(\\w+)`\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)");
    private static final Pattern ADD_COLUMN =
            Pattern.compile("ADD COLUMN\\s+`(\\w+)`\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)");
    private static final Pattern MODIFY_COLUMN =
            Pattern.compile("MODIFY(?:\\s+COLUMN)?\\s+`(\\w+)`\\s+([A-Za-z]+(?:\\s*\\([^)]*\\))?)");
    private static final List<String> NOT_A_COLUMN =
            List.of("PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "INDEX", "FOREIGN", "CHECK", ")");

    /** 버전 순서대로 적용해 마지막 선언이 남게 한다(뒤 마이그레이션의 MODIFY가 앞 CREATE를 덮는다). */
    private static Map<String, String> parseMigrations() throws IOException {
        Map<String, String> declared = new HashMap<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            files.filter(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .sorted(Comparator.comparingInt(SchemaTypeConsistencyTest::version))
                    .forEach(path -> parse(path, declared));
        }
        return declared;
    }

    private static int version(Path path) {
        Matcher matcher = Pattern.compile("^V(\\d+)__").matcher(path.getFileName().toString());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private static void parse(Path path, Map<String, String> declared) {
        String sql;
        try {
            sql = Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("마이그레이션을 읽을 수 없습니다: " + path, failure);
        }
        sql = sql.lines().filter(line -> !line.stripLeading().startsWith("--"))
                .reduce(new StringBuilder(), (builder, line) -> builder.append(line).append('\n'),
                        StringBuilder::append).toString();

        Matcher create = CREATE_TABLE.matcher(sql);
        while (create.find()) {
            String table = create.group(1);
            for (String line : create.group(2).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || NOT_A_COLUMN.stream().anyMatch(trimmed.toUpperCase(Locale.ROOT)::startsWith)) {
                    continue;
                }
                Matcher column = COLUMN_LINE.matcher(trimmed);
                if (column.find()) {
                    declared.put(key(table, column.group(1)), column.group(2).trim());
                }
            }
        }
        Matcher alter = ALTER_TABLE.matcher(sql);
        while (alter.find()) {
            String table = alter.group(1);
            Matcher added = ADD_COLUMN.matcher(alter.group(2));
            while (added.find()) {
                declared.put(key(table, added.group(1)), added.group(2).trim());
            }
            Matcher modified = MODIFY_COLUMN.matcher(alter.group(2));
            while (modified.find()) {
                declared.put(key(table, modified.group(1)), modified.group(2).trim());
            }
        }
    }

    private static String key(String table, String column) {
        return table.toLowerCase(Locale.ROOT) + "." + column.toLowerCase(Locale.ROOT).replace("`", "");
    }
}
