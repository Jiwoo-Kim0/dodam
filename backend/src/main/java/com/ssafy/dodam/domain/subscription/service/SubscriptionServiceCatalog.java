package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.subscription.entity.SubscriptionCategory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 정기결제 서비스 카탈로그(GET /subscriptions/services). 국내에서 흔한 구독 서비스의 이름·카테고리·대표 월 요금·FE 아이콘 키를
 * 코드에 고정해 둔 목록이다. 금융망에는 이런 카탈로그가 없고 ERD에도 테이블이 없어(2026-09-11) 정적 목록으로 시작한다.
 * 후보 탐지(513)가 가맹점명을 이 목록과 맞춰 카테고리를 제안하는 데도 쓴다.
 *
 * <p>대표 요금은 2026-09 기준 개인 표준 요금제의 월 금액이며 안내용이다. 실제 청구 금액은 거래·정기결제 데이터가 기준이다.
 */
@Component
public class SubscriptionServiceCatalog {

    /**
     * @param keywords 가맹점명과 맞출 때 쓰는 소문자 키워드(이름 자체도 포함해서 비교한다)
     */
    public record CatalogEntry(
            String serviceCode,
            String name,
            SubscriptionCategory category,
            long typicalMonthlyAmount,
            String iconKey,
            List<String> keywords
    ) {
    }

    private static final List<CatalogEntry> ENTRIES = List.of(
            entry("NETFLIX", "넷플릭스", SubscriptionCategory.VIDEO, 17000, "netflix", "netflix"),
            entry("YOUTUBE_PREMIUM", "유튜브 프리미엄", SubscriptionCategory.VIDEO, 14900, "youtube", "youtube", "구글 유튜브"),
            entry("DISNEY_PLUS", "디즈니+", SubscriptionCategory.VIDEO, 9900, null, "disney", "디즈니"),
            entry("TVING", "티빙", SubscriptionCategory.VIDEO, 9500, null, "tving"),
            entry("WAVVE", "웨이브", SubscriptionCategory.VIDEO, 7900, null, "wavve"),
            entry("WATCHA", "왓챠", SubscriptionCategory.VIDEO, 7900, null, "watcha"),
            entry("COUPANG_PLAY", "쿠팡플레이", SubscriptionCategory.VIDEO, 7890, "coupang", "쿠팡플레이", "coupang play"),
            entry("SPOTIFY", "스포티파이", SubscriptionCategory.MUSIC, 10900, "spotify", "spotify"),
            entry("MELON", "멜론", SubscriptionCategory.MUSIC, 10900, null, "melon", "카카오 멜론"),
            entry("YOUTUBE_MUSIC", "유튜브 뮤직", SubscriptionCategory.MUSIC, 11990, "youtube", "youtube music"),
            entry("GENIE", "지니뮤직", SubscriptionCategory.MUSIC, 8400, null, "genie", "지니"),
            entry("MILLIE", "밀리의서재", SubscriptionCategory.BOOK, 9900, "millie", "millie", "밀리"),
            entry("RIDI_SELECT", "리디셀렉트", SubscriptionCategory.BOOK, 4900, null, "ridi", "리디"),
            entry("CHATGPT_PLUS", "챗GPT 플러스", SubscriptionCategory.AI, 29000, "chatgpt", "chatgpt", "openai", "챗gpt"),
            entry("CLAUDE_PRO", "Claude Pro", SubscriptionCategory.AI, 29000, null, "claude", "anthropic"),
            entry("ICLOUD_PLUS", "iCloud+", SubscriptionCategory.CLOUD, 1100, null, "icloud", "apple.com/bill", "애플"),
            entry("GOOGLE_ONE", "구글 원", SubscriptionCategory.CLOUD, 2400, null, "google one", "구글 원", "google storage"),
            entry("NAVER_MYBOX", "네이버 MYBOX", SubscriptionCategory.CLOUD, 3300, "naver", "mybox", "마이박스"),
            entry("COUPANG_WOW", "쿠팡 와우 멤버십", SubscriptionCategory.SHOPPING, 7890, "rocket", "쿠팡와우", "쿠팡 와우", "coupang wow", "쿠팡"),
            entry("NAVER_PLUS", "네이버플러스 멤버십", SubscriptionCategory.SHOPPING, 4900, "naver", "네이버플러스", "naver plus"),
            entry("SSG_UNIVERSE", "신세계 유니버스 클럽", SubscriptionCategory.SHOPPING, 2500, "ssg-com", "유니버스", "ssg"),
            entry("BAEMIN_CLUB", "배민클럽", SubscriptionCategory.DELIVERY, 3990, "baemin", "배민클럽", "배민"),
            entry("YOGIYO_PASS", "요기패스", SubscriptionCategory.DELIVERY, 2900, "yogiyo", "요기패스", "요기요"),
            entry("NINTENDO_ONLINE", "닌텐도 스위치 온라인", SubscriptionCategory.GAME, 4900, null, "nintendo", "닌텐도"),
            entry("XBOX_GAME_PASS", "Xbox 게임패스", SubscriptionCategory.GAME, 11900, null, "xbox", "game pass", "게임패스"),
            entry("PLAYSTATION_PLUS", "PlayStation Plus", SubscriptionCategory.GAME, 11900, null, "playstation", "플레이스테이션"),
            entry("DUOLINGO", "듀오링고", SubscriptionCategory.EDUCATION, 15900, null, "duolingo", "듀오링고"),
            entry("CLASS101", "클래스101", SubscriptionCategory.EDUCATION, 19000, null, "class101", "클래스101"),
            entry("MICROSOFT_365", "Microsoft 365", SubscriptionCategory.CLOUD, 8900, null, "microsoft", "office 365", "ms365"),
            entry("NOTION_PLUS", "Notion Plus", SubscriptionCategory.CLOUD, 14000, null, "notion")
    );

    private static CatalogEntry entry(
            String code, String name, SubscriptionCategory category, long amount, String iconKey, String... keywords
    ) {
        return new CatalogEntry(code, name, category, amount, iconKey, List.of(keywords));
    }

    public List<CatalogEntry> entries() {
        return ENTRIES;
    }

    public List<CatalogEntry> entries(String category) {
        String normalized = SubscriptionCategory.normalize(category);
        if (normalized == null) {
            return ENTRIES;
        }
        return ENTRIES.stream().filter(entry -> entry.category().name().equals(normalized)).toList();
    }

    /** 가맹점명에 서비스 이름이나 키워드가 들어 있으면 그 서비스. 여러 개면 먼저 선언된 것. */
    public Optional<CatalogEntry> match(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return Optional.empty();
        }
        String haystack = merchantName.toLowerCase(Locale.ROOT).replace(" ", "");
        return ENTRIES.stream()
                .filter(entry -> haystack.contains(entry.name().toLowerCase(Locale.ROOT).replace(" ", ""))
                        || entry.keywords().stream()
                        .anyMatch(keyword -> haystack.contains(keyword.toLowerCase(Locale.ROOT).replace(" ", ""))))
                .findFirst();
    }
}
