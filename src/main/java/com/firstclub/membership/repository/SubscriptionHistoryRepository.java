package com.firstclub.membership.repository;

import com.firstclub.membership.domain.entity.Subscription;
import com.firstclub.membership.domain.entity.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {
    List<SubscriptionHistory> findBySubscriptionOrderByChangedAtDesc(Subscription subscription);
}
