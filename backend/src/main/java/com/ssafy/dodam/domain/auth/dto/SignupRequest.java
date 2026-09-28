package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.user.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 생년월일·성별은 선택이다(629). 생략하면 NULL로 가입되고 PATCH /users/me로 뒤에 채울 수 있다.
 * 성별은 {@link Gender} 값(MALE, FEMALE)만 받고, 그 밖의 문자열은 본문 해석 실패로 400 INVALID_REQUEST다.
 */
public record SignupRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        // BCrypt는 72바이트까지만 반영하므로 상한을 72로 둔다.
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$",
                message = "비밀번호는 공백 없이 영문과 숫자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @Past(message = "생년월일은 오늘 이전 날짜여야 합니다.")
        LocalDate birthDate,

        Gender gender
) {
}
