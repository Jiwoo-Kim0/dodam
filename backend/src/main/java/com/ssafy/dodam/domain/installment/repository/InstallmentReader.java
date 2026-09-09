package com.ssafy.dodam.domain.installment.repository;

import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InstallmentReader {
    boolean ownsCard(Long userId, Long cardId);

    Page<InstallmentRow> readPage(Long userId, Long cardId, InstallmentStatus status, Pageable pageable);

    Optional<InstallmentRow> readDetail(Long userId, Long installmentId);

    List<InstallmentRow> readOutstanding(Long userId);
}
