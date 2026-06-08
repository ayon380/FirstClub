package com.firstclub.membership.service;

import com.google.common.util.concurrent.Striped;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

@Component
public class UserLockManager {
    // Guava Striped gives fixed number of locks, preventing unbounded growth
    // 256 stripes = good balance of granularity vs memory
    private final Striped<Lock> striped = Striped.lock(256);

    public Lock getLock(Long userId) {
        return striped.get(userId);
    }

    // Convenience: acquire, run, release
    public <T> T executeWithLock(Long userId, Supplier<T> action) {
        Lock lock = getLock(userId);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
