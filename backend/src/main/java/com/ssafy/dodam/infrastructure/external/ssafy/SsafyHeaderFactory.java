package com.ssafy.dodam.infrastructure.external.ssafy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 거래 API 공통 Header를 만든다. 전송일시는 금융망 기준(Asia/Seoul)으로 찍는다.
 *
 * 기관거래고유번호는 "전송일자 + 전송시각 + 6자리 일련번호"이며 금융망이 중복을 거절(H1001 등)하므로 호출마다 새로 생성한다.
 * 일련번호는 서버 여러 대가 같은 초에 호출해도 겹치지 않도록 난수를 쓴다.
 */
@Component
public class SsafyHeaderFactory {

    static final String INSTITUTION_CODE = "00100";
    static final String FINTECH_APP_NO = "001";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final int SERIAL_BOUND = 1_000_000;
    private static final ZoneId FINANCE_ZONE = ZoneId.of("Asia/Seoul");

    private final String apiKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SsafyHeaderFactory(SsafyFinanceProperties properties) {
        this(properties, Clock.system(FINANCE_ZONE));
    }

    SsafyHeaderFactory(SsafyFinanceProperties properties, Clock clock) {
        this.apiKey = properties.apiKey();
        this.clock = clock;
    }

    /**
     * @param apiName API 문서의 API명(= URL 마지막 경로 = apiServiceCode)
     * @param userKey 사용자 단위 API의 userKey. 앱 단위 API면 null
     */
    public SsafyRequestHeader create(String apiName, String userKey) {
        LocalDateTime now = LocalDateTime.now(clock);
        String date = now.format(DATE);
        String time = now.format(TIME);
        return new SsafyRequestHeader(
                apiName,
                date,
                time,
                INSTITUTION_CODE,
                FINTECH_APP_NO,
                apiName,
                date + time + String.format("%06d", random.nextInt(SERIAL_BOUND)),
                apiKey,
                userKey
        );
    }
}
