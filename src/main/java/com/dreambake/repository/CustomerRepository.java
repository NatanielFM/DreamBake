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
}