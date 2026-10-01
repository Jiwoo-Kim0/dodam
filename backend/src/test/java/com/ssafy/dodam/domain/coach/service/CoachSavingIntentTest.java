package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CoachSavingIntentTest {
    @Test
    void tenMoreIsAnIncrease() {
        assertThat(CoachSavingIntent.parse("그럼 월 10만원 더 저축하면 목표변화?")).isEqualTo(
                new CoachSavingIntent.Parsed(true, 100_000L));
    }

    @Test
    void statedFinalAmountWinsOverIncrease() {
        assertThat(CoachSavingIntent.parse("월 10만원 더 저축해서 월 50만원을 저축하게 되면?")).isEqualTo(
                new CoachSavingIntent.Parsed(false, 500_000L));
    }

    @Test
    void screenshotWordingKeepsTheStatedFinalAmount() {
        assertThat(CoachSavingIntent.parse("월 10만원을 더 저축해서 월 40만원을 저축했을 때 목표변화")).isEqualTo(
                new CoachSavingIntent.Parsed(false, 400_000L));
    }

    @Test
    void oldAmountIsNotMistakenForTheNewTotal() {
        assertThat(CoachSavingIntent.parse("원래 월 40만원을 저축하던 거에서 10만원 더 저축하면?")).isEqualTo(
                new CoachSavingIntent.Parsed(true, 100_000L));
    }

    @Test
    void explicitTotalStaysAbsolute() {
        assertThat(CoachSavingIntent.parse("저축을 월 50만원으로 올리면?")).isEqualTo(
                new CoachSavingIntent.Parsed(false, 500_000L));
    }

    @Test
    void increaseWithDoingVerbIsRecognized() {
        assertThat(CoachSavingIntent.parse("월 10만원 더 하고 싶어요")).isEqualTo(
                new CoachSavingIntent.Parsed(true, 100_000L));
    }

    @Test
    void multipleIncreaseAmountsAreRejected() {
        assertThatThrownBy(() -> CoachSavingIntent.parse("월 10만원 더 저축하고 월 20만원 더 저축하면?"))
                .isInstanceOf(CoachToolArgumentException.class);
    }
}
