package com.dreambake.entity;

/**
 * Qué red social tiene vinculada el usuario: LOCAL = ninguna (solo email y contraseña).
 * Un usuario vinculado a GOOGLE o FACEBOOK también puede tener contraseña propia
 * (clientes que ya existían y personal que vinculó su cuenta desde el perfil).
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    FACEBOOK
}
