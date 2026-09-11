package com.ssafy.dodam.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountNumberMaskerTest {

    @Test
    void masksSixteenDigitFinanceAccountNumberLikeSeed() {
        assertThat(AccountNumberMasker.mask("1234567890123456")).isEqualTo("123456-**-**3456");
        assertThat(AccountNumberMasker.mask("0011234567890001")).isEqualTo("001123-**-**0001");
    }

    @Test
    void ignoresSeparatorsInInput() {
        assertThat(AccountNumberMasker.mask("123456-78-901234")).isEqualTo("123456-**-**1234");
    }

    @Test
    void usesShortFormBelowTwelveDigits() {
        assertThat(AccountNumberMasker.mask("1234567890")).isEqualTo("123-***-890");
    }

    @Test
    void rejectsTooShortOrMissingNumbers() {
        assertThatThrownBy(() -> AccountNumberMasker.mask("123456")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountNumberMasker.mask(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
