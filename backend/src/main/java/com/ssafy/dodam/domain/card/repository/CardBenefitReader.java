package com.ssafy.dodam.domain.card.repository;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

public interface CardBenefitReader {

    Optional<JsonNode> readBenefits(Long financialProductId);
}
