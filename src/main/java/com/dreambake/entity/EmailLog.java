package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Historial de emails: una fila por cada intento de envío (salga bien o mal).
 * Sirve para ver en la ficha del cliente qué se le mandó, y para saber por qué falló un envío.
 */
@Entity
@Table(name = "email_logs")
@Getter
@Setter
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cliente al que se le mandó (puede ser nulo: por ejemplo, el email de prueba al administrador)
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    private EmailType type;

    private String subject;

    @Enumerated(EnumType.STRING)
    private EmailStatus status;

    // Por qué falló (vacío si salió bien)
    @Column(length = 500)
    private String errorMessage;

    private LocalDateTime sentAt;

    // Campaña de promociones a la que pertenece (paso 1.4). Nulo en los demás emails.
    private Long campaignId;
}
