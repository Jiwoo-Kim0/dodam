package com.ssafy.dodam.domain.product.service;

import com.ssafy.dodam.domain.product.model.SpendingSegment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CardBenefitCategoryMapper {

    private static final Map<String, Set<String>> CATEGORY_ALIASES = aliases();

    public Set<String> applicableBenefitCategories(SpendingSegment segment) {
        Set<String> categories = new HashSet<>();
        String categoryCode = normalize(segment.categoryCode());
        if (categoryCode != null) {
            categories.add(categoryCode);
            categories.addAll(CATEGORY_ALIASES.getOrDefault(categoryCode, Set.of()));
        }

        if (!"OVERSEAS".equals(categoryCode)) {
            categories.add("DOMESTIC");
        }
        categories.add("ALL");

        String paymentMethod = normalize(segment.paymentMethod());
        if (paymentMethod != null) {
            categories.add(paymentMethod);
            if (paymentMethod.contains("PAY")) {
                categories.add("EASY_PAY");
            }
        }
        return Set.copyOf(categories);
    }

    private static Map<String, Set<String>> aliases() {
        Map<String, Set<String>> aliases = new HashMap<>();
        aliases.put("PUBLIC_TRANSPORT", Set.of("TRANSIT", "TRANSPORT"));
        aliases.put("TRANSPORT", Set.of("TRANSIT"));
        aliases.put("TRANSIT", Set.of("TRANSPORT"));
        aliases.put("DINING", Set.of("FOOD"));
        aliases.put("RESTAURANT", Set.of("FOOD"));
        aliases.put("EDUCATION", Set.of("ACADEMY"));
        aliases.put("CULTURE", Set.of("CINEMA", "MOBILE_CULTURE"));
        aliases.put("SUBSCRIPTION", Set.of("DIGITAL_SUBSCRIPTION", "OTT", "RECURRING"));
        aliases.put("HOUSING", Set.of("APARTMENT", "LIVING"));
        return Map.copyOf(aliases);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
