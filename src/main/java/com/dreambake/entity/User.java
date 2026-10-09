package com.dreambake.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @Email(message = "El email debe ser válido")
    @NotBlank(message = "El email no puede estar vacío")
    private String email;

    // WRITE_ONLY: la contraseña se puede RECIBIR en JSON (al crear o editar un pastelero),
    // pero nunca se ENVÍA en las respuestas de la API (ni siquiera cifrada).
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    @NotBlank(message = "La contraseña no puede estar vacía")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    @NotBlank(message = "El nombre no puede estar vacío")
    private String name;

    private String phone;
    private String roles; // Ejemplo: "ROLE_USER,ROLE_ADMIN"
    private Boolean enabled = true;

    // Por dónde entra el usuario. Los que ya existían quedan como LOCAL (el default de la columna
    // se encarga de rellenar las filas viejas cuando Hibernate agrega la columna).
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'LOCAL'")
    private AuthProvider authProvider = AuthProvider.LOCAL;

    // Id del usuario en Google o Facebook (null para los usuarios LOCAL)
    @JsonIgnore
    @Column(name = "provider_id")
    private String providerId;

    // Foto de perfil que informa la red social (opcional)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // Último ingreso exitoso (lo actualiza LoginTimestampListener)
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        enabled = true; // Por defecto el usuario está habilitado
    }

}
