package com.dreambake.service;

import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditLog;
import com.dreambake.entity.AuditModule;
import com.dreambake.entity.User;
import com.dreambake.repository.AuditLogRepository;
import com.dreambake.repository.UserRepository;
import com.dreambake.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Registro de auditoría (paso 3.1): anota quién hizo qué en el sistema.
 *
 * Uso desde cualquier parte del código:
 *
 *     auditService.log(AuditAction.STATUS_CHANGE, AuditModule.PEDIDOS, "Pedido", 45,
 *             "Confirmó el pedido #45 de María Gómez",
 *             Map.of("antes", Map.of("estado", "PENDING"), "despues", Map.of("estado", "CONFIRMED")));
 *
 * El usuario, su rol, la IP y el navegador se toman solos de la sesión y de la petición.
 *
 * CADENA DE HASHES: cada registro guarda una "huella digital" (hash SHA-256) calculada con sus datos
 * y con la huella del registro anterior. Si alguien cambia un registro viejo desde la base de datos,
 * su huella ya no coincide; y si lo borra, el siguiente queda apuntando a una huella que no existe.
 * verifyChain() recorre todo y encuentra el primer registro alterado.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** previousHash del primer registro de la cadena. */
    public static final String GENESIS = "GENESIS";

    // Formato fijo de la hora para el hash (con milisegundos). Tiene que ser siempre igual al
    // guardar y al verificar; por eso la hora se recorta a milisegundos antes de guardarla
    // (MySQL no guarda los nanosegundos que a veces trae Java, y el hash no coincidiría).
    private static final DateTimeFormatter HASH_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    // Nombres de campos que nunca se guardan en "details": se reemplaza el valor por [oculto]
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(password|contrase|token|secret|clave|apikey|api_key|credential).*");

    /*
     * BLOQUEO (por qué hace falta):
     * Para guardar un registro hay que (1) leer el hash del último registro y (2) guardar el nuevo
     * con ese hash como "anterior". Si dos acciones pasan al mismo tiempo (por ejemplo, la dueña
     * confirma un pedido mientras el pastelero descuenta stock), las dos podrían leer el MISMO último
     * hash y guardar dos registros que cuelgan del mismo anterior: la cadena se "bifurca" y
     * verifyChain() la daría por rota aunque nadie hizo trampa.
     * El bloqueo hace que los registros se escriban de a uno: el segundo espera a que el primero
     * termine (incluida su transacción) y recién ahí lee el último hash, que ya es el del primero.
     * Es "fair" (justo): los que esperan entran en orden de llegada.
     *
     * Este bloqueo vive en la memoria de la aplicación, que corre en un solo servidor (igual que
     * RateLimitFilter). Como red de seguridad, la columna previous_hash es UNIQUE en la base: si
     * alguna vez hubiera dos aplicaciones escribiendo, la base rechaza el registro que bifurcaría
     * la cadena y se reintenta con el último hash nuevo (ver write()).
     */
    private final ReentrantLock chainLock = new ReentrantLock(true);

    private final AuditLogRepository auditRepository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate newTransaction;

    public AuditService(AuditLogRepository auditRepository, UserRepository userRepository,
                        JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.jsonMapper = jsonMapper;
        // Transacción PROPIA (REQUIRES_NEW): el registro se guarda aunque la acción del usuario
        // esté dentro de otra transacción, y un problema con la auditoría no deshace esa acción.
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Resultado de verifyChain(). firstBrokenId es null si la cadena está íntegra. */
    public record ChainCheck(boolean intact, Long firstBrokenId, long checked, String message) {}

    /**
     * Registra una acción. Nunca tira error hacia afuera: si algo falla, queda anotado en el log de
     * la aplicación y la acción del usuario sigue normalmente. Devuelve el registro guardado, o null
     * si falló o si se va a guardar al confirmarse la transacción en curso.
     *
     * @param entityId id de lo afectado (número, código de cupón…); puede ser null
     * @param details  datos de antes y después (un Map, un objeto o un texto JSON); puede ser null
     */
    public AuditLog log(AuditAction action, AuditModule module, String entityType, Object entityId,
                        String description, Object details) {
        return logAs(null, action, module, entityType, entityId, description, details);
    }

    /**
     * Igual que log(), pero indicando a mano el email de quien actúa. Sirve cuando todavía no hay
     * nadie logueado, por ejemplo un intento de inicio de sesión fallido (paso 3.3).
     */
    public AuditLog logAs(String actorEmail, AuditAction action, AuditModule module, String entityType,
                          Object entityId, String description, Object details) {
        try {
            AuditLog entry = new AuditLog();
            // Hora recortada a milisegundos (ver HASH_TIME)
            entry.setTimestamp(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            fillActor(entry, actorEmail, action);
            fillRequest(entry);
            entry.setAction(action);
            entry.setModule(module);
            entry.setEntityType(cut(entityType, 60));
            entry.setEntityId(entityId == null ? null : cut(String.valueOf(entityId), 64));
            entry.setDescription(cut(description == null || description.isBlank() ? action + " en " + module : description, 500));
            entry.setDetails(toSafeJson(details));

            // Si la acción del usuario está dentro de una transacción (por ejemplo, confirmar un pedido),
            // el registro se escribe recién DESPUÉS de que esa transacción se confirma (afterCommit).
            // Así nunca queda anotado algo que al final se deshizo por un error. Los datos (quién,
            // desde dónde, el antes y el después) ya se tomaron arriba, en este mismo momento.
            if (TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            write(entry);
                        } catch (Exception e) {
                            log.error("No se pudo guardar el registro de auditoría ({}): {}", entry.getDescription(), e.getMessage());
                        }
                    }
                });
                return null; // se guarda al confirmarse la transacción
            }
            return write(entry);
        } catch (Exception e) {
            log.error("No se pudo guardar el registro de auditoría ({} / {}: {}): {}",
                    action, module, description, e.getMessage());
            return null;
        }
    }

    // Engancha el registro al final de la cadena y lo guarda (de a uno por vez: ver chainLock)
    private AuditLog write(AuditLog entry) {
        chainLock.lock();
        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    // El bloqueo envuelve la transacción completa: se suelta recién después del commit,
                    // así el próximo registro ya ve a este como "el último".
                    return newTransaction.execute(status -> {
                        String previous = auditRepository.findTopByOrderByIdDesc()
                                .map(AuditLog::getHash)
                                .orElse(GENESIS);
                        entry.setPreviousHash(previous);
                        entry.setHash(computeHash(entry));
                        return auditRepository.save(entry);
                    });
                } catch (DataIntegrityViolationException e) {
                    // Otro proceso escribió en el medio (previous_hash repetido): se reintenta
                    if (attempt >= 3) throw e;
                }
            }
        } finally {
            chainLock.unlock();
        }
    }

    /**
     * Calcula el hash SHA-256 de un registro. Entran, en este orden: hash anterior, fecha y hora,
     * email de quien actuó, acción, módulo, tipo e id de lo afectado, descripción y detalles.
     *
     * Cada dato va precedido de su largo ("5:hola"), y los vacíos se marcan con "-". Así no hay
     * ambigüedad: sin el largo, "ab" + "c" y "a" + "bc" darían el mismo texto y el mismo hash, y
     * se podría pasar texto de un campo a otro sin que la cadena lo note.
     */
    public static String computeHash(AuditLog e) {
        StringBuilder text = new StringBuilder();
        for (String part : new String[] {
                e.getPreviousHash(),
                e.getTimestamp() == null ? null : e.getTimestamp().format(HASH_TIME),
                e.getActorEmail(),
                e.getAction() == null ? null : e.getAction().name(),
                e.getModule() == null ? null : e.getModule().name(),
                e.getEntityType(),
                e.getEntityId(),
                e.getDescription(),
                e.getDetails() }) {
            if (part == null) {
                text.append('-');
            } else {
                text.append(part.length()).append(':').append(part);
            }
            text.append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest); // 64 caracteres hexadecimales
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex); // no pasa: Java siempre lo trae
        }
    }

    /**
     * Revisa la cadena completa, del primer registro al último, de a tandas de 500.
     * En cada registro comprueba dos cosas:
     *  1. que su "hash anterior" sea el hash del registro previo (si no, falta o se cambió uno), y
     *  2. que su hash coincida con el que se calcula ahora con sus datos (si no, lo modificaron).
     * Devuelve si está íntegra o el id del primer registro alterado.
     *
     * Límite (para la tesis): si se borra el ÚLTIMO registro, los anteriores siguen encadenados y la
     * cadena sola no lo nota. Eso lo cubre la protección en la base de datos (paso 3.2).
     */
    public ChainCheck verifyChain() {
        return newTransaction.execute(status -> {
            String expectedPrevious = GENESIS;
            long lastId = 0;
            long checked = 0;
            while (true) {
                List<AuditLog> batch = auditRepository.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, 500));
                if (batch.isEmpty()) break;
                for (AuditLog entry : batch) {
                    checked++;
                    if (!expectedPrevious.equals(entry.getPreviousHash())) {
                        return new ChainCheck(false, entry.getId(), checked,
                                "El registro #" + entry.getId() + " no apunta al anterior: falta un registro o se modificó.");
                    }
                    if (!computeHash(entry).equals(entry.getHash())) {
                        return new ChainCheck(false, entry.getId(), checked,
                                "El registro #" + entry.getId() + " fue modificado después de guardarse.");
                    }
                    expectedPrevious = entry.getHash();
                    lastId = entry.getId();
                }
            }
            return new ChainCheck(true, null, checked, checked == 0
                    ? "Todavía no hay registros."
                    : "La cadena está íntegra: " + checked + " registros verificados.");
        });
    }

    // Quién actúa: el usuario logueado, o "Cliente web" (tienda pública, sin sesión) / "Sistema" (tareas automáticas)
    private void fillActor(AuditLog entry, String actorEmail, AuditAction action) {
        // Login fallido: se anota el email que se intentó, pero NO a nombre del dueño de esa cuenta
        // (quien lo intentó no demostró ser él: puede ser otra persona probando contraseñas)
        if (action == AuditAction.LOGIN_FAILED) {
            entry.setActorEmail(cut(actorEmail, 150));
            entry.setActorName("Sin sesión (intento de ingreso)");
            entry.setActorRole("PUBLICO");
            return;
        }
        String email = actorEmail;
        if (email == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                email = auth.getName();
            }
        }
        if (email == null) {
            boolean inRequest = currentRequest() != null;
            entry.setActorName(inRequest ? "Cliente web" : "Sistema (tarea automática)");
            entry.setActorRole(inRequest ? "PUBLICO" : "SISTEMA");
            return;
        }
        entry.setActorEmail(cut(email, 150));
        User user = userRepository.findByEmail(email).orElse(null);
        entry.setActorName(cut(user != null ? user.getName() : email, 150));
        entry.setActorRole(cut(user != null ? user.getRoles() : "DESCONOCIDO", 40));
    }

    // IP y navegador de la petición en curso (si la hay: las tareas automáticas no tienen)
    private void fillRequest(AuditLog entry) {
        HttpServletRequest request = currentRequest();
        if (request == null) return;
        entry.setIpAddress(cut(ClientIpResolver.resolve(request), 64));
        entry.setUserAgent(cut(request.getHeader("User-Agent"), 255));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    // Convierte los detalles a JSON reemplazando contraseñas, tokens y claves por "[oculto]"
    private String toSafeJson(Object details) {
        if (details == null) return null;
        Object value = details;
        if (details instanceof CharSequence text) {
            try {
                value = jsonMapper.readValue(text.toString(), Object.class);
            } catch (Exception notJson) {
                value = Map.of("texto", text.toString());
            }
        } else if (!(details instanceof Map) && !(details instanceof List)) {
            value = jsonMapper.convertValue(details, Object.class); // un objeto cualquiera → Map
        }
        return jsonMapper.writeValueAsString(hideSensitive(value));
    }

    private Object hideSensitive(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                // Contraseñas y claves: "[oculto]". Teléfono, email, dirección y cumpleaños: tapados
                // (AuditText.maskPersonal), porque el registro es inmutable y no se podrían borrar después
                copy.put(key, SENSITIVE.matcher(key).matches() ? "[oculto]"
                        : com.dreambake.audit.AuditText.maskPersonal(key, hideSensitive(v)));
            });
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(v -> copy.add(hideSensitive(v)));
            return copy;
        }
        return value;
    }

    private static String cut(String text, int max) {
        if (text == null) return null;
        return text.length() > max ? text.substring(0, max) : text;
    }
}
