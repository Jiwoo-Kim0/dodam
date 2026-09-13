package com.ssafy.dodam.domain.diagnosis.controller;

import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisResponse;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisIssueResponse;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisQueryService;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisRunner;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/diagnoses")
public class DiagnosisController {
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,49}$";
    private final DiagnosisQueryService query;
    private final DiagnosisRunner runner;
    private final AuthenticatedUserIdResolver users;

    public DiagnosisController(DiagnosisQueryService query, DiagnosisRunner runner, AuthenticatedUserIdResolver users) {
        this.query = query;
        this.runner = runner;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiagnosisRunner.Result>> run(
            @Valid @RequestBody(required = false) Request request, Authentication authentication) {
        Request input = request == null ? new Request(null, false) : request;
        var result = runner.run(users.resolve(authentication), input.goalId(), Boolean.TRUE.equals(input.forceRefresh()));
        return ResponseEntity.status(result.cached() ? 200 : 201).body(ApiResponse.success(result));
    }

    @GetMapping
    public ApiResponse<PageResponse<DiagnosisResponse>> history(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.history(users.resolve(authentication), page, size));
    }

    @GetMapping("/latest")
    public ApiResponse<DiagnosisResponse> latest(@RequestParam(required = false) @Positive Long goalId,
            Authentication authentication) {
        return ApiResponse.success(query.latest(users.resolve(authentication), goalId));
    }

    @GetMapping("/{diagnosisId}")
    public ApiResponse<DiagnosisResponse> detail(@PathVariable @Positive Long diagnosisId, Authentication authentication) {
        return ApiResponse.success(query.detail(users.resolve(authentication), diagnosisId));
    }

    @GetMapping("/{diagnosisId}/summary")
    public ApiResponse<DiagnosisQueryService.Summary> summary(@PathVariable @Positive Long diagnosisId,
            Authentication authentication) {
        return ApiResponse.success(query.summary(users.resolve(authentication), diagnosisId));
    }

    @GetMapping("/{diagnosisId}/issues")
    public ApiResponse<PageResponse<DiagnosisIssueResponse>> issues(@PathVariable @Positive Long diagnosisId,
            @RequestParam(required = false) @Pattern(regexp = CODE) String domain,
            @RequestParam(required = false) @Pattern(regexp = CODE) String type,
            @RequestParam(required = false) IssueSeverity severity,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.issues(users.resolve(authentication), diagnosisId, domain, type, severity, page, size));
    }

    @GetMapping("/{diagnosisId}/issues/{issueId}")
    public ApiResponse<DiagnosisIssueResponse> issue(@PathVariable @Positive Long diagnosisId,
            @PathVariable @Positive Long issueId, Authentication authentication) {
        return ApiResponse.success(query.issue(users.resolve(authentication), diagnosisId, issueId));
    }

    public record Request(@Positive Long goalId, Boolean forceRefresh) { }
}
