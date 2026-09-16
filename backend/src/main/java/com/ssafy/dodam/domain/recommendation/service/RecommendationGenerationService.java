package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationGenerationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSimulationPreset;
import com.ssafy.dodam.domain.recommendation.service.RecommendationPriorityPolicy.Scored;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.PrioritizedCandidate;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.SaveRequest;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.Saved;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * POST /recommendations/generate: 완료 진단 읽기 → 후보 수집 → 점수·순위 → 저장.
 *
 * <p>후보 수집과 점수 계산은 순수 계산이라 저장 트랜잭션(사용자 행 잠금) 밖에서 끝낸다(547 계약). 같은 진단·같은 규칙 버전이면
 * 같은 키가 나오므로 재요청은 기존 행을 돌려받는다({@code reused}) — 명세의 "중복 요청 방지"는 이 멱등 계약으로 충족한다.
 * 기존 행의 priority는 갱신하지 않는다.
 */
@Service
public class RecommendationGenerationService {
    private final RecommendationInputReader reader;
    private final RecommendationCandidateCollector collector;
    private final RecommendationStore store;
    private final RecommendationClock clock;
    private final RecommendationSimulationPresetAssembler presets;

    public RecommendationGenerationService(RecommendationInputReader reader, RecommendationCandidateCollector collector,
            RecommendationStore store, RecommendationClock clock, RecommendationSimulationPresetAssembler presets) {
        this.reader = reader;
        this.collector = collector;
        this.store = store;
        this.clock = clock;
        this.presets = presets;
    }

    public RecommendationGenerationResponse generate(Long userId, Long diagnosisId, Long requestedGoalId) {
        RecommendationInput input = reader.read(userId, diagnosisId, requestedGoalId);
        CandidateGenerationResult collected = collector.collect(input);
        LocalDateTime now = clock.now();
        List<Scored> ranked = RecommendationPriorityPolicy.rank(input, collected.candidates(), baseDate(input));
        List<PrioritizedCandidate> prioritized = ranked.stream()
                .map(s -> new PrioritizedCandidate(s.candidate(), s.priority())).toList();
        List<Saved> saved = store.save(new SaveRequest(userId, input.diagnosisId(), input.goalId(), prioritized), now);

        Map<Long, RecommendationSimulationPreset> presetById = presets.assembleAll(userId,
                saved.stream().map(Saved::recommendation).toList());
        List<RecommendationGenerationResponse.Item> items = new ArrayList<>();
        int created = 0;
        for (int i = 0; i < saved.size(); i++) {
            Saved s = saved.get(i);
            if (s.created()) {
                created++;
            }
            items.add(new RecommendationGenerationResponse.Item(RecommendationResponse.from(s.recommendation(),
                    presetById.get(s.recommendation().getRecommendationId())),
                    ranked.get(i).score(), s.created()));
        }
        return new RecommendationGenerationResponse(input.diagnosisId(), input.goalId(), created, saved.size() - created,
                items, collected.skipped());
    }

    // 시급성 판정 기준일은 생성기와 같은 진단 기준일(스냅샷 asOf, 없으면 완료일)이다.
    private static LocalDate baseDate(RecommendationInput input) {
        var asOf = input.inputSnapshot() == null ? null : input.inputSnapshot().get("asOf");
        if (asOf != null && asOf.isTextual()) {
            try {
                return LocalDateTime.parse(asOf.asText()).toLocalDate();
            } catch (java.time.format.DateTimeParseException ignored) {
                // 형식이 다르면 완료일로
            }
        }
        return input.completedAt().toLocalDate();
    }
}
