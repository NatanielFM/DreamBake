package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Link para recuperar la contraseña ("Olvidé mi contraseña", paso 6.1).
 *
 * El link que va en el email lleva un código al azar (token). En la base NUNCA se guarda ese código:
 * se guarda su huella SHA-256 (tokenHash). Así, aunque alguien viera la tabla (o una copia de
 * seguridad), no podría armar el link: la huella no se puede "dar vuelta" para sacar el código.
 *
 * Cada link sirve una sola vez (used) y vence a los 30 minutos (expiresAt).
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_reset_tokens_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Huella SHA-256 del código, en hexadecimal (64 caracteres). Única: sirve para buscar el link.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // De quién es la cuenta. Al dar de baja al usuario se borran sus links (UserService / CustomerAccountService).
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Hasta cuándo sirve el link (30 minutos después de pedirlo)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Ya se usó para cambiar la contraseña (o se anuló porque la contraseña se cambió con otro link)
    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** ¿Todavía sirve? (no se usó y no venció) */
    public boolean isUsable(LocalDateTime now) {
        return !used && expiresAt != null && now.isBefore(expiresAt);
    }
}
