package com.firstclub.membership.service;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class UserLockManagerTests {

    private final UserLockManager userLockManager = new UserLockManager();

    @Test
    void executeWithLock_SameUser_BlocksConcurrentExecution() throws Exception {
        Long userId = 1L;
        CountDownLatch thread1Started = new CountDownLatch(1);
        CountDownLatch thread2Completed = new CountDownLatch(1);
        CountDownLatch releaseThread1 = new CountDownLatch(1);
        AtomicBoolean thread2FinishedAfterRelease = new AtomicBoolean(false);

        Thread t1 = new Thread(() -> {
            userLockManager.executeWithLock(userId, () -> {
                thread1Started.countDown();
                try {
                    releaseThread1.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        Thread t2 = new Thread(() -> {
            try {
                thread1Started.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            userLockManager.executeWithLock(userId, () -> {
                thread2FinishedAfterRelease.set(true);
                thread2Completed.countDown();
                return null;
            });
        });

        t1.start();
        t2.start();

        assertThat(thread1Started.await(2, TimeUnit.SECONDS)).isTrue();
        
        boolean t2DoneBeforeRelease = thread2Completed.await(500, TimeUnit.MILLISECONDS);
        assertThat(t2DoneBeforeRelease).isFalse();

        releaseThread1.countDown();

        assertThat(thread2Completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(thread2FinishedAfterRelease.get()).isTrue();

        t1.join();
        t2.join();
    }

    @Test
    void executeWithLock_DifferentUsers_AllowsConcurrentExecution() throws Exception {
        Long user1 = 1L;
        Long user2 = 2L;

        CountDownLatch user1InLock = new CountDownLatch(1);
        CountDownLatch user2InLock = new CountDownLatch(1);
        CountDownLatch releaseLocks = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            userLockManager.executeWithLock(user1, () -> {
                user1InLock.countDown();
                try {
                    releaseLocks.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        Thread t2 = new Thread(() -> {
            userLockManager.executeWithLock(user2, () -> {
                user2InLock.countDown();
                try {
                    releaseLocks.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        t1.start();
        t2.start();

        assertThat(user1InLock.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(user2InLock.await(2, TimeUnit.SECONDS)).isTrue();

        releaseLocks.countDown();
        t1.join();
        t2.join();
    }
}
