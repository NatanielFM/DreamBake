package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Campaña de promociones por email (Clientes → Fidelización → Campañas).
 *
 * El contenido se guarda como TEXTO (título, texto, botón, foto de la galería): el administrador
 * no escribe HTML. El email se arma con la plantilla email/campana.html, que escapa todo lo que
 * escribió (así nadie puede meter código). bodyHtml es solo una copia del HTML que se generó al
 * empezar el envío, como constancia de lo que se mandó; nunca llega desde el navegador.
 */
@Entity
@Table(name = "campaigns")
@Getter
@Setter
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre interno (solo lo ve el administrador) y asunto del email
    private String name;
    private String subject;

    // ===== Contenido (texto plano) =====
    private String title;
    @Column(columnDefinition = "TEXT")
    private String bodyText;
    private String imageUrl;      // foto de la galería (/uploads/...) o vacío
    private String buttonText;
    private String buttonUrl;

    // Copia del HTML enviado (se guarda al empezar a enviar)
    @Column(columnDefinition = "MEDIUMTEXT")
    private String bodyHtml;

    // ===== A quién =====
    @Enumerated(EnumType.STRING)
    private CampaignAudience audience = CampaignAudience.ALL_CONSENTED;

    @ManyToOne
    @JoinColumn(name = "tag_id")
    private CustomerTag tag;          // solo para la audiencia TAG

    @ManyToOne
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;            // cupón opcional que se muestra en el email

    // ===== Estado y métricas =====
    @Enumerated(EnumType.STRING)
    private CampaignStatus status = CampaignStatus.DRAFT;

    private LocalDateTime scheduledAt;    // para cuándo está programada
    private LocalDateTime sentAt;         // cuándo empezó a enviarse
    private Integer totalRecipients = 0;
    private Integer sentCount = 0;
    private Integer failedCount = 0;

    // Aviso para el panel (ej: "Llegó al tope diario: sigue mañana") y cuándo puede reintentar
    private String statusNote;
    private LocalDateTime nextAttemptAt;

    private String createdBy;             // email del administrador que la creó
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
