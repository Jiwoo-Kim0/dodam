package com.ssafy.dodam.domain.notification.repository;

import com.ssafy.dodam.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /** 본인 소유이고 삭제되지 않은 알림. 타인 것·없는 것·삭제된 것은 구분하지 않고 비어 있다(404). */
    Optional<Notification> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<Notification> findByUserIdAndDedupeKey(Long userId, String dedupeKey);

    long countByUserIdAndReadFalseAndDeletedAtIsNull(Long userId);

    /** 읽지 않은 알림 전부를 읽음 처리한다. 갱신 건수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :now
             where n.userId = :userId and n.read = false and n.deletedAt is null
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
