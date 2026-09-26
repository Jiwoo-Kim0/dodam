package com.ssafy.dodam.global.repository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** 단일 백엔드 인스턴스에서 외부 이체와 목표 배정을 사용자 단위로 직렬화한다. */
@Component
public class SafeboxOperationLock {
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock forUser(Long userId) {
        return locks.computeIfAbsent(userId, ignored -> new ReentrantLock());
    }
}
