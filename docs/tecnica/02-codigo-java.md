# 2. Código Java por paquete

[← Volver al índice](../DOCUMENTACION-TECNICA.md)

Todo el código Java está en `src/main/java/com/dreambake/`. Son **180 clases** repartidas en 10 paquetes. Cada descripción sale del archivo real; entre paréntesis se indica el paso del proyecto en que se agregó cuando ayuda a ubicarlo.

| Paquete | Clases | Para qué |
|---|---|---|
| [raíz](#paquete-raíz) | 1 | Arranque de la aplicación |
| [`config`](#config--configuración-filtros-y-tareas-automáticas) | 27 | Seguridad, filtros, tareas programadas y ajustes de arranque |
| [`controller`](#controller--puertas-de-entrada-http) | 32 | Páginas y API REST |
| [`service`](#service--lógica-del-negocio) | 39 | Reglas del negocio, emails, backups, Bakey |
| [`repository`](#repository--acceso-a-la-base) | 21 | Lectura y escritura en MySQL |
| [`entity`](#entity--modelo-de-datos) | 35 | Tablas (23 entidades), listas de valores (11 enums) y 1 clase auxiliar |
| [`audit`](#audit--registro-de-actividad) | 4 | Registro de auditoría |
| [`ai`](#ai--conexión-con-la-inteligencia-artificial) | 8 | Conexión con Gemini (Bakey) |
| [`ai/tools`](#aitools--herramientas-de-bakey) | 10 | Herramientas de consulta y de acción de Bakey |
| [`util`](#util--utilidades) | 3 | Funciones chicas que usan varias clases |

## Paquete raíz

- **`DreambakeApplication`** — Punto de entrada (`main`). Antes de arrancar Spring fija la zona horaria de toda la app en `America/Argentina/Buenos_Aires`, porque los cumpleaños, los backups, los recordatorios y la cadena de hashes de la auditoría dependen de "qué día y qué hora es". No se debe cambiar.

## `config` — configuración, filtros y tareas automáticas

**Seguridad y filtros**
- **`SecurityConfig`** — La configuración de seguridad completa: qué rutas son públicas y cuáles exigen rol, CSRF, encabezados (CSP, `X-Frame-Options`, `Referrer-Policy`), login con formulario y con Google/Facebook, "mantener la sesión iniciada", redirección según el rol y cifrado de contraseñas (BCrypt). Ver [Seguridad](05-seguridad.md).
- **`RateLimitFilter`** — Limita cuántos envíos puede hacer una misma IP en 10 minutos (pedidos, cupones, registros, logins, bajas de email, códigos por email y recuperación de contraseña). Si se pasa, responde 429. Usa una "ventana deslizante" en memoria.
- **`MaintenanceFilter`** — Mientras se restaura un backup, responde 503 "En mantenimiento" a todo salvo CSS, JS e imágenes. Es el primer filtro de la cadena.
- **`MaintenanceMode`** — La marca en memoria (activo/inactivo) que consulta el filtro anterior. La activa y desactiva `RestoreService`, siempre dentro de un `try/finally`.
- **`ProxyConfig`** — Hace que la app entienda que llegó por HTTPS cuando está detrás de Tailscale Funnel. Solo confía en los encabezados `X-Forwarded-*` si la conexión viene de la propia PC (`127.0.0.1`/`::1`).
- **`ErrorConfig`** — Fuerza que las respuestas de error sean mínimas (fecha, código, ruta), sin traza de clases ni mensajes internos, aunque DevTools pida lo contrario.
- **`MoneyResponseAdvice`** — Cuando quien pregunta es un PASTELERO, quita de las respuestas JSON de pedidos, insumos, recetas y productos los campos de dinero (`totalPrice`, `deposit`, `price`, `unitCost`, `estimatedCost`, `costComplete`). Trabaja sobre una copia, nunca sobre las entidades (paso 9.3b).

**Datos para las vistas y configuración propia**
- **`GlobalControllerAdvice`** — Pone el usuario logueado (`currentUser`) a disposición de todas las plantillas, para no buscarlo en cada controlador.
- **`PublicViewAdvice`** — En las páginas públicas expone los datos del negocio (`negocio`), el número de WhatsApp, la URL del sitio y si los botones de Google/Facebook están activos.
- **`AppProperties`** — Lee las claves `app.*` (`app.public-url`, `app.business.*`) con valores por defecto, para que la app nunca falle por un dato sin cargar.
- **`BakeyProperties`** — Lee las claves `bakey.*` (clave y modelo de Gemini, tiempo de espera, límites por usuario, minutos de vida de una acción pendiente).
- **`MvcConfig`** — Sirve `/uploads/**` desde la carpeta del disco, configura la caché de una semana para `/css`, `/js`, `/img` y `/uploads`, agrega la huella del contenido al nombre de los CSS/JS (para que un cambio se vea sin borrar la caché) y fija el tamaño máximo de subida (5 MB).
- **`AsyncConfig`** / **`SchedulingConfig`** — Activan los métodos `@Async` (emails en otro hilo) y las tareas `@Scheduled`.
- **`ConfigurationCheck`** — Al arrancar avisa en el log qué funciones quedan desactivadas por falta de su clave (correo, Google, Facebook, Gemini, backups). Nunca frena el arranque.
- **`LoginTimestampListener`** — Anota la fecha y hora del último ingreso de cada usuario.

**Tareas programadas** (ver [Arquitectura](01-arquitectura.md#12-tareas-programadas))
- **`BackupScheduler`** — Backup diario a las 3:00; si al arrancar el último bueno tiene más de 24 h, hace uno a los 2 minutos.
- **`OrderNoticeScheduler`** — A las 8:00: resumen de entregas para el negocio y recordatorio al cliente el día antes. Se pone al día si la PC estuvo apagada.
- **`LoyaltyScheduler`** — A las 9:00: saludos de cumpleaños. Se pone al día al arrancar y respeta el interruptor "Saludos automáticos".
- **`CampaignScheduler`** — Cada 5 minutos: arranca las campañas programadas y retoma las pausadas.
- **`ReminderScheduler`** — Cada minuto: email de los recordatorios que vencen dentro de la hora.
- **`AuditScheduler`** — Lunes 7:00: verifica la integridad del registro de auditoría.

**Mantenimiento de la base al arrancar** (todas son seguras de correr siempre: si no hay nada que hacer, no cambian nada)
- **`AuditDatabaseInitializer`** — Crea los dos triggers de MySQL que impiden modificar o borrar filas de `audit_logs`.
- **`EnumCheckSchemaFix`** — Borra las restricciones `CHECK` que Hibernate 7 pone en las columnas de enums, para que agregar un valor nuevo a un enum no rompa la base.
- **`EmailLogSchemaFix`** — Convierte `email_logs.type` de ENUM de MySQL a texto, por el mismo motivo.
- **`TrackingCodeBackfill`** — Da código de seguimiento a los pedidos viejos que no lo tenían.
- **`CustomerConsentBackfill`** — Completa el consentimiento (sin consentimiento) y el código de baja de los clientes viejos.

## `controller` — puertas de entrada HTTP

**Páginas (devuelven una plantilla Thymeleaf)**
- **`WebController`** — Tienda pública: inicio (`/`, calcula el sticker "Más vendido" de los 3 productos más pedidos), `/products`, `/builder`, `/cart` y las páginas legales (`/privacidad`, `/terminos`, `/eliminar-datos`).
- **`AuthController`** — Registro (`/register`, solo acepta nombre, email, teléfono y contraseña) e inicio de sesión (`/login`).
- **`PasswordResetController`** — "Olvidé mi contraseña" (`/recuperar`, `/recuperar/nueva`). El código del link pasa a la sesión para que no quede en la barra de direcciones.
- **`SocialSignupController`** — "Completá tu email" para quien entra con Facebook sin email: código de 6 dígitos antes de crear la cuenta.
- **`ProfileController`** — Perfil del cliente (`/profile`): datos, confirmar email, cambiar contraseña, vincular/desvincular Google, eliminar la cuenta.
- **`AdminController`** — Panel de administración (`/admin`) y el botón "Entendido" del aviso de auditoría alterada.
- **`BakerController`** — Panel del pastelero (`/baker`).
- **`TrackingController`** — Seguimiento público de pedidos (`/seguimiento?codigo=…`).
- **`UnsubscribeController`** — Baja de los emails promocionales (`/baja`) en dos pasos, para que un antivirus que abre el link no dé de baja a nadie.
- **`SeoController`** — Genera `/robots.txt` y `/sitemap.xml` con la URL pública del sitio.

**API REST (devuelven JSON; los usa el JavaScript)**
- **`OrderRestController`** (`/api/orders`) — El controlador central de pedidos: listar, crear (desde la tienda o el panel), confirmar (verifica stock, descuenta insumos y une el pedido a su cliente), editar, cambiar estado, eliminar y estadísticas (facturación mensual y más vendidos). Audita cada cambio y publica el evento que dispara los emails. Bakey llama a sus métodos directamente.
- **`InventoryRestController`** (`/api/inventory`) — Alta, edición (ajuste de stock con el antes y el después en la auditoría) y baja de insumos.
- **`RecipeRestController`** (`/api/recipes`) — Alta, edición y baja de recetas con sus insumos.
- **`ProductRestController`** (`/api`) — Productos (`/products`) y categorías (`/categories`, con orden). Al borrar un producto borra su foto si nadie más la usa.
- **`CakeConfigRestController`** (`/api/cake-config`) — Tamaños y opciones del armador de tortas. `/public` lo usa la tienda; el resto es del admin. Al arrancar carga tamaños y sabores por defecto si la tabla está vacía.
- **`CustomerRestController`** (`/api`) — Clientes (lista con pedidos y gasto calculados, alta, edición, baja, historial de emails, dar de baja promociones) y etiquetas.
- **`CouponRestController`** (`/api/coupons`) — Cupones (alta, edición, baja). `/validate/{code}` es público para el carrito y devuelve solo lo necesario.
- **`LoyaltyRestController`** (`/api/loyalty`) — Ajustes de los saludos de cumpleaños, próximos cumpleaños y envío manual (email o WhatsApp).
- **`CampaignRestController`** (`/api/campaigns`) — Campañas de promociones: crear, editar, vista previa, prueba, programar, enviar, cancelar. El navegador manda solo texto, nunca HTML.
- **`GalleryRestController`** (`/api/gallery`) — Galería de fotos: subir (pasa por `FileStorageService`) y borrar.
- **`UserRestController`** (`/api/users`) — Equipo: lista, alta y baja de pasteleros.
- **`SettingsRestController`** (`/api/settings`) — Datos del negocio y cuenta de correo de envío.
- **`PublicSettingsController`** (`/api/public/settings`) — Datos públicos del negocio para la tienda (WhatsApp, redes, anticipación, pausa).
- **`EmailRestController`** (`/api/email/test`) — Manda un email de prueba.
- **`OrderNoticeRestController`** (`/api/order-notices`) — Mensaje de WhatsApp de un pedido (lleva montos, por eso es solo del admin) y disparo manual de los avisos diarios.
- **`NotificationRestController`** (`/api/notifications`) — Campanita: avisos del rol y marcar como leídos.
- **`ReminderRestController`** (`/api/reminders`) — Recordatorios del personal: listar, avisos que llegaron a su hora, crear, marcar hecho, borrar. La persona sale siempre de la sesión.
- **`AccountRestController`** (`/api/account/orders`) — "Mis pedidos" del cliente logueado. No recibe parámetros: imposible pedir los de otro.
- **`AuditRestController`** (`/api/audit`) — Registro de actividad: búsqueda paginada, lista de personas, CSV, PDF, verificar la cadena y registrar descargas. **No tiene ningún endpoint que modifique o borre.**
- **`BackupRestController`** (`/api/backups`) — Estado y lista de backups, backup manual, descarga (auditada) y restauración.
- **`BakeyRestController`** (`/api/bakey`) — Chat con Bakey, historial, uso del día, nueva conversación y confirmar/cancelar acciones propuestas.
- **`RestExceptionHandler`** — Convierte errores de validación y archivos demasiado grandes en respuestas JSON con un mensaje en español.

## `service` — lógica del negocio

**Pedidos, precios y stock**
- **`OrderPricingService`** — Calcula en el servidor el precio de un pedido de la tienda: busca los precios reales de productos y tamaños de torta, valida el cupón, descuenta un uso y arma el total.
- **`StockCalculator`** — Suma los insumos que necesita un pedido siguiendo la cadena Pedido → Ítem → Producto → Receta → Insumo. Lo usan la confirmación de pedidos y Bakey.
- **`CouponService`** — Reglas de los cupones: si está activo, no venció y le quedan usos; descontar un uso.
- **`TrackingService`** — Arma la línea de tiempo del seguimiento con el nombre abreviado ("María G.") y sin datos de contacto ni montos; limita las búsquedas por IP.
- **`OrderNoticeService`** — Emails de pedidos (recibido, nuevo pedido para el negocio, confirmado, listo, cancelado, recordatorio del día antes, resumen diario) y el texto de WhatsApp. Escucha el evento del pedido **después** de que se guardó, en otro hilo, y no repite avisos.
- **`NotificationService`** — Calcula los avisos de la campanita según el rol (pedidos nuevos, stock bajo, vencimientos, entregas de mañana, recordatorios; solo admin: backup fallido y cumpleaños). Solo guarda qué leyó cada persona.

**Clientes, fidelización y correo**
- **`CustomerAccountService`** — Área del cliente: su ficha (solo si su email está comprobado), sus pedidos, guardar datos y "Eliminar mi cuenta" (borra el usuario y anonimiza ficha y pedidos, Ley 25.326).
- **`BirthdayService`** — Saludo de cumpleaños con cupón único `CUMPLE-XXXX` (un uso, 30 días) a quienes aceptaron promociones. No repite en el año.
- **`CampaignService`** — Reglas de las campañas: audiencia, validación del contenido, armado del email y vista previa.
- **`CampaignSender`** — Manda las campañas de a un email cada 2 segundos, con tope diario y pausa ante errores seguidos.
- **`EmailService`** — Envía emails HTML con el diseño común (`email/base.html`), en otro hilo, y registra cada envío en `email_logs`. Nunca tira error hacia afuera.
- **`MailAccountService`** — Decide desde qué cuenta salen los emails: la cargada en el panel (contraseña cifrada en la base) o la de `config/application.properties`. Prueba la conexión antes de guardar.
- **`SecretCipher`** — Cifra y descifra secretos guardados en la base (contraseña del correo) con AES-GCM. La clave está en `config/secret.key`, fuera del repositorio.
- **`EmailCodeChallenge`** / **`EmailCodeService`** — Código de 6 dígitos por email para comprobar que un email es de quien lo escribe (intentos, reenvíos y espera).
- **`BusinessProfileService`** — Datos del negocio editables (con caché, validación y auditoría del antes y el después) y las reglas de la tienda: pausa de pedidos y anticipación mínima.
- **`BusinessSettingService`** — Ajustes técnicos guardados en `business_settings` (cumpleaños activos y porcentaje, tope diario de campañas, WhatsApp de respaldo).

**Usuarios y acceso**
- **`UserService`** — Registro de clientes (contraseña con BCrypt, rol de cliente), creación de usuarios de redes sociales y alta/baja de pasteleros.
- **`UserDetailsServiceImpl`** — Busca al usuario por email para el login con contraseña; las cuentas deshabilitadas no entran.
- **`LoginAttemptService`** — Bloquea 15 minutos la combinación email + IP después de 5 contraseñas incorrectas.
- **`PasswordResetService`** — "Olvidé mi contraseña": código de 256 bits, se guarda solo su huella SHA-256, vence en 30 minutos y sirve una vez; al cambiar la contraseña audita y avisa por email.
- **`SocialLoginService`** — Decide a qué usuario corresponde quien entra con Google o Facebook (por id de la red, por email verificado o cuenta nueva) y rechaza al personal que no vinculó su cuenta.
- **`CustomOidcUserService`** / **`CustomOAuth2UserService`** — Reciben los datos de Google (OpenID Connect) y de Facebook (OAuth2) y aplican `SocialLoginService`.
- **`SocialUserPrincipal`** — El "usuario logueado" de una red social, con el email y los roles de DreamBake.
- **`PendingSocialSignup`** — Registro con Facebook a medias (sin email), guardado en la sesión hasta confirmar el código.

**Archivos, backups y restauración**
- **`FileStorageService`** — Toda subida de imágenes: máximo 5 MB, revisa el contenido real (firma JPG/PNG/WebP), rechaza imágenes gigantes, achica a 1600 px, vuelve a guardar sin EXIF, nombre UUID. Borra una foto solo si nadie la usa.
- **`BackupService`** — Hace el backup (`mysqldump` + fotos + `secret.key` en un zip con su `.sha256`), lo guarda en diarios/semanales/mensuales/manuales con retención y avisa si hace más de 48 h que no hay uno bueno.
- **`BackupAlertService`** — Traduce el error técnico de un backup fallido a lenguaje simple y lo manda por email a los administradores.
- **`RestoreService`** — Restaura un backup con varias protecciones: confirmación con contraseña, verificación de la huella, prueba en una base aparte, compatibilidad de columnas, copia previa, modo mantenimiento y cambio atómico de tablas. No toca `audit_logs` ni `backup_records`.
- **`MysqlClient`** — Ejecuta `mysqldump` y `mysql` con un archivo de opciones temporal, para que la contraseña nunca aparezca en la línea de comandos.

**Auditoría**
- **`AuditService`** — Escribe cada registro de auditoría con su hash encadenado al anterior, enmascara datos personales y verifica la cadena (`verifyChain`). `runAsAssistant` marca lo que hace Bakey como "vía Bakey".
- **`AuditIntegrityService`** — Corre la verificación; si la cadena está rota deja un aviso en el panel y manda email (sin repetir el mismo aviso cada semana).
- **`AuditReportService`** — Búsqueda paginada del registro y exportación a CSV y PDF (OpenPDF) con sello de integridad. Solo lectura.

**Bakey (lado servicio)**
- **`BakeyService`** — Recibe el mensaje, revisa límites, arma las instrucciones (con fechas ya calculadas), llama a Gemini, ejecuta las herramientas pedidas (máximo 5 vueltas) y devuelve una respuesta amable.
- **`BakeyConversationStore`** — Guarda en memoria, por usuario, las últimas 20 entradas de la conversación (no en el navegador).
- **`BakeyRateLimiter`** — Límite propio por usuario: 20 mensajes por minuto y 200 por día.
- **`BakeyUsageStats`** — Contadores del día (mensajes, acciones propuestas y confirmadas, errores) para la tarjeta del Resumen.
- **`ReminderService`** — Recordatorios del equipo: crear, listar, marcar hecho, borrar, email una hora antes; audita todo. Lo usan el panel y Bakey.

## `repository` — acceso a la base

Son **interfaces**: Spring Data JPA genera el SQL a partir del nombre del método (por ejemplo, `findByEmail` → `SELECT … WHERE email = ?`). Las consultas especiales usan `@Query`.

| Repositorio | Entidad | Consultas propias destacadas |
|---|---|---|
| `OrderRepository` | `Order` | por estado, por cliente o teléfono, para "Mis pedidos", por rango de fechas y estados; ranking de más vendidos |
| `CustomerRepository` | `Customer` | por etiqueta, primer cliente con un teléfono, por código de baja, por email, con consentimiento, con cumpleaños |
| `ProductRepository` | `Product` | por categoría |
| `CategoryRepository`, `CustomerTagRepository` | `Category`, `CustomerTag` | por nombre |
| `InventoryItemRepository`, `RecipeRepository` | `InventoryItem`, `Recipe` | (solo las básicas de JPA) |
| `CouponRepository` | `Coupon` | por código sin importar mayúsculas; descontar un uso de forma atómica |
| `CakeSizeRepository`, `CakeOptionRepository` | `CakeSize`, `CakeOption` | habilitados en orden |
| `GalleryImageRepository` | `GalleryImage` | ¿existe esta URL? |
| `UserRepository` | `User` | por email, por red social e id, actualizar último ingreso |
| `PasswordResetTokenRepository` | `PasswordResetToken` | por huella, anular todos los de una persona, borrar vencidos |
| `ReminderRepository` | `Reminder` | pendientes, hechos, para email, marcar hecho o enviado |
| `NotificationReadRepository` | `NotificationRead` | claves leídas por persona, borrar viejas |
| `EmailLogRepository` | `EmailLog` | historial por cliente o email; "¿ya se mandó este aviso?" (para no repetir) |
| `CampaignRepository` | `Campaign` | programadas vencidas, en curso; actualizar contadores, pausar, terminar |
| `BusinessProfileRepository`, `BusinessSettingRepository` | `BusinessProfile`, `BusinessSetting` | (básicas) |
| `BackupRecordRepository` | `BackupRecord` | último bueno, último por tipo, último intento |
| **`AuditLogRepository`** | `AuditLog` | extiende `Repository` (no `JpaRepository`): **no tiene métodos de borrar ni de modificar**; solo guardar, contar, buscar y recorrer la cadena |

## `entity` — modelo de datos

Las clases con `@Entity` son tablas; los `enum` son listas cerradas de valores guardadas como texto. El detalle de campos y relaciones está en [Modelo de datos](04-modelo-de-datos.md).

- **Pedidos:** `Order` (pedido, tabla `orders_table`), `OrderItem` (cada producto del pedido), `OrderStatus` (estados), `TrackingCodes` (genera los códigos `DB-XXXXXX`, sin 0/O/1/I/L para que no se confundan).
- **Catálogo:** `Product`, `Category`, `GalleryImage`, `CakeSize` y `CakeOption` (armador de tortas).
- **Producción:** `Recipe`, `RecipeItem`, `InventoryItem`.
- **Clientes y fidelización:** `Customer`, `CustomerTag`, `Coupon`, `CouponType`, `Campaign`, `CampaignAudience`, `CampaignStatus`, `EmailLog`, `EmailType`, `EmailStatus`.
- **Usuarios y acceso:** `User`, `AuthProvider` (LOCAL/GOOGLE/FACEBOOK), `PasswordResetToken`, `NotificationRead`, `Reminder`.
- **Negocio y sistema:** `BusinessProfile` (una fila con los datos del negocio), `BusinessSetting` (clave/valor), `BackupRecord`, `BackupType`, `BackupStatus`.
- **Auditoría:** `AuditLog` (marcada `@Immutable`: Hibernate nunca la actualiza), `AuditAction`, `AuditModule`.

## `audit` — registro de actividad

- **`Auditable`** — Anotación que se pone arriba de un método para auditarlo con una sola línea; la descripción admite datos del método (`#{#body.code}`, `#result`).
- **`AuditAspect`** — Código que Spring ejecuta automáticamente **después** de cada método `@Auditable` que terminó bien y llama a `AuditService.log`. Para los casos que necesitan el antes y el después se llama a `AuditService` directamente.
- **`AuditSecurityListener`** — Audita inicios de sesión, intentos fallidos y cierres de sesión a partir de los eventos de Spring Security.
- **`AuditText`** — Textos comunes: estados con los nombres del panel, montos en pesos, la "foto" de un pedido para comparar y el **enmascarado** de teléfonos, emails y direcciones.

## `ai` — conexión con la inteligencia artificial

Ver también [docs/BAKEY.md](../BAKEY.md).

- **`AiProvider`** — Interfaz (patrón *Strategy*): el resto del sistema solo conoce esta interfaz, nunca a Gemini. Cambiar de proveedor sería escribir otra implementación.
- **`GeminiProvider`** — La implementación para Gemini: habla con su API REST usando `RestClient` (sin SDK). La clave va en un encabezado, nunca en la dirección ni en los logs.
- **`AiMessage`**, **`AiReply`**, **`AiTool`**, **`AiToolCall`**, **`AiToolResult`** — Datos neutros de la conversación: un mensaje, una respuesta, una herramienta, un pedido de usar una herramienta (con la `signature` que Gemini exige devolver tal cual) y su resultado.
- **`AiException`** — Error al hablar con la IA, con su tipo (sin cupo, demora, sin configurar, bloqueado, fallido) para elegir un mensaje amable.

## `ai/tools` — herramientas de Bakey

- **`BakeyTool`** — Definición de una herramienta: nombre, descripción para el modelo, parámetros, roles permitidos y si es de consulta o de acción.
- **`BakeyQueryTools`** — Las herramientas de **solo lectura** (pedidos por fecha, próximos, detalle, búsqueda de clientes, cumpleaños, stock, vencimientos, insumos necesarios, resumen de ventas, productos y precios, recordatorios, ayuda del sistema). Devuelven poco y sin montos para el pastelero.
- **`BakeyActionTools`** — Las herramientas que **proponen** cambios (crear pedido, cambiar estado, ajustar stock, registrar compra de insumos, crear recordatorio, marcarlo hecho). Validan con las mismas reglas que el panel y llaman a los mismos métodos de los controladores al ejecutarse.
- **`ActionProposal`** — Lo que Bakey propone: título, resumen legible y el código que hará el cambio recién al confirmar.
- **`BakeyActionService`** — Guarda las acciones pendientes (10 minutos, por persona) y las ejecuta solo cuando esa misma persona toca Confirmar; audita como "vía Bakey".
- **`BakeyToolRegistry`** — Ofrece al modelo solo las herramientas del rol y vuelve a revisar el permiso al ejecutar.
- **`BakeyPrivacy`** — Minimización de datos: tapa teléfonos, emails y direcciones salvo doble condición, y marca los textos de clientes como datos («…»), nunca como instrucciones.
- **`ToolArgs`** — Valida los parámetros que manda el modelo (fechas, números, textos, ids) y corta con un mensaje en español si algo está mal.
- **`ToolContext`**, **`ToolOutcome`** — Quién pregunta (rol, fecha de hoy) y el resultado de una herramienta (JSON para el modelo y, si es una acción, la tarjeta para la persona).

## `util` — utilidades

- **`ClientIpResolver`** — Averigua la IP real del visitante detrás de Tailscale Funnel (solo confía en `X-Forwarded-For` si la conexión viene de la propia PC). La usan los límites por IP y el bloqueo de login.
- **`InputSanitizer`** — Limpia los textos de los pedidos de la tienda (caracteres que permiten armar HTML, caracteres de control, largo máximo) para evitar ataques XSS en el panel.
- **`MoneyVisibility`** — La regla "¿es pastelero sin ser admin?", que usan `MoneyResponseAdvice` y los controladores para ocultar y proteger los montos y limitar los cambios de estado.
