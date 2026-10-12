package com.dreambake.ai.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Los parámetros que mandó el modelo, con controles. El modelo puede mandar cualquier cosa (una
 * fecha mal escrita, 5000 días…): acá se revisa y, si algo está mal, se corta con un mensaje en
 * español que vuelve al modelo para que lo corrija o se lo explique a la persona.
 */
public class ToolArgs {

    /** Parámetro inválido: el mensaje se le devuelve al modelo como {"error": "…"}. */
    public static class InvalidArgException extends RuntimeException {
        public InvalidArgException(String message) { super(message); }
    }

    private final Map<String, Object> raw;

    public ToolArgs(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public Map<String, Object> raw() {
        return raw;
    }

    /** Fecha en formato AAAA-MM-DD (obligatoria). */
    public LocalDate date(String name) {
        Object value = raw.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new InvalidArgException("Falta el parámetro " + name + " (fecha AAAA-MM-DD).");
        }
        try {
            return LocalDate.parse(String.valueOf(value).trim());
        } catch (DateTimeParseException e) {
            throw new InvalidArgException("La fecha " + name + " tiene que tener el formato AAAA-MM-DD.");
        }
    }

    /** Número entero entre min y max; si no viene, el valor por defecto. */
    public int integer(String name, int defaultValue, int min, int max) {
        Object value = raw.get(name);
        if (value == null || String.valueOf(value).isBlank()) return defaultValue;
        int number;
        try {
            number = value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new InvalidArgException("El parámetro " + name + " tiene que ser un número.");
        }
        if (number < min || number > max) {
            throw new InvalidArgException("El parámetro " + name + " tiene que estar entre " + min + " y " + max + ".");
        }
        return number;
    }

    /** Número largo obligatorio (por ejemplo el número de pedido). */
    public long id(String name) {
        Object value = raw.get(name);
        try {
            return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new InvalidArgException("Falta el parámetro " + name + " (un número).");
        }
    }

    /** Texto recortado a max letras; vacío si no viene. */
    public String text(String name, int max) {
        Object value = raw.get(name);
        if (value == null) return "";
        String text = String.valueOf(value).trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    public boolean bool(String name) {
        Object value = raw.get(name);
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }

    /** Revisa un rango de fechas: "hasta" no antes que "desde" y como mucho maxDays días. */
    public static void checkRange(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw new InvalidArgException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (from.plusDays(maxDays).isBefore(to)) {
            throw new InvalidArgException("El rango es muy largo: como mucho " + maxDays + " días.");
        }
    }
}
