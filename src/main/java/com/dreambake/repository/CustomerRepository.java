package com.dreambake.repository;

import com.dreambake.entity.Customer;
import com.dreambake.entity.CustomerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByTag(CustomerTag tag);

    // NUEVO MÉTODO PARA AUTO-CREACIÓN DE CLIENTES
    Optional<Customer> findByPhone(String phone);

    // Igual que findByPhone, pero no falla si por error hay dos clientes con el mismo teléfono
    Optional<Customer> findFirstByPhone(String phone);

    // Cliente dueño de un link de baja (/baja?token=...)
    Optional<Customer> findByUnsubscribeToken(String unsubscribeToken);

    // Clientes que aceptaron recibir promociones (los únicos a los que se les manda el saludo automático)
    List<Customer> findByMarketingConsentTrue();

    // Clientes con cumpleaños cargado (para la lista de próximos cumpleaños del panel)
    List<Customer> findByBirthDateIsNotNull();
}