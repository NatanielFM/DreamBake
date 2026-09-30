package com.dreambake.repository;

import com.dreambake.entity.Order;
import com.dreambake.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT p.name, COUNT(i) FROM OrderItem i JOIN Product p ON i.productType = p.name WHERE i.order.status IN :statuses GROUP BY p.name ORDER BY COUNT(i) DESC")
    List<Object[]> findTopProductsAggregation(@Param("statuses") List<OrderStatus> statuses);
}