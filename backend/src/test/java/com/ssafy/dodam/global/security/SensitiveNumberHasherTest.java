package com.ssafy.dodam.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveNumberHasherTest {

    private static final String CARD_NO = "1111222233334444";

    private final SensitiveNumberHasher hasher = new SensitiveNumberHasher(
            new SecurityHashProperties("test-key")
    );

    @Test
    void producesStableLowercaseHexOfSixtyFourCharacters() {
        String hash = hasher.hash(CARD_NO);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hasher.hash(CARD_NO)).isEqualTo(hash);
    }

    @Test
    void producesDifferentHashesForDifferentNumbersAndKeys() {
        SensitiveNumberHasher otherKeyHasher = new SensitiveNumberHasher(
                new SecurityHashProperties("other-key")
        );

        assertThat(hasher.hash(CARD_NO)).isNotEqualTo(hasher.hash("5555666677778888"));
        assertThat(hasher.hash(CARD_NO)).isNotEqualTo(otherKeyHasher.hash(CARD_NO));
    }

    @Test
    void requiresConfiguredKeyAndNonBlankValue() {
        assertThatThrownBy(() -> new SensitiveNumberHasher(new SecurityHashProperties(" ")))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> hasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
