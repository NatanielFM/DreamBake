package com.dreambake.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Ajustes del negocio que la dueña puede cambiar desde el panel (pestaña "Ajustes").
// Cada fila es un par "clave = valor", por ejemplo: whatsapp = 5493764159312.
// Así, para sumar un ajuste nuevo no hace falta cambiar la tabla: alcanza con una clave nueva.
@Entity
@Table(name = "business_settings")
@Getter
@Setter
@NoArgsConstructor
public class BusinessSetting {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;

    public BusinessSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
