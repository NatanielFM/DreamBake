package com.dreambake.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Recordatorio de una persona del equipo (paso 5.2). Ejemplo: "Hacer las decoraciones de la torta
 * de María" para el lunes a las 10:00. Lo puede crear la persona desde el panel o Bakey (paso 5.4).
 *
 * Cada recordatorio es de un usuario (owner): lo ve él y también el administrador. Una hora antes
 * se le manda un email (si tiene) y, cuando llega la hora, el panel muestra un aviso.
 */
@Entity
@Table(name = "reminders", indexes = {
        @Index(name = "idx_reminders_owner_due", columnList = "owner_id, due_at"),
        @Index(name = "idx_reminders_due", columnList = "due_at")
})
@Getter
@Setter
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Qué hay que hacer (corto, se ve en la lista)
    @Column(nullable = false, length = 120)
    private String title;

    // Detalle opcional
    @Column(length = 1000)
    private String notes;

    // Cuándo hay que hacerlo (fecha y hora de Argentina)
    @Column(nullable = false)
    private LocalDateTime dueAt;

    // De quién es. @JsonIgnore: la API devuelve un resumen propio (ReminderView), nunca el usuario entero.
    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Pedido relacionado (opcional). Se guarda solo el número: si el pedido se borra, el recordatorio queda.
    private Long relatedOrderId;

    private Boolean done;

    private LocalDateTime doneAt;

    private LocalDateTime createdAt;

    // true si lo creó Bakey (paso 5.4); false si lo cargó la persona desde el panel
    private Boolean createdByAssistant;

    // Cuándo se mandó el email de "falta 1 hora" (vacío = todavía no). Evita mandarlo dos veces.
    private LocalDateTime emailSentAt;

    // Valores por defecto al guardar por primera vez. Se completan acá (y no solo con "= false" arriba)
    // porque Jackson 3 puede armar el objeto dejando en null lo que no vino (lección del paso 3.3).
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (done == null) done = false;
        if (createdByAssistant == null) createdByAssistant = false;
    }

    public boolean isDone() {
        return Boolean.TRUE.equals(done);
    }
}
