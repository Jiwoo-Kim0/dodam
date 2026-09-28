package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 소유권 격리와 정렬 계약. H2에는 FK가 없으므로 진단·사용자 행 없이 id만으로 저장한다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:recommendation547repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class RecommendationRepositoryTest {

    private static final AtomicLong USER_SEQUENCE = new AtomicLong(5_470_100L);
    private static final AtomicLong KEY_SEQUENCE = new AtomicLong();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 9, 0);

    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationReasonRepository reasons;
    @Autowired RecommendationHistoryRepository histories;

    @Test
    void headerLookupRequiresOwnership() {
        Long owner = nextUserId();
        Long other = nextUserId();
        Long id = save(owner, "CARD", NOW).getRecommendationId();

        assertThat(recommendations.findByRecommendationIdAndUserId(id, owner)).isPresent();
        assertThat(recommendations.findByRecommendationIdAndUserId(id, other)).isEmpty();
        assertThat(recommendations.findByRecommendationIdAndUserId(id + 999_999L, owner)).isEmpty();
    }

    /** 근거·이력에는 user_id가 없다. recommendationId만 알아도 타인 것은 비어 있어야 한다. */
    @Test
    void reasonsAndHistoryAreHiddenFromOtherUsers() {
        Long owner = nextUserId();
        Long other = nextUserId();
        Long id = save(owner, "CARD", NOW).getRecommendationId();
        reasons.save(RecommendationReason.of(id, "FEE", "연회비", null));
        histories.save(RecommendationHistory.initial(id, NOW));

        assertThat(reasons.findOwned(owner, id)).hasSize(1);
        assertThat(histories.findOwned(owner, id)).hasSize(1);
        assertThat(reasons.findOwned(other, id)).isEmpty();
        assertThat(histories.findOwned(other, id)).isEmpty();
        assertThat(histories.findOwnedInPeriod(other, NOW.minusDays(1), NOW.plusDays(1), PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void listFiltersByStatusAndDomainAndSortsNewestFirstWithIdTieBreak() {
        Long owner = nextUserId();
        Recommendation older = save(owner, "CARD", NOW.minusDays(1));
        Recommendation a = save(owner, "CARD", NOW);
        Recommendation b = save(owner, "LOAN", NOW);
        save(nextUserId(), "CARD", NOW);

        Page<Recommendation> all = recommendations.findOwned(owner, null, null, PageRequest.of(0, 20));
        assertThat(all.getTotalElements()).isEqualTo(3);
        // 같은 createdAt이면 id 내림차순
        assertThat(all.getContent()).extracting(Recommendation::getRecommendationId)
                .containsExactly(b.getRecommendationId(), a.getRecommendationId(), older.getRecommendationId());

        // 결과보다 작은 페이지로 읽어야 파생 count 쿼리(카티전 조인 JPQL)가 실제로 실행된다.
        Page<Recommendation> second = recommendations.findOwned(owner, null, null, PageRequest.of(1, 1));
        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(second.getTotalPages()).isEqualTo(3);
        assertThat(second.getContent()).extracting(Recommendation::getRecommendationId).containsExactly(a.getRecommendationId());

        assertThat(recommendations.findOwned(owner, null, "LOAN", PageRequest.of(0, 20)).getContent())
                .extracting(Recommendation::getRecommendationId).containsExactly(b.getRecommendationId());
        assertThat(recommendations.findOwned(owner, RecommendationStatus.PENDING, null, PageRequest.of(0, 20)))
                .hasSize(3);
        assertThat(recommendations.findOwned(owner, RecommendationStatus.ACCEPTED, null, PageRequest.of(0, 20))).isEmpty();
        assertThat(recommendations.findOwned(owner, null, "NOPE", PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void historyPeriodIsHalfOpenAndNewestFirst() {
        Long owner = nextUserId();
        Long id = save(owner, "CARD", NOW).getRecommendationId();
        RecommendationHistory h1 = histories.save(RecommendationHistory.of(id, RecommendationStatus.PENDING, null, null, NOW));
        RecommendationHistory h2 = histories.save(RecommendationHistory.of(id, RecommendationStatus.ACCEPTED, "r", "m", NOW));
        histories.save(RecommendationHistory.of(id, RecommendationStatus.COMPLETED, null, null, NOW.plusDays(1)));

        List<RecommendationHistory> perRecommendation = histories.findOwned(owner, id);
        assertThat(perRecommendation).extracting(RecommendationHistory::getStatus)
                .containsExactly(RecommendationStatus.COMPLETED, RecommendationStatus.ACCEPTED, RecommendationStatus.PENDING);
        assertThat(perRecommendation.get(1).getRecommendationHistoryId()).isEqualTo(h2.getRecommendationHistoryId());
        assertThat(perRecommendation.get(2).getRecommendationHistoryId()).isEqualTo(h1.getRecommendationHistoryId());

        // [from, to): to 경계의 COMPLETED(NOW+1d)는 제외된다.
        Page<RecommendationHistory> inPeriod = histories.findOwnedInPeriod(owner, NOW, NOW.plusDays(1), PageRequest.of(0, 20));
        assertThat(inPeriod.getContent()).extracting(RecommendationHistory::getStatus)
                .containsExactly(RecommendationStatus.ACCEPTED, RecommendationStatus.PENDING);
        assertThat(histories.findOwnedInPeriod(owner, NOW.plusDays(2), NOW.plusDays(3), PageRequest.of(0, 20))).isEmpty();
        Page<RecommendationHistory> paged = histories.findOwnedInPeriod(owner, NOW, NOW.plusDays(2), PageRequest.of(0, 1));
        assertThat(paged.getTotalElements()).isEqualTo(3);
        assertThat(paged.getContent()).extracting(RecommendationHistory::getStatus).containsExactly(RecommendationStatus.COMPLETED);
    }

    private Recommendation save(Long userId, String domain, LocalDateTime createdAt) {
        String key = String.format("%064x", KEY_SEQUENCE.incrementAndGet());
        return recommendations.save(Recommendation.pending(userId, 1L, null, null, domain, "TYPE", "제목", "설명", 1, null, key, createdAt));
    }

    private static Long nextUserId() {
        return USER_SEQUENCE.incrementAndGet();
    }
}
