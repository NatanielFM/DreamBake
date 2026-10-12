package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * "Ya vi este aviso" de la campanita de los paneles (paso 6.6).
 *
 * Los avisos NO se guardan: se calculan en el momento a partir de los datos (pedidos nuevos, stock bajo,
 * vencimientos…; ver NotificationService). Lo único que se guarda es qué avisos marcó como leídos cada
 * persona, identificados por una clave de texto (por ejemplo "order:45" o "stock:3:2.0").
 * Así lo leído se mantiene aunque la persona cambie de computadora o de celular.
 */
@Entity
@Table(name = "notification_reads",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_read", columnNames = {"user_id", "notification_key"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quién lo leyó. Al dar de baja al usuario se borran sus marcas (UserService / CustomerAccountService).
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "notification_key", nullable = false, length = 80)
    private String notificationKey;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    public NotificationRead(User user, String notificationKey, LocalDateTime readAt) {
        this.user = user;
        this.notificationKey = notificationKey;
        this.readAt = readAt;
    }
}
