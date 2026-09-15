package com.ssafy.dodam.domain.simulation.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * 586 신규 DTO의 정수 필드용. 전역 ObjectMapper는 {@code ACCEPT_FLOAT_AS_INT}(기본값)라 {@code 1000.5}가 1000으로 절사돼
 * 들어온다. 금액·개월 수를 묵시적으로 절사하지 않도록 소수 토큰과 소수점이 있는 문자열을 거부한다(→ 400 INVALID_REQUEST).
 * 기존 6종의 동작(전역 설정)은 바꾸지 않는다.
 */
public class WholeNumberDeserializer extends JsonDeserializer<Long> {
    static final String MESSAGE = "정수만 허용합니다.";

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = parser.getText().strip();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return (Long) context.handleWeirdStringValue(Long.class, text, MESSAGE);
            }
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            return (Long) context.handleWeirdNumberValue(Long.class, parser.getNumberValue(), MESSAGE);
        }
        return (Long) context.handleUnexpectedToken(Long.class, parser);
    }

    @Override
    public Long getNullValue(DeserializationContext context) {
        return null;
    }

    /** Integer 필드(개월 수)용. 범위는 Bean Validation이 본다. */
    public static class ForInteger extends JsonDeserializer<Integer> {
        private final WholeNumberDeserializer delegate = new WholeNumberDeserializer();

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            Long value = delegate.deserialize(parser, context);
            if (value == null) {
                return null;
            }
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return (Integer) context.handleWeirdNumberValue(Integer.class, value, "허용 범위를 벗어났습니다.");
            }
            return value.intValue();
        }

        @Override
        public Integer getNullValue(DeserializationContext context) {
            return null;
        }
    }
}
