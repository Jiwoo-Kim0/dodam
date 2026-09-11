package com.ssafy.dodam.domain.account.service;

/**
 * 계좌번호 마스킹. `account.account_no_masked`에 저장되고 모든 계좌 응답에 그대로 나간다.
 *
 * <p>seed(`DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql`)와 카드 출금계좌 표시가 쓰는 형식과 같다.
 * <ul>
 *   <li>12자리 이상(금융망 16자리 포함): 앞 6자리 + `-**-**` + 뒤 4자리 → `123456-**-**3456`</li>
 *   <li>12자리 미만: 앞 3자리 + `-***-` + 뒤 3자리 → `123-***-890`</li>
 * </ul>
 * 원문에 구분자가 섞여 있어도 숫자만 남기고 처리한다.
 */
public final class AccountNumberMasker {

    private static final int LONG_FORM_MIN_LENGTH = 12;

    private AccountNumberMasker() {
    }

    public static String mask(String accountNo) {
        if (accountNo == null) {
            throw new IllegalArgumentException("계좌번호가 비어 있습니다.");
        }
        String digits = accountNo.replaceAll("\\D", "");
        if (digits.length() < 7) {
            throw new IllegalArgumentException("마스킹할 수 없는 계좌번호 형식입니다.");
        }
        if (digits.length() >= LONG_FORM_MIN_LENGTH) {
            return digits.substring(0, 6) + "-**-**" + digits.substring(digits.length() - 4);
        }
        return digits.substring(0, 3) + "-***-" + digits.substring(digits.length() - 3);
    }
}
