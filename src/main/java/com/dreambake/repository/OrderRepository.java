package com.dreambake.repository;

import com.dreambake.entity.Order;
import com.dreambake.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT CASE WHEN i.isCustomCake = true THEN 'Torta Personalizada' WHEN i.product IS NOT NULL THEN i.product.name ELSE COALESCE(i.productType, 'Desconocido') END, COUNT(i) FROM OrderItem i WHERE i.order.status IN :statuses GROUP BY CASE WHEN i.isCustomCake = true THEN 'Torta Personalizada' WHEN i.product IS NOT NULL THEN i.product.name ELSE COALESCE(i.productType, 'Desconocido') END ORDER BY COUNT(i) DESC")
    List<Object[]> findTopProductsAggregation(@Param("statuses") List<OrderStatus> statuses);
}