package com.ssafy.dodam.domain.notification.repository;

import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 알림 목록 필터(명세 22: type, isRead, fromDate, toDate). 조건이 null이면 그 필터를 걸지 않는다.
 * JPQL의 `:param is null or ...`는 null 바인딩 타입 문제가 있어 Specification으로 조립한다.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    /**
     * @param from 이 시각 이상(포함)
     * @param to   이 시각 미만(제외). 종료일을 포함하려면 다음 날 0시를 준다
     */
    public static Specification<Notification> of(Long userId, NotificationType type, Boolean read, LocalDateTime from, LocalDateTime to) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type.name()));
            }
            if (read != null) {
                predicates.add(builder.equal(root.get("read"), read));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
