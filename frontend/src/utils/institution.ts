import {
  FINANCIAL_INSTITUTIONS,
  type FinancialInstitution,
} from '@/mocks/financeConnection';

/**
 * 서버가 준 은행명으로 로고를 가진 금융기관을 찾는다.
 *
 * <p>서버 계좌 응답의 `bankName`(`신한은행`)과 목록에 든 표기(`신한은행`, `KB국민은행`)가
 * 늘 같지 않아 `은행`을 떼고 양쪽으로 포함 관계를 본다. 규칙이 한 곳에만 있어야
 * 같은 계좌가 화면마다 다른 로고로 보이는 일이 없다.
 *
 * <p>못 찾으면 `undefined`다. 로고 자리를 무엇으로 채울지는 화면마다 달라 호출부가 정한다.
 */
export function findInstitutionByBankName(
  bankName: string,
): FinancialInstitution | undefined {
  const normalized = bankName.replace('은행', '');

  return FINANCIAL_INSTITUTIONS.find(({ name }) => {
    const institutionName = name.replace('은행', '');
    return (
      bankName.includes(institutionName) || name.includes(normalized)
    );
  });
}
