package com.dreambake.util;

/**
 * Limpia los textos que llegan desde la tienda pública (pedidos sin iniciar sesión).
 *
 * El panel de administración muestra esos textos (nombre, teléfono, detalles del
 * pedido) armando HTML. Si alguien escribiera código HTML o JavaScript como
 * "nombre", se ejecutaría en la computadora del administrador (ataque XSS).
 * Por eso se reemplazan los caracteres que permiten armar etiquetas o salir de
 * un atributo, se quitan caracteres de control y se limita el largo.
 */
public final class InputSanitizer {

    private InputSanitizer() {}

    public static String clean(String value, int maxLength) {
        if (value == null) return null;
        String text = value
                .replace('<', '‹')
                .replace('>', '›')
                .replace('"', '”')
                .replace('`', '\'')
                // caracteres de control invisibles (se conservan saltos de línea y tabulaciones)
                .replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "")
                .trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
