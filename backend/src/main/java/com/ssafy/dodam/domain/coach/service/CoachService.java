package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.response.CoachDegradedReason;
import com.ssafy.dodam.domain.coach.dto.response.CoachMessageResponse;
import com.ssafy.dodam.domain.coach.dto.response.CoachSource;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * API 21 3종의 공통 흐름. 세 엔드포인트의 차이는 {@link CoachFactAssembler}가 무엇을 모으는지뿐이고,
 * 그 뒤 검사·호출·대체는 전부 같은 경로를 탄다.
 *
 * <pre>
 *   사실 조립(소유권 검증 포함)
 *     → 프롬프트 생성
 *     → 민감정보 검사   ─ 걸리면 호출하지 않고 템플릿
 *     → 호출 예산 확인  ─ 넘었으면 호출하지 않고 템플릿
 *     → 외부 AI 호출    ─ 실패하면 템플릿
 *     → 숫자 검증       ─ 지어낸 숫자가 있으면 템플릿
 *     → 응답(수치·판정은 어느 경로든 서버 계산값)
 * </pre>
 *
 * <p><b>실패해도 200이다.</b> 코치 문장은 진단·추천 데이터의 표현 계층이지 데이터 자체가 아니다.
 * 외부 API가 죽었다고 502를 돌려주면 FE는 화면을 못 그리는데, 정작 보여줄 수치는 전부 서버에 있다.
 * 그래서 실패를 상태 코드가 아니라 {@code source}/{@code degradedReason} 필드로 알린다. 인증·소유권
 * 실패(401/404)는 이와 무관하게 그대로 예외로 올린다 — 그쪽은 데이터가 없는 것이 맞다.
 */
@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final CoachFactAssembler assembler;
    private final CoachPromptFactory prompts;
    private final CoachTextGenerator generator;
    private final CoachTemplateWriter template;
    private final CoachNumberGuard numbers;
    private final CoachSensitiveDataGuard sensitive;
    private final CoachAnswerCache cache;
    private final CoachCacheKey cacheKey;
    private final CoachCallRateLimiter rateLimiter;
    private final CoachClock clock;

    public CoachService(CoachFactAssembler assembler, CoachPromptFactory prompts, CoachTextGenerator generator,
            CoachTemplateWriter template, CoachNumberGuard numbers, CoachSensitiveDataGuard sensitive,
            CoachAnswerCache cache, CoachCacheKey cacheKey, CoachCallRateLimiter rateLimiter, CoachClock clock) {
        this.assembler = assembler;
        this.prompts = prompts;
        this.generator = generator;
        this.template = template;
        this.numbers = numbers;
        this.sensitive = sensitive;
        this.cache = cache;
        this.cacheKey = cacheKey;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public CoachMessageResponse financialSummary(Long userId, Long diagnosisId) {
        return explain(userId, assembler.financialSummary(userId, diagnosisId));
    }

    public CoachMessageResponse inefficiency(Long userId, Long diagnosisId, Long issueId) {
        return explain(userId, assembler.inefficiency(userId, diagnosisId, issueId));
    }

    public CoachMessageResponse recommendation(Long userId, Long recommendationId) {
        return explain(userId, assembler.recommendation(userId, recommendationId));
    }

    private CoachMessageResponse explain(Long userId, CoachFacts facts) {
        // 캐시는 생성기 가용성보다 먼저 본다. 같은 사실에 대해 이미 받아 둔 문장이 있으면 외부 호출이
        // 필요 없고, AI가 지금 꺼져 있더라도 그 문장을 쓰는 편이 템플릿보다 낫다.
        String key = cacheKey.of(userId, facts);
        Optional<CoachAnswerCache.Entry> cached = cache.find(key);
        if (cached.isPresent()) {
            return respond(facts, CoachSource.AI, null, cached.get().model(), cached.get().message());
        }

        if (!generator.isAvailable()) {
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.AI_DISABLED, null, template.write(facts));
        }

        String systemPrompt = prompts.system(facts.topic());
        String userPrompt = prompts.user(facts);

        List<String> violations = sensitive.scan(userPrompt);
        if (!violations.isEmpty()) {
            // 조립기가 거르지 못한 값이 있다는 뜻이므로 회귀로 보고 ERROR로 남긴다. 프롬프트 본문은 남기지 않는다.
            log.error("Coach prompt blocked before the external call: topic={} violations={}", facts.topic(), violations);
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.SENSITIVE_DATA_BLOCKED, null, template.write(facts));
        }

        // 예산은 "돈이 나가기 직전"에만 깎는다. 캐시 적중과 AI 비활성은 과금이 없으므로 여기까지 오지 않고,
        // 민감정보로 막힌 요청도 외부로 나가지 않으므로 이 줄 뒤에 둔다.
        if (!rateLimiter.tryAcquire(userId)) {
            // 정상 사용으로는 닿기 어려운 한도다. 반복해서 찍히면 FE 루프나 스크립트를 의심한다.
            log.warn("Coach call refused by the per-user budget: userId={} topic={}", userId, facts.topic());
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.RATE_LIMITED, null, template.write(facts));
        }

        String generated;
        try {
            generated = generator.generate(systemPrompt, userPrompt);
        } catch (CoachGenerationException exception) {
            log.warn("Coach generation failed, falling back to template: topic={} reason={}",
                    facts.topic(), exception.getMessage());
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.AI_UNAVAILABLE, null, template.write(facts));
        }

        if (generated == null || generated.isBlank()) {
            log.warn("Coach generation returned an empty body: topic={}", facts.topic());
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.AI_EMPTY_RESPONSE, null, template.write(facts));
        }

        Optional<String> unverified = numbers.findUnverifiedNumber(generated, facts);
        if (unverified.isPresent()) {
            // 모델이 지어낸 숫자. 어떤 토큰이 걸렸는지만 남긴다 — 프롬프트·응답 전문을 로그에 흘리지 않는다.
            log.warn("Coach answer contains a number that was not provided: topic={} token={}",
                    facts.topic(), unverified.get());
            return respond(facts, CoachSource.TEMPLATE, CoachDegradedReason.AI_UNVERIFIED_NUMBER, null, template.write(facts));
        }

        String message = generated.trim();
        int limit = prompts.maxMessageLength(facts.topic());
        if (message.length() > limit) {
            // 버리지 않는다. 조금 긴 좋은 문장이 템플릿보다 낫다. 프롬프트가 먹히는지만 관측한다.
            log.warn("Coach answer is longer than the prompt asked: topic={} length={} limit={}",
                    facts.topic(), message.length(), limit);
        }
        // 성공한 AI 문장만 저장한다. 템플릿을 저장하면 공급자가 복구된 뒤에도 TTL 내내 템플릿만 나간다.
        cache.put(key, new CoachAnswerCache.Entry(message, generator.modelName()));
        return respond(facts, CoachSource.AI, null, generator.modelName(), message);
    }

    private CoachMessageResponse respond(CoachFacts facts, CoachSource source, CoachDegradedReason reason,
            String model, String message) {
        return new CoachMessageResponse(facts.topic(), source, reason, model, clock.now(), facts.subject(),
                message, template.highlights(facts), facts, CoachMessageResponse.DISCLAIMER);
    }
}
