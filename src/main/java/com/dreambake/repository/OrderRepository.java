package com.dreambake.repository;

import com.dreambake.entity.Customer;
import com.dreambake.entity.Order;
import com.dreambake.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("SELECT CASE WHEN i.isCustomCake = true THEN 'Torta Personalizada' WHEN i.product IS NOT NULL THEN i.product.name ELSE COALESCE(i.productType, 'Desconocido') END, COUNT(i) FROM OrderItem i WHERE i.order.status IN :statuses GROUP BY CASE WHEN i.isCustomCake = true THEN 'Torta Personalizada' WHEN i.product IS NOT NULL THEN i.product.name ELSE COALESCE(i.productType, 'Desconocido') END ORDER BY COUNT(i) DESC")
    List<Object[]> findTopProductsAggregation(@Param("statuses") List<OrderStatus> statuses);

    // Historial de un cliente: los pedidos unidos a él y también los viejos que tienen su
    // mismo teléfono (de antes de que existiera la relación Pedido–Cliente)
    List<Order> findByCustomerOrCustomerPhoneOrderByDeliveryDateDesc(Customer customer, String customerPhone);

    // Para borrar un cliente: primero se desvinculan sus pedidos (los pedidos no se borran)
    List<Order> findByCustomer(Customer customer);

    // Pedidos que se entregan entre dos fechas (inclusive) y están en alguno de esos estados
    // (los usa la tarea de las 8:00: recordatorios y resumen de entregas)
    List<Order> findByDeliveryDateBetweenAndStatusIn(LocalDate from, LocalDate to, Collection<OrderStatus> statuses);
}