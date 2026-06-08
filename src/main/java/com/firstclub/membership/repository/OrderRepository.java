package com.firstclub.membership.repository;

import com.firstclub.membership.domain.entity.Order;
import com.firstclub.membership.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserAndOrderDateAfter(User user, LocalDateTime since);

    // Returns sum of finalTotal for a user in a window
    @Query("SELECT COALESCE(SUM(o.finalTotal), 0) FROM Order o WHERE o.user = :user AND o.orderDate > :since")
    BigDecimal sumFinalTotalByUserAndDateAfter(@Param("user") User user, @Param("since") LocalDateTime since);
}
