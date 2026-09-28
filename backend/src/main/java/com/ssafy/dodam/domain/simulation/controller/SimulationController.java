package com.ssafy.dodam.domain.simulation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.request.CashAdvanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.CombinedSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.InstallmentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanNewSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.RevolvingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingProductSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationRunResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationSummaryResponse;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationInputResolver;
import com.ssafy.dodam.domain.simulation.service.SimulationQueryService;
import com.ssafy.dodam.domain.simulation.service.SimulationRunner;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 17 Simulation. 실행 경로 10종(spending·saving·saving-product·loan/new·loan/prepayment·loan/refinance·installment·revolving·
 * cash-advance·combined)과 공통 조회·삭제. 실행은 모두 같은 Runner·Store·조회 계약을 쓴다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {
    private final SimulationRunner runner;
    private final SimulationInputResolver resolver;
    private final SimulationQueryService query;
    private final AuthenticatedUserIdResolver users;

    public SimulationController(SimulationRunner runner, SimulationInputResolver resolver, SimulationQueryService query,
            AuthenticatedUserIdResolver users) {
        this.runner = runner;
        this.resolver = resolver;
        this.query = query;
        this.users = users;
    }

    @PostMapping("/spending")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> spending(@Valid @RequestBody SpendingSimulationRequest request,
            Authentication authentication) {
        return run(users.resolve(authentication), resolver.resolve(request));
    }

    @PostMapping("/saving")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> saving(@Valid @RequestBody SavingSimulationRequest request,
            Authentication authentication) {
        Long userId = users.resolve(authentication);
        return run(userId, resolver.resolve(userId, request));
    }

    @PostMapping("/saving-product")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> savingProduct(
            @Valid @RequestBody SavingProductSimulationRequest request, Authentication authentication) {
        return run(users.resolve(authentication), resolver.resolve(request));
    }

    @PostMapping("/loan/new")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> loanNew(@Valid @RequestBody LoanNewSimulationRequest request,
            Authentication authentication) {
        return run(users.resolve(authentication), resolver.resolve(request));
    }

    @PostMapping("/loan/prepayment")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> loanPrepayment(
            @Valid @RequestBody LoanPrepaymentSimulationRequest request, Authentication authentication) {
        Long userId = users.resolve(authentication);
        return run(userId, resolver.resolve(userId, request));
    }

    @PostMapping("/loan/refinance")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> loanRefinance(
            @Valid @RequestBody LoanRefinanceSimulationRequest request, Authentication authentication) {
        Long userId = users.resolve(authentication);
        return run(userId, resolver.resolve(userId, request));
    }

    @PostMapping("/installment")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> installment(
            @Valid @RequestBody InstallmentSimulationRequest request, Authentication authentication) {
        return run(users.resolve(authentication), resolver.resolve(request));
    }

    @PostMapping("/revolving")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> revolving(@Valid @RequestBody RevolvingSimulationRequest request,
            Authentication authentication) {
        Long userId = users.resolve(authentication);
        return run(userId, resolver.resolve(userId, request));
    }

    @PostMapping("/cash-advance")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> cashAdvance(
            @Valid @RequestBody CashAdvanceSimulationRequest request, Authentication authentication) {
        return run(users.resolve(authentication), resolver.resolve(request));
    }

    @PostMapping("/combined")
    public ResponseEntity<ApiResponse<SimulationRunResponse>> combined(@Valid @RequestBody CombinedSimulationRequest request,
            Authentication authentication) {
        Long userId = users.resolve(authentication);
        return run(userId, resolver.resolve(userId, request));
    }

    private ResponseEntity<ApiResponse<SimulationRunResponse>> run(Long userId, SimulationInput input) {
        SimulationRunner.Outcome outcome = runner.run(userId, input);
        SimulationResponse simulation = query.toResponse(userId, outcome.simulation());
        return ResponseEntity.status(outcome.created() ? 201 : 200)
                .body(ApiResponse.success(new SimulationRunResponse(simulation, outcome.created())));
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
