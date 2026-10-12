package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // Se guarda como texto (VARCHAR) y no como ENUM de MySQL: así se pueden agregar tipos de email
    // nuevos sin tocar la base (ver config/EmailLogSchemaFix, que arregla las bases ya creadas).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 40)
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

    // Pedido al que se refiere el aviso (paso 1.5): sirve para no repetir el mismo aviso
    // para el mismo pedido y estado. Nulo en los emails que no son de un pedido.
    private Long orderId;
}
