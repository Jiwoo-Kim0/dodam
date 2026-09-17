package com.ssafy.dodam.domain.user.dto;

import com.ssafy.dodam.domain.user.entity.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 수정 허용 프로필 필드. 이메일은 금융망 계정 키라 바꿀 수 없고, 비밀번호 변경은 별도 API로 다룬다.
 * 모두 선택이며 null인 항목은 변경하지 않는다. 신용등급은 금융망 조회 결과라 여기서 받지 않는다(629).
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100, message = "이름은 1자 이상 100자 이하여야 합니다.")
        @Pattern(regexp = "^(?!\\s*$).+", message = "이름은 공백만으로 이루어질 수 없습니다.")
        String name,

        @Past(message = "생년월일은 오늘 이전 날짜여야 합니다.")
        LocalDate birthDate,

        Gender gender
) {

    public boolean isEmpty() {
        return name == null && birthDate == null && gender == null;
    }
}
