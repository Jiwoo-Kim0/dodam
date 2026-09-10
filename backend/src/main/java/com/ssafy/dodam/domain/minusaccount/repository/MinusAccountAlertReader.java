package com.ssafy.dodam.domain.minusaccount.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 마이너스통장 알림 조회.
 *
 * <p>`notification`은 S15P21D105-458 소관이므로 읽기 전용 필터 뷰만 제공한다. 알림 생성,
 * 읽음 처리, 삭제는 `22 Notification` API가 소유한다.
 */
public interface MinusAccountAlertReader {

    Page<MinusAccountAlertRow> read(
            Long userId,
            Long accountId,
            boolean unreadOnly,
            Pageable pageable
    );
}
