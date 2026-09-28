package com.ssafy.dodam.domain.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

class TransactionMemoPolicyTest {

    private final TransactionMemoPolicy policy = new TransactionMemoPolicy();

    @Test
    void nullAndBlankBecomeNull() {
        assertThat(policy.normalize(null)).isNull();
        assertThat(policy.normalize("")).isNull();
        assertThat(policy.normalize("   \n\t ")).isNull();
    }

    @Test
    void stripsSurroundingWhitespaceButKeepsInnerLineBreaks() {
        assertThat(policy.normalize("  첫 줄\n둘째 줄\t끝  ")).isEqualTo("첫 줄\n둘째 줄\t끝");
    }

    @Test
    void allowsEmojiAndSymbols() {
        assertThat(policy.normalize("커피 ☕ 10% 할인 😀")).isEqualTo("커피 ☕ 10% 할인 😀");
    }

    @Test
    void allowsExactlyMaxLengthAfterStrip() {
        String memo = "가".repeat(TransactionMemoPolicy.MAX_LENGTH);
        assertThat(policy.normalize("  " + memo + "  ")).isEqualTo(memo);
    }

    @Test
    void rejectsOverMaxLength() {
        String memo = "가".repeat(TransactionMemoPolicy.MAX_LENGTH + 1);
        assertThatThrownBy(() -> policy.normalize(memo))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("메모는 500자 이하여야 합니다.");
    }

    @Test
    void rejectsControlAndFormatCharacters() {
        String[] forbidden = {
                "메모" + (char) 0x00 + "끝",   // NUL
                "메모" + (char) 0x01 + "끝",   // SOH
                "메모" + (char) 0x1B + "[31m끝", // ESC (ANSI 색상 시퀀스)
                "메모" + (char) 0x7F + "끝",   // DEL
                "메모" + (char) 0x200B + "끝", // ZERO WIDTH SPACE (FORMAT)
                "메모" + (char) 0xFEFF + "끝"  // BOM (FORMAT)
        };
        for (String memo : forbidden) {
            assertThatThrownBy(() -> policy.normalize(memo))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("메모에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
    }
}
