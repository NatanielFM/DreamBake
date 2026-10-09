package com.dreambake.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Averigua la IP real del cliente que hizo el pedido HTTP.
 *
 * El problema: la app se publica con Tailscale Funnel, que recibe las visitas de internet
 * y se las pasa a la app desde la misma PC (127.0.0.1). Para la app, entonces, TODAS las
 * visitas vienen de 127.0.0.1, y los límites por IP (RateLimitFilter, bloqueo del login)
 * serían para todos los clientes juntos: un día con muchos pedidos bloquearía a clientes reales.
 *
 * El proxy de Tailscale anota la IP verdadera del visitante en el encabezado X-Forwarded-For.
 * Pero ese encabezado lo puede escribir cualquiera desde su navegador o un programa, así que:
 *
 *  1. Solo se le cree si la conexión viene de la misma PC (127.0.0.1 o ::1), es decir,
 *     del proxy de Tailscale. Si alguien se conecta directo desde afuera, se usa la IP de
 *     la conexión y el encabezado se ignora (no puede inventarse otra IP para saltear el límite).
 *  2. Se toma la ÚLTIMA IP de la lista, no la primera. Si el visitante manda su propio
 *     "X-Forwarded-For: 1.2.3.4", el proxy agrega la IP real al final:
 *     "1.2.3.4, 181.238.22.99". La primera la inventó el visitante; la última la puso el proxy.
 *  3. Si el encabezado no está o no parece una IP, se usa la IP de la conexión.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isLoopback(remote)) {
            return remote;   // conexión directa: no se confía en ningún encabezado
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;   // visita desde la misma PC (por ejemplo, http://localhost:8080)
        }
        String[] parts = forwarded.split(",");
        String last = parts[parts.length - 1].trim();
        return looksLikeIp(last) ? last : remote;
    }

    // ¿La conexión viene de la misma PC? (127.x.x.x o ::1, escrito de cualquier forma)
    private static boolean isLoopback(String ip) {
        if (!looksLikeIp(ip)) return false;
        try {
            // Con una IP escrita en números no se consulta ningún DNS: solo se interpreta el texto
            return InetAddress.getByName(ip).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    // Solo números, letras hexadecimales, puntos y dos puntos (IPv4 o IPv6), con un largo razonable.
    // Evita que un texto cualquiera se use como "IP" en los contadores.
    private static boolean looksLikeIp(String value) {
        return value != null && !value.isEmpty() && value.length() <= 45
                && value.matches("[0-9a-fA-F.:]+");
    }
}
