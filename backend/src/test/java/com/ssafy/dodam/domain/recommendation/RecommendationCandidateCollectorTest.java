package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateCollector;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 후보 수집 골격의 순수 단위 테스트. 운영 생성기가 없으므로 테스트 생성기로 입력 전달·순서·중복 검출을
 * 확인한다. 여기의 생성기는 운영 추천 규칙이 아니다.
 */
class RecommendationCandidateCollectorTest {

    private static final RecommendationInput INPUT = new RecommendationInput(
            10L, 1L, "v2-goal-cashflow", null, LocalDateTime.of(2026, 9, 13, 9, 0), null,
            List.of(
                    issue(1L, "SPENDING", "OVERSPENDING"),
                    issue(2L, "CARD", "HIGH_CARD_FEE"),
                    issue(3L, "LOAN", "HIGH_INTEREST")));

    @Test
    void noGeneratorsMarksEveryIssueAsNoGenerator() {
        CandidateGenerationResult result = new RecommendationCandidateCollector(List.of()).collect(INPUT);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.skipped()).extracting(CandidateGenerationResult.Skipped::reason)
                .containsOnly(SkipReason.NO_GENERATOR);
        assertThat(result.skipped()).extracting(CandidateGenerationResult.Skipped::issueId)
                .containsExactly(1L, 2L, 3L);
    }

    /** 문제가 없어서 후보가 없는 경우는 건너뛴 사유도 없다. 미구현과 구분된다. */
    @Test
    void noIssuesYieldsEmptyResultWithoutSkips() {
        RecommendationInput healthy = new RecommendationInput(10L, 1L, "v2", null, INPUT.completedAt(), null, List.of());

        CandidateGenerationResult result = new RecommendationCandidateCollector(List.of()).collect(healthy);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    void unsupportedIssuesAreReportedAndSupportedOnesGenerated() {
        var cardOnly = generator("card-v1", "HIGH_CARD_FEE",
                issue -> RecommendationCandidateGenerator.Outcome.of(candidate("card-v1", "CARD:" + issue.issueId())));

        CandidateGenerationResult result = new RecommendationCandidateCollector(List.of(cardOnly)).collect(INPUT);

        assertThat(result.candidates()).extracting(RecommendationCandidate::candidateKey).containsExactly("CARD:2");
        assertThat(result.skipped())
                .extracting(CandidateGenerationResult.Skipped::issueId, CandidateGenerationResult.Skipped::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, SkipReason.UNSUPPORTED_ISSUE_TYPE),
                        org.assertj.core.groups.Tuple.tuple(3L, SkipReason.UNSUPPORTED_ISSUE_TYPE));
    }

    /** 순서는 문제 순 → 생성기 등록 순이다. 같은 입력이면 항상 같은 순서로 나온다. */
    @Test
    void orderIsIssueThenGeneratorRegistrationOrder() {
        var product = generator("product-v1", null,
                issue -> RecommendationCandidateGenerator.Outcome.of(candidate("product-v1", "P:" + issue.issueId())));
        var behavior = generator("behavior-v1", null,
                issue -> RecommendationCandidateGenerator.Outcome.of(candidate("behavior-v1", "B:" + issue.issueId())));
        var collector = new RecommendationCandidateCollector(List.of(product, behavior));

        List<String> first = collector.collect(INPUT).candidates().stream().map(RecommendationCandidate::candidateKey).toList();
        List<String> second = collector.collect(INPUT).candidates().stream().map(RecommendationCandidate::candidateKey).toList();

        assertThat(first).containsExactly("P:1", "B:1", "P:2", "B:2", "P:3", "B:3").isEqualTo(second);
    }

    @Test
    void generatorSkipReasonsArePreservedPerIssue() {
        var needsCluster = generator("cluster-v1", "HIGH_INTEREST",
                issue -> RecommendationCandidateGenerator.Outcome.skipped(SkipReason.NO_CLUSTER_CONTEXT));

        CandidateGenerationResult result = new RecommendationCandidateCollector(List.of(needsCluster)).collect(INPUT);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.skipped()).filteredOn(s -> s.issueId() == 3L)
                .extracting(CandidateGenerationResult.Skipped::reason).containsExactly(SkipReason.NO_CLUSTER_CONTEXT);
    }

    /** 같은 (버전, 키) 후보가 두 번 나오면 생성기 결함이다. 조용히 합치지 않는다. */
    @Test
    void duplicateCandidateKeyFails() {
        var buggy = generator("dup-v1", null,
                issue -> RecommendationCandidateGenerator.Outcome.of(candidate("dup-v1", "SAME")));

        assertThatThrownBy(() -> new RecommendationCandidateCollector(List.of(buggy)).collect(INPUT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void candidateWithForeignVersionFails() {
        var mislabeled = generator("real-v1", null,
                issue -> RecommendationCandidateGenerator.Outcome.of(candidate("other-v9", "X:" + issue.issueId())));

        assertThatThrownBy(() -> new RecommendationCandidateCollector(List.of(mislabeled)).collect(INPUT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outcomeCannotCarryBothCandidatesAndSkipReason() {
        assertThatThrownBy(() -> new RecommendationCandidateGenerator.Outcome(
                List.of(candidate("v", "k")), SkipReason.INSUFFICIENT_DATA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RecommendationCandidateGenerator generator(
            String version, String supportedType, Function<RecommendationInput.Issue, RecommendationCandidateGenerator.Outcome> body) {
        return new RecommendationCandidateGenerator() {
            @Override public String version() { return version; }
            @Override public boolean supports(RecommendationInput.Issue issue) {
                return supportedType == null || supportedType.equals(issue.type());
            }
            @Override public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
                return body.apply(issue);
            }
        };
    }

    private static RecommendationCandidate candidate(String version, String key) {
        return new RecommendationCandidate(version, key, null, "CARD", "SWITCH_CARD", "제목", "설명",
                List.of(new RecommendationCandidate.Reason("FEE", "연회비", null)), null);
    }

    private static RecommendationInput.Issue issue(Long id, String domain, String type) {
        return new RecommendationInput.Issue(id, domain, type, IssueSeverity.HIGH, "t", "d",
                null, null, null, DetectionSource.RULE, null, null);
    }
}
