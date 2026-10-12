# 5. Seguridad

[← Volver al índice](../DOCUMENTACION-TECNICA.md)

Este capítulo describe **lo que está implementado**. Lo que falta (auditoría de seguridad completa, pruebas automáticas, endurecimiento para producción) está en [Limitaciones](06-limitaciones-y-mejoras.md). Casi todo se configura en `config/SecurityConfig.java`.

## 5.1 Roles

Los roles son *authorities* sin prefijo guardadas como texto en `users.roles`:

| Rol | Quién es | Después de iniciar sesión va a |
|---|---|---|
| `ADMIN` | La dueña (administradora) | `/admin` |
| `PASTELERO` | El personal de producción | `/baker` |
| (sin rol de personal) | Cliente registrado | `/` (la tienda) |
| — | Visitante sin sesión | — |

No hay un administrador precargado: el primero se crea registrándose y cambiando su rol en MySQL (ver [Instalación](07-instalacion.md)). Desde Panel → Equipo, el administrador da de alta y de baja a los pasteleros. Una cuenta con `enabled = false` no puede entrar.

## 5.2 Quién puede entrar a cada ruta

Spring Security revisa las reglas **en orden y usa la primera que coincide**. Por eso las reglas puntuales (por ejemplo, "borrar un pedido: solo ADMIN") van antes que las generales.

| Ruta | Acceso |
|---|---|
| `/`, `/products`, `/builder`, `/cart`, `/register`, `/login`, `/baja`, `/seguimiento`, `/recuperar/**`, `/login/completar-email/**`, `/privacidad`, `/terminos`, `/eliminar-datos`, `/css/**`, `/js/**`, `/img/**`, `/uploads/**`, `/favicon.ico`, `/site.webmanifest`, `/robots.txt`, `/sitemap.xml`, `/error` | Público |
| `/oauth2/**`, `/login/oauth2/**` | Público, y solo existen si hay credenciales de Google o Facebook |
| `POST /api/orders` (crear pedido), `GET /api/cake-config/public`, `GET /api/public/settings`, `GET /api/coupons/validate/**`, `GET /actuator/health` | Público (los usa la tienda; `health` solo responde UP o DOWN) |
| `/admin/**` | `ADMIN` |
| `/baker/**` | `PASTELERO` |
| `GET /api/orders/stats/**` (facturación, más vendidos) | `ADMIN` |
| `DELETE /api/orders/**`, `PUT /api/orders/{id}` (editar un pedido) | `ADMIN` |
| `GET /api/inventory/**`, `GET /api/recipes/**` | `ADMIN` o `PASTELERO` |
| Cualquier otra escritura de `/api/inventory/**` y `/api/recipes/**` | `ADMIN` |
| Resto de `/api/orders/**` (listar, crear, confirmar, cambiar estado) | `ADMIN` o `PASTELERO` (con las restricciones de 5.9) |
| `GET /api/products/**`, `GET /api/categories/**` | `ADMIN` o `PASTELERO` |
| `/api/bakey/**`, `/api/reminders/**`, `/api/notifications/**`, `POST /api/audit/download` | `ADMIN` o `PASTELERO` (cada uno ve lo suyo) |
| `/api/account/**` ("Mis pedidos") | Cualquier usuario logueado; siempre los datos de su propia sesión |
| Resto de `/api/**` (usuarios, clientes, cupones, campañas, fidelización, galería, productos, armador, ajustes, auditoría, backups) | `ADMIN` |
| Cualquier otra ruta | Usuario logueado |

## 5.3 Sesión, contraseñas e inicio de sesión

- **Contraseñas** guardadas con **BCrypt** (un resumen irreversible con "sal"); nunca en texto.
- **Bloqueo por intentos:** después de **5 contraseñas incorrectas** con el mismo email **desde la misma IP**, esa combinación queda bloqueada **15 minutos** (`LoginAttemptService`). Se cuenta por email + IP para que nadie pueda dejar a la dueña sin entrar equivocándose a propósito desde otra computadora.
- **"Mantener la sesión iniciada":** cookie firmada que dura **14 días**. La firma incluye la contraseña cifrada, así que al cambiar la contraseña la cookie deja de valer. La clave de firma sale de `dreambake.remember-me-key`; si no está configurada se genera una al arrancar (al reiniciar la app hay que volver a entrar).
- **Cookie de sesión** con `SameSite=Lax`; en el perfil `prod`, además `Secure` y `HttpOnly`.
- **Cerrar sesión** borra la cookie de sesión y la de "mantener la sesión".
- **Registro:** solo se aceptan nombre, email, teléfono y contraseña (`@InitBinder`): nadie puede mandar un campo `roles` y crearse como administrador. El email es único.
- **Google y Facebook:** el personal no puede entrar con una red social sin antes haberla vinculado desde su perfil con la contraseña; si Facebook no manda el email, se pide uno y se comprueba con un código de 6 dígitos (`SocialLoginService`, `SocialSignupController`).
- **"Olvidé mi contraseña":** siempre responde lo mismo (no revela qué emails existen); código al azar de 256 bits del que la base guarda solo la huella SHA-256; vence a los 30 minutos y sirve una vez; al usarlo se anulan los demás links; el código pasa a la sesión para que no quede en la barra de direcciones; contraseña nueva de 8 a 72 caracteres; se audita y se avisa por email.

## 5.4 CSRF

Un ataque CSRF es cuando otro sitio hace que el navegador de un usuario logueado mande una acción a DreamBake sin que se dé cuenta. La protección está activada en toda la aplicación (`csrf.spa()`):
- Spring guarda un token en la cookie `XSRF-TOKEN`. Otro sitio no puede leerla.
- Cada `POST`, `PUT`, `PATCH` o `DELETE` tiene que devolver ese token en el encabezado `X-XSRF-TOKEN`. Lo agrega solo `static/js/csrf.js`, que se carga **primero en todas las páginas**.
- Los formularios con `th:action` incluyen el token solos.

## 5.5 Encabezados de seguridad

| Encabezado | Valor y para qué |
|---|---|
| `Content-Security-Policy` | `script-src 'self' https://cdn.jsdelivr.net`: solo se ejecutan archivos `.js` del propio sitio y de jsDelivr (Chart.js). **No se permite JavaScript dentro del HTML** (`<script>…</script>`, `onclick="…"`): si alguien lograra inyectar código en una página, el navegador no lo ejecutaría. También: estilos y fuentes solo del sitio y de Google Fonts; imágenes del sitio y de las fotos de perfil de Google/Facebook; `connect-src 'self'`; `object-src 'none'`; `base-uri 'self'`; `form-action 'self' https://accounts.google.com`; `frame-ancestors 'none'`. Los estilos todavía admiten `'unsafe-inline'` por atributos `style="…"` (no ejecutan código). |
| `X-Frame-Options: DENY` | Nadie puede mostrar DreamBake dentro de un marco de otro sitio (evita el *clickjacking*). |
| `Referrer-Policy: strict-origin-when-cross-origin` | A otros sitios solo les llega el dominio, nunca la dirección completa. |
| `X-Content-Type-Options: nosniff` | El navegador no "adivina" el tipo de un archivo (importa para las fotos subidas). |
| `Cache-Control: no-store` en las páginas | Las páginas con datos no quedan guardadas en la caché del navegador. |

## 5.6 Límites por IP

`RateLimitFilter` cuenta, por IP, los envíos de los **últimos 10 minutos** ("ventana deslizante"); si se pasan, responde **429 (Demasiadas solicitudes)**:

| Acción | Máximo por IP cada 10 min |
|---|---|
| Crear pedidos (`POST /api/orders`) | 10 |
| Validar o usar cupones | 30 |
| Registrarse | 10 |
| Intentos de inicio de sesión (cualquier cuenta) | 20 |
| Darse de baja de los emails | 20 |
| Pedir o probar un código por email | 15 |
| "Olvidé mi contraseña" | 10 (más 3 por email por hora) |

Además: el seguimiento de pedidos admite **10 búsquedas por minuto por IP** (`TrackingService`) y Bakey tiene un límite propio de **20 mensajes por minuto y 200 por día por usuario** (`BakeyRateLimiter`).

**Detrás de Tailscale Funnel** todas las conexiones llegan desde la propia PC (`127.0.0.1`). `ClientIpResolver` y `ProxyConfig` toman la IP real del encabezado `X-Forwarded-For`, pero **solo si la conexión viene de la propia PC**: un visitante no puede inventar ese encabezado para saltear los límites. Los contadores viven en memoria y vuelven a cero al reiniciar la app.

## 5.7 Datos que llegan desde afuera

- **El precio lo calcula el servidor** (`OrderPricingService`): la tienda manda qué productos o qué tamaño de torta y el código del cupón; el total que mande el navegador se ignora.
- **Pedidos públicos:** el servidor fuerza estado nuevo, seña 0 y pedido nuevo (no se puede pisar uno existente), exige los datos obligatorios, revisa la pausa y la anticipación mínima, y **limpia los textos** (`InputSanitizer`) para evitar ataques XSS en el panel. El código de seguimiento lo genera el servidor.
- **En los paneles** todo texto que vino de la base se muestra escapado (`esc()` o `textContent` en JS, `th:text` en las plantillas).
- **Subida de imágenes** (`FileStorageService`, única puerta de subida): máximo 5 MB; se revisa el **contenido real** (firma JPG, PNG o WebP), no la extensión; se rechazan imágenes de más de 50 megapíxeles ("bombas de descompresión"); JPG y PNG se abren, se achican y se vuelven a guardar (sin EXIF ni ubicación GPS); nombre al azar (UUID); se sirven con `nosniff`.
- **Páginas de error** sin trazas ni mensajes internos (`ErrorConfig` y las plantillas de `error/`).
- **Secretos guardados en la base** (la contraseña del correo) van **cifrados con AES-GCM** (`SecretCipher`); la clave está en `config/secret.key`, fuera del repositorio.
- **Programas de MySQL** (`mysqldump`, `mysql`) se ejecutan con un archivo de opciones temporal: la contraseña nunca aparece en la línea de comandos (`MysqlClient`).

## 5.8 Registro de auditoría

**Toda acción que crea, edita, borra, cambia un estado o descarga algo queda registrada** en la tabla `audit_logs`, con quién (email, nombre, rol), cuándo, desde qué IP y navegador, qué módulo y qué cambió (antes y después). Las descripciones están en español para la dueña ("Confirmó el pedido #45 de María G. con una seña de $ 10.000").

**Cómo se registra**
- Casos simples: la anotación `@Auditable` arriba del método. `AuditAspect` registra **solo si el método terminó bien**.
- Casos con antes y después (precios, stock, pedidos, cupones): llamada directa a `AuditService.log(...)` con `AuditText.snapshot/diff/change`.
- Inicios de sesión, intentos fallidos y cierres de sesión: `AuditSecurityListener`.
- Lo que hace Bakey queda marcado como `ASSISTANT_ACTION` "… (vía Bakey)".
- Dentro de una transacción, el registro se escribe **cuando la transacción se confirma**: si la operación falla, no queda un registro de algo que no pasó.

**Por qué no se puede alterar** (cuatro capas)
1. **En el código:** `AuditLogRepository` no tiene métodos de borrar ni modificar, y la entidad `AuditLog` es `@Immutable` (Hibernate nunca la actualiza). `AuditRestController` no tiene ningún endpoint de escritura.
2. **En la base:** dos *triggers* de MySQL (`audit_logs_no_update` y `audit_logs_no_delete`, creados por `AuditDatabaseInitializer`) cancelan cualquier `UPDATE` o `DELETE`, aunque alguien entre directo a MySQL.
3. **Cadena de hashes SHA-256:** cada registro guarda la huella de sus datos **junto con la huella del registro anterior**. Si alguien cambia un registro viejo, su huella deja de coincidir; si lo borra, el siguiente apunta a una huella que ya no existe. La huella incluye la hora, por eso la zona horaria de la app y la `serverTimezone=UTC` de la conexión no se deben cambiar.
4. **Verificación:** `AuditService.verifyChain()` recorre toda la cadena. Corre sola los lunes a las 7:00 (`AuditScheduler`) y a pedido desde el panel ("Verificar integridad"); si encuentra una alteración, deja un aviso rojo en el panel y manda email a los administradores. Los informes en PDF y CSV llevan un sello de integridad.

**Protección de datos personales (Ley 25.326):** teléfonos, emails, direcciones y cumpleaños se guardan **enmascarados** en los detalles (`AuditText.maskPersonal`), porque el registro no se puede borrar aunque un cliente elimine su cuenta.

**Limitación:** la app se conecta a MySQL con `root`, que puede quitar los triggers. Las capas 1, 3 y 4 siguen detectando la alteración, pero la capa 2 solo protege de verdad con un usuario de MySQL sin permisos de `TRIGGER`, `DROP` ni `ALTER` (pendiente de la fase 7; ver [limitaciones](06-limitaciones-y-mejoras.md)).

## 5.9 Qué ve y qué puede hacer el pastelero

La regla del negocio es que **los montos solo los ve la administradora** y que **el pastelero solo hace lo que su panel le permite**. Ambas cosas las controla el **servidor**, no la pantalla: `baker.html` además oculta botones con CSS, pero eso es solo comodidad.

**No ve dinero** (paso 9.3b). `MoneyResponseAdvice` quita de las respuestas JSON de pedidos, insumos, recetas y productos los campos `totalPrice`, `deposit`, `price`, `unitCost`, `estimatedCost` y `costComplete`, a cualquier profundidad, cuando la sesión es de un PASTELERO sin rol ADMIN (`MoneyVisibility`). Trabaja sobre una copia: las entidades nunca se modifican. Las estadísticas de facturación son solo del ADMIN. La campanita y Bakey tampoco le muestran montos.

**No puede pisar montos.** Si un pastelero crea un pedido, queda en $ 0 aunque el cuerpo traiga montos; si confirma, la seña no cambia; si edita un insumo, el costo se conserva.

**Solo escribe lo que usa su panel** (paso 9.3c):
| Puede | No puede (el servidor responde 403) |
|---|---|
| Ver pedidos, insumos, recetas y productos | Ver estadísticas de facturación, clientes, cupones, auditoría, backups o ajustes |
| Crear pedidos (sin montos) | Editar un pedido ya cargado o eliminarlo |
| Confirmar un pedido nuevo | Rechazar o cancelar pedidos |
| Marcar Entregado y volver a Agendado (`CONFIRMED`/`COMPLETED`) | Pasar un pedido a cualquier otro estado |
| Sus recordatorios, la campanita, Bakey | Crear, editar o borrar insumos, recetas, productos o categorías |

La regla de estados está **también en el controlador** (`OrderRestController.updateStatus`), no solo en `SecurityConfig`, porque Bakey llama a ese método directamente. **Bakey** aplica las mismas reglas: al pastelero no le ofrece las herramientas de ajustar stock ni de ver ventas, no le deja cancelar ni rechazar, y nunca cambia datos sin que la persona toque Confirmar (ver [docs/BAKEY.md](../BAKEY.md)).
