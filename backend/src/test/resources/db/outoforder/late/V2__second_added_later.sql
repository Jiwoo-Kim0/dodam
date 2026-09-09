-- Flyway out-of-order 재현용 (FlywayOutOfOrderTest). V1·V3가 이미 적용된 DB에 나중에 추가되는 낮은 버전.
CREATE TABLE ooo_second (id INT PRIMARY KEY);
