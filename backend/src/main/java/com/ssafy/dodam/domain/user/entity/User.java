package com.ssafy.dodam.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * ERD `user` 테이블. `user`는 MySQL·H2 모두에서 예약어이므로 백틱으로 인용한다.
 * (Hibernate가 백틱을 Dialect별 인용문자로 변환한다.)
 *
 * <p>V39(629): 생년월일·성별은 사용자가 입력하고, 신용등급은 SSAFY 금융망 조회 결과를 서버가 기록한다.
 * V42(680): 실제 CB 조회값과 구분되는 도담 예상 신용점수와 산정 메타데이터를 추가한다.
 */
@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * 소셜 로그인만 사용하는 계정은 비밀번호가 없으므로 NULL을 허용한다(ERD 기준).
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    /**
     * SSAFY 금융망 2.7.4 「내 신용등급 조회」의 ratingName(A~E). 금융망은 숫자 신용점수를 주지 않는다.
     * 사용자 입력이 아니라 신용등급 조회·금융 데이터 동기화가 성공할 때 서버가 갱신한다.
     */
    @Column(name = "credit_rating", length = 5)
    private String creditRating;

    @Column(name = "credit_rating_updated_at")
    private LocalDateTime creditRatingUpdatedAt;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_score_source", length = 30)
    private CreditScoreSource creditScoreSource;

    @Column(name = "credit_score_model_version", length = 30)
    private String creditScoreModelVersion;

    @Column(name = "credit_score_updated_at")
    private LocalDateTime creditScoreUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(String email, String passwordHash, String name, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }

    /**
     * 이메일·비밀번호로 가입하는 자체 회원을 생성한다.
     */
    public static User createLocalUser(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name, UserRole.USER);
    }

    /**
     * 이메일·비밀번호로 가입하는 자체 회원을 생년월일·성별과 함께 생성한다. 두 값은 선택이라 null일 수 있다.
     */
    public static User createLocalUser(
            String email,
            String passwordHash,
            String name,
            LocalDate birthDate,
            Gender gender
    ) {
        User user = createLocalUser(email, passwordHash, name);
        user.birthDate = birthDate;
        user.gender = gender;
        return user;
    }

    /**
     * 소셜 로그인으로 가입하는 회원을 생성한다. 비밀번호가 없으므로 {@link #hasPassword()}가 false이고 이메일·비밀번호 로그인은 불가하다.
     */
    public static User createSocialUser(String email, String name) {
        return new User(email, null, name, UserRole.USER);
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }

    /**
     * 금융망 신용등급 조회 결과를 기록한다. 등급명이 비어 있으면 이전 값을 유지한다.
     */
    public void updateCreditRating(String ratingName, LocalDateTime at) {
        if (ratingName == null || ratingName.isBlank()) {
            return;
        }
        this.creditRating = ratingName.trim();
        this.creditRatingUpdatedAt = at;
    }

    /**
     * 신용점수와 출처를 함께 기록한다. 합성 점수를 KCB/NICE 조회값처럼 보이지 않게 메타데이터를 필수로 묶는다.
     */
    public void updateCreditScore(int score, CreditScoreSource source, String modelVersion, LocalDateTime at) {
        if (score < 1 || score > 1000) {
            throw new IllegalArgumentException("credit score must be between 1 and 1000");
        }
        if (source == null || at == null) {
            throw new IllegalArgumentException("credit score source and updated time are required");
        }
        if (source == CreditScoreSource.SYNTHETIC && (modelVersion == null || modelVersion.isBlank())) {
            throw new IllegalArgumentException("synthetic credit score model version is required");
        }
        this.creditScore = score;
        this.creditScoreSource = source;
        this.creditScoreModelVersion = modelVersion == null ? null : modelVersion.trim();
        this.creditScoreUpdatedAt = at;
    }

    /**
     * 기준일 현재 만 나이. 생년월일이 없으면 null.
     */
    public Integer ageAt(LocalDate today) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, today).getYears();
    }

    /**
     * 회원 탈퇴(soft delete). 이메일은 UNIQUE 제약상 그대로 남으므로 같은 이메일로 재가입은 불가하다.
     * 비밀번호 해시는 더 보관할 이유가 없어 지운다.
     */
    public void withdraw(LocalDateTime at) {
        this.deletedAt = at;
        this.passwordHash = null;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public String getCreditRating() {
        return creditRating;
    }

    public LocalDateTime getCreditRatingUpdatedAt() {
        return creditRatingUpdatedAt;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public CreditScoreSource getCreditScoreSource() {
        return creditScoreSource;
    }

    public String getCreditScoreModelVersion() {
        return creditScoreModelVersion;
    }

    public LocalDateTime getCreditScoreUpdatedAt() {
        return creditScoreUpdatedAt;
    }

    public UserRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
