package com.ssafy.dodam.domain.simulation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationSummaryResponse;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 17 Simulation 공통 조회·삭제(550). 유형별 실행 경로는 계산기가 있는 유형만 연다(551부터).
 * 구현 전인 실행 경로는 존재하지 않는다 — 404가 "미구현"을 뜻한다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationQueryService query;
    private final AuthenticatedUserIdResolver users;

    public SimulationController(SimulationQueryService query, AuthenticatedUserIdResolver users) {
        this.query = query;
        this.users = users;
    }

    @GetMapping
    public ApiResponse<PageResponse<SimulationSummaryResponse>> list(
            @RequestParam(required = false) SimulationType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.list(users.resolve(authentication), type, page, size));
    }

    @GetMapping("/{simulationId}")
    public ApiResponse<SimulationResponse> detail(@PathVariable @Positive Long simulationId, Authentication authentication) {
        return ApiResponse.success(query.detail(users.resolve(authentication), simulationId));
    }

    @GetMapping("/{simulationId}/comparison")
    public ApiResponse<JsonNode> comparison(@PathVariable @Positive Long simulationId, Authentication authentication) {
        return ApiResponse.success(query.comparison(users.resolve(authentication), simulationId));
    }

    @GetMapping("/{simulationId}/cashflow")
    public ApiResponse<JsonNode> cashflow(@PathVariable @Positive Long simulationId, Authentication authentication) {
        return ApiResponse.success(query.cashflow(users.resolve(authentication), simulationId));
    }

    @GetMapping("/{simulationId}/goal-impact")
    public ApiResponse<List<SimulationGoalImpactResponse>> goalImpact(@PathVariable @Positive Long simulationId,
            Authentication authentication) {
        return ApiResponse.success(query.goalImpact(users.resolve(authentication), simulationId));
    }

    @GetMapping("/{simulationId}/assets")
    public ApiResponse<PageResponse<JsonNode>> assets(@PathVariable @Positive Long simulationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.comparisonSection(users.resolve(authentication), simulationId, "assets", page, size));
    }

    @GetMapping("/{simulationId}/debts")
    public ApiResponse<PageResponse<JsonNode>> debts(@PathVariable @Positive Long simulationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.comparisonSection(users.resolve(authentication), simulationId, "debts", page, size));
    }

    @GetMapping("/{simulationId}/costs")
    public ApiResponse<PageResponse<JsonNode>> costs(@PathVariable @Positive Long simulationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.comparisonSection(users.resolve(authentication), simulationId, "costs", page, size));
    }

    @DeleteMapping("/{simulationId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long simulationId, Authentication authentication) {
        query.delete(users.resolve(authentication), simulationId);
        return ResponseEntity.noContent().build();
    }
}
