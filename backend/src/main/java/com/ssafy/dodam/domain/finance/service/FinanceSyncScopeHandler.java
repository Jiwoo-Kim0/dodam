package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;

/**
 * 도메인별 금융 데이터 동기화 확장점.
 *
 * 각 도메인(Account, Card, Loan, Saving)은 자기 테이블을 소유하므로 SSAFY 금융망에서 읽어 로컬 DB에 반영하는
 * 구현도 해당 도메인 패키지에 둔다. 이 인터페이스를 구현한 빈을 등록하면 {@link FinanceSyncService}가 scope에 맞춰 호출한다.
 * 등록된 구현이 없는 scope는 SKIPPED로 보고한다.
 *
 * 구현 규칙
 * - 트랜잭션 바깥에서 호출된다. 금융망 응답을 기다리며 DB 커넥션을 잡지 않도록 저장 구간만 스스로 트랜잭션을 연다.
 * - 예외를 던지면 그 scope만 FAILED로 기록되고 다른 scope는 계속 진행한다.
 * - 같은 scope의 구현이 둘 이상이면 기동에 실패한다.
 */
public interface FinanceSyncScopeHandler {

    FinanceSyncScope scope();

    /**
     * @param userId  동기화 대상 사용자
     * @param userKey 금융망 userKey 원문. 로그·응답·DB에 남기지 않는다.
     */
    FinanceSyncScopeResult sync(Long userId, String userKey);
}
