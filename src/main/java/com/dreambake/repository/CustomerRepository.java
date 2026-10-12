package com.dreambake.repository;

import com.dreambake.entity.Customer;
import com.dreambake.entity.CustomerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByTag(CustomerTag tag);

    // Busca al cliente por teléfono (auto-creación de clientes); no falla si por error hay dos con el mismo teléfono
    Optional<Customer> findFirstByPhone(String phone);

    // Cliente dueño de un link de baja (/baja?token=...)
    Optional<Customer> findByUnsubscribeToken(String unsubscribeToken);

    // Clientes que aceptaron recibir promociones (los únicos a los que se les manda el saludo automático)
    List<Customer> findByMarketingConsentTrue();

    // Fichas de cliente con un email (sin importar mayúsculas). Puede haber más de una:
    // por ejemplo, si la dueña la cargó a mano y además se creó otra al confirmar un pedido.
    List<Customer> findByEmailIgnoreCase(String email);

    // Clientes con cumpleaños cargado (para la lista de próximos cumpleaños del panel)
    List<Customer> findByBirthDateIsNotNull();
}