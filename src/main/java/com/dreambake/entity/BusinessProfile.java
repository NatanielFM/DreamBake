package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Datos del negocio que se editan desde el panel (Ajustes → Configuración del negocio, paso 6.4).
 *
 * Tiene UNA SOLA fila (id = 1): la crea BusinessProfileService la primera vez con los valores que había
 * antes (app.business.* y el WhatsApp de la tabla business_settings).
 *
 * ¿Por qué no se llama BusinessSettings? Porque ya existe BusinessSetting (tabla business_settings):
 * pares clave = valor para ajustes técnicos (cuenta de correo cifrada, cumpleaños, campañas…).
 * Esta tabla, en cambio, guarda los datos "de vidriera" del negocio, cada uno en su columna.
 */
@Entity
@Table(name = "business_profile")
@Getter
@Setter
@NoArgsConstructor
public class BusinessProfile {

    public static final long SINGLE_ID = 1L;

    @Id
    private Long id = SINGLE_ID;

    @Column(nullable = false, length = 80)
    private String name;

    // Solo dígitos, formato internacional argentino: 549 + código de área + número (13 dígitos)
    @Column(nullable = false, length = 20)
    private String whatsapp;

    @Column(length = 150)
    private String email;

    @Column(length = 200)
    private String address;

    // Usuario de Instagram sin la @ (ej: dulcessuenos_posadas)
    @Column(length = 40)
    private String instagram;

    // Usuario o nombre de la página de Facebook (lo que va después de facebook.com/)
    @Column(length = 100)
    private String facebook;

    // Horario de atención en texto libre (ej: "Lunes a sábado de 9 a 19 h")
    @Column(name = "opening_hours", length = 200)
    private String openingHours;

    // Días de anticipación mínima para hacer un pedido en la tienda
    @Column(name = "min_lead_days", nullable = false)
    private Integer minLeadDays = 3;

    // Porcentaje de seña sugerido (se muestra en los términos; lo cobra la dueña por WhatsApp)
    @Column(name = "deposit_percent", nullable = false)
    private Integer depositPercent = 50;

    // Mensaje opcional que se muestra arriba en toda la tienda (ej: "¡Llegaron las tortas de Halloween!")
    @Column(name = "store_banner", length = 200)
    private String storeBanner;

    // Pausar los pedidos online (vacaciones): la tienda lo avisa y el servidor rechaza pedidos nuevos
    @Column(name = "orders_paused", nullable = false)
    private Boolean ordersPaused = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Datos armados para mostrar (no son columnas: JPA solo guarda los campos de arriba) =====

    /** WhatsApp para leer: "+54 9 376 415-9312" a partir de 5493764159312. */
    public String getWhatsappDisplay() {
        if (whatsapp == null || !whatsapp.matches("549[0-9]{10}")) return whatsapp == null ? "" : "+" + whatsapp;
        String local = whatsapp.substring(3);
        return "+54 9 " + local.substring(0, 3) + " " + local.substring(3, 6) + "-" + local.substring(6);
    }

    /** Link al perfil de Instagram (null si no hay usuario cargado). */
    public String getInstagramUrl() {
        return instagram == null || instagram.isBlank() ? null : "https://www.instagram.com/" + instagram + "/";
    }

    /** Link a la página de Facebook (null si no hay página cargada). */
    public String getFacebookUrl() {
        return facebook == null || facebook.isBlank() ? null : "https://www.facebook.com/" + facebook;
    }
}
