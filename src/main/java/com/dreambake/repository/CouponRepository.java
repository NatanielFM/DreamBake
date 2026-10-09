package com.dreambake.repository;

import com.dreambake.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    // Cupón más nuevo con esa descripción (los cupones de cumpleaños se identifican por ella, ver BirthdayService)
    Optional<Coupon> findFirstByDescriptionOrderByIdDesc(String description);

    // Descuenta un uso (si el cupón no está vencido) en UNA sola consulta a la base, y solo si todavía le quedan usos.
    // La base de datos ejecuta los UPDATE sobre la misma fila de a uno: si dos clientes usan
    // el último uso al mismo tiempo, el segundo ya ve usageLimit = 0 y no cambia nada.
    // Devuelve cuántas filas cambió: 1 = se pudo usar, 0 = se agotó (o se desactivó) justo antes.
    // "active" se calcula primero, con el valor de usageLimit de antes de restar:
    // si quedaba 1 uso, el cupón queda inactivo.
    @Modifying
    @Query("UPDATE Coupon c SET c.active = CASE WHEN c.usageLimit <= 1 THEN false ELSE true END, "
            + "c.usageLimit = c.usageLimit - 1 "
            + "WHERE c.id = :id AND c.active = true AND c.usageLimit > 0 "
            + "AND (c.expirationDate IS NULL OR c.expirationDate >= CURRENT_DATE)")
    int consumeOneUse(@Param("id") Long id);
}
