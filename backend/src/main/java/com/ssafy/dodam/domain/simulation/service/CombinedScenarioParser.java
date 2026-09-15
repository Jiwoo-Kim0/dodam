package com.ssafy.dodam.domain.simulation.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ssafy.dodam.domain.simulation.dto.request.CashAdvanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.InstallmentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanNewSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.RevolvingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingProductSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 복합 요청의 자식 원소 {@code { type, parameters }}를 단독 API와 같은 DTO로 바꾼다.
 *
 * <p>유형은 허용 목록(COMBINED 제외 9종)으로만 매핑하고 클래스 이름 기반 다형성은 쓰지 않는다. 원소에 type·parameters 외의
 * 필드(simulationId, 다른 자식 참조 등)가 있거나 parameters에 DTO가 모르는 필드가 있으면 400이다 — 전역 ObjectMapper의
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}를 바꾸지 않고 이 파서의 reader에만 켠다. Bean Validation은 단독 API의
 * {@code @Valid}와 같은 제약을 같은 DTO로 검사한다. 오류 경로는 {@code scenarios[i].parameters.<field>}이며 입력 원문은 메시지에
 * 넣지 않는다.
 */
@Component
public class CombinedScenarioParser {
    static final String TYPE_FIELD = "type";
    static final String PARAMETERS_FIELD = "parameters";
    private static final Set<String> ALLOWED_FIELDS = Set.of(TYPE_FIELD, PARAMETERS_FIELD);
    private static final Map<SimulationType, Class<?>> REQUEST_TYPES = new EnumMap<>(SimulationType.class);

    static {
        REQUEST_TYPES.put(SimulationType.SPENDING, SpendingSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.SAVING, SavingSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.SAVING_PRODUCT, SavingProductSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.LOAN_NEW, LoanNewSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.LOAN_PREPAYMENT, LoanPrepaymentSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.LOAN_REFINANCE, LoanRefinanceSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.INSTALLMENT, InstallmentSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.REVOLVING, RevolvingSimulationRequest.class);
        REQUEST_TYPES.put(SimulationType.CASH_ADVANCE, CashAdvanceSimulationRequest.class);
    }

    private final ObjectMapper json;
    private final Validator validator;

    public CombinedScenarioParser(ObjectMapper json, Validator validator) {
        this.json = json;
        this.validator = validator;
    }

    /** 파싱·검증이 끝난 자식. {@code request}는 유형에 대응하는 단독 DTO다. */
    public record ParsedScenario(int index, SimulationType type, Object request) {
    }

    public ParsedScenario parse(int index, JsonNode scenario) {
        String path = "scenarios[" + index + "]";
        if (scenario == null || !scenario.isObject()) {
            throw new InvalidRequestException(path + ": type과 parameters를 가진 객체여야 합니다.");
        }
        for (Iterator<String> names = scenario.fieldNames(); names.hasNext();) {
            String name = names.next();
            if (!ALLOWED_FIELDS.contains(name)) {
                throw new InvalidRequestException(path + "." + name + ": 허용하지 않는 필드입니다.");
            }
        }
        SimulationType type = typeOf(path, scenario.get(TYPE_FIELD));
        JsonNode parameters = scenario.get(PARAMETERS_FIELD);
        if (parameters == null || !parameters.isObject()) {
            throw new InvalidRequestException(path + "." + PARAMETERS_FIELD + ": 객체여야 합니다.");
        }
        Object request = readRequest(path + "." + PARAMETERS_FIELD, REQUEST_TYPES.get(type), parameters);
        validate(path + "." + PARAMETERS_FIELD, request);
        return new ParsedScenario(index, type, request);
    }

    private static SimulationType typeOf(String path, JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new InvalidRequestException(path + "." + TYPE_FIELD + ": 필수입니다.");
        }
        SimulationType type;
        try {
            type = SimulationType.valueOf(node.asText().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(path + "." + TYPE_FIELD + ": 알 수 없는 시나리오 유형입니다.");
        }
        if (!REQUEST_TYPES.containsKey(type)) {
            // COMBINED 중첩. 허용 목록에 없으므로 같은 메시지 계열로 거부한다
            throw new InvalidRequestException(path + "." + TYPE_FIELD + ": 복합 시나리오는 중첩할 수 없습니다.");
        }
        return type;
    }

    private Object readRequest(String path, Class<?> requestType, JsonNode parameters) {
        try {
            return json.readerFor(requestType).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(parameters);
        } catch (UnrecognizedPropertyException e) {
            throw new InvalidRequestException(path + "." + e.getPropertyName() + ": 허용하지 않는 필드입니다.");
        } catch (JsonMappingException e) {
            throw new InvalidRequestException(path + fieldPath(e) + ": 형식이 올바르지 않습니다.");
        } catch (IOException e) {
            throw new InvalidRequestException(path + ": 형식이 올바르지 않습니다.");
        }
    }

    private static String fieldPath(JsonMappingException e) {
        StringBuilder text = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                text.append('.').append(reference.getFieldName());
            }
        }
        return text.toString();
    }

    private void validate(String path, Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        ConstraintViolation<Object> first = violations.stream()
                .min(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .orElseThrow();
        throw new InvalidRequestException(path + "." + first.getPropertyPath() + ": " + first.getMessage());
    }
}
