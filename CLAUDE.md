# CLAUDE.md — DreamBake

Contexto del proyecto para Claude Code. Leelo completo antes de hacer cambios.

## Qué es DreamBake

DreamBake es el **trabajo final (tesis)** de Nataniel Medina para la carrera **Técnico Superior en Analista de Sistemas** (IPESMI, Posadas, Misiones, ciclo 2026). Es un sistema web de gestión para la pastelería **Dulce Sueños Posadas**, un emprendimiento chico que hace tortas personalizadas y mesas dulces.

### El problema (relevado en 4 entrevistas con la clienta)
- La información está dispersa: los pedidos en un pizarrón, los clientes en WhatsApp/Instagram y las finanzas en un cuaderno.
- No hay control de inventario ni de vencimientos de insumos y packaging.
- Las recetas y el cálculo de insumos se hacen sin fórmula.
- La producción de pedidos grandes se coordina de memoria (decoraciones 1 semana antes, bizcochuelos 3 días antes, galletitas 2 días antes).
- Cuando un pedido sale mal se pierde el 100% de lo utilizado; se dedican 4–5 h semanales a tareas administrativas automatizables.

### Lo que pidió la clienta (tenerlo en cuenta en cualquier cambio)
- **Que sea intuitivo**: el personal no es técnico. Si el sistema resulta complicado, prefieren volver al papel. La simplicidad es un requisito, no un detalle.
- Accesible desde **PC y celular** sin instalar nada → todo tiene que ser **responsive**.
- Recordatorios de entregas con al menos 3 días de anticipación; planificación de producción con horizonte mínimo de 3 días.
- Control de stock con alertas de stock bajo y vencimientos.
- Fidelización de clientes (recordatorio de cumpleaños estilo Starbucks, cupones).
- Recibos automáticos (sin valor legal).
- Reportes de ganancias y márgenes.
- Seguridad moderada de los datos de clientes (Ley 25.326 de Protección de Datos Personales).
- A futuro: evolucionar a tienda online.

## Módulos del sistema

1. Autenticación, seguridad y perfiles
2. Catálogo de productos y galería
3. Creador de tortas y tienda web
4. Gestor de pedidos
5. Gestor de inventario
6. Gestor de recetas
7. Impresión de recibos
8. Dashboard con resumen y métricas
9. Gestor de clientes
10. Fidelización de clientes (cupones, cumpleaños)
11. Tareas del pastelero
12. Gestor de equipo de trabajo

## Stack técnico

- **Java 21** (obligatorio, definido en `pom.xml`)
- **Spring Boot 4.0.6**: Web MVC, Data JPA (Hibernate), Security, Validation, Thymeleaf, DevTools
- **Thymeleaf** + `thymeleaf-extras-springsecurity6` para las vistas
- **MySQL 8** (`mysql-connector-j`)
- **Lombok** (requiere annotation processing en el IDE)
- **Maven** a través del wrapper (`mvnw` / `mvnw.cmd`, Maven 3.9.16). No hace falta Maven instalado.
- Frontend: **HTML + CSS + JavaScript vanilla** (sin frameworks, sin build step, sin npm). Fuente **Poppins** (Google Fonts) y **Chart.js** por CDN en los paneles.
- Entorno de desarrollo: **Windows** (PowerShell / Git Bash).

## Cómo levantar el proyecto

```powershell
# Requiere MySQL corriendo con la base creada:  CREATE DATABASE dreambake_db;
.\mvnw.cmd spring-boot:run
# Abrir http://localhost:8080
```

- Compilar sin levantar: `.\mvnw.cmd compile`
- Tests: `.\mvnw.cmd test` (solo existe el test de contexto por defecto)
- Conexión en `src/main/resources/application.properties`: `jdbc:mysql://localhost:3306/dreambake_db`, usuario `root`.
- `spring.jpa.hibernate.ddl-auto=update`: Hibernate crea y actualiza las tablas solo a partir de las entidades. **No existen scripts SQL ni migraciones.** Agregar un campo a una entidad agrega la columna; renombrar o borrar campos NO limpia columnas viejas.
- **No hay usuario administrador precargado.** Para tener uno: registrarse en `/register` y después en MySQL `UPDATE users SET roles='ADMIN' WHERE email='...';` (o `'PASTELERO'`).
- Con DevTools, los cambios en templates, CSS y JS se ven recargando el navegador (a veces con Ctrl+F5). Los cambios en Java reinician la app cuando se recompilan (o reiniciándola a mano).

## Estructura

```
src/main/java/com/dreambake/
├── config/        SecurityConfig (rutas, roles, CSP, remember-me), MvcConfig (expone /uploads), GlobalControllerAdvice (currentUser en todas las vistas),
│                  PublicViewAdvice (número de WhatsApp y URL del sitio en las páginas públicas), RateLimitFilter
├── controller/    Controladores de vistas (WebController, AdminController, BakerController, AuthController, ProfileController)
│                  y REST bajo /api (*RestController) que consume el JS del frontend
├── entity/        Entidades JPA (ver "Modelo de datos")
├── repository/    Interfaces Spring Data JPA
└── service/       UserService, UserDetailsServiceImpl, ProductManagementService, LoginAttemptService,
                   OrderPricingService (precios reales de un pedido de la tienda), CouponService, BusinessSettingService (ajustes del negocio)
                   (gran parte de la lógica de negocio está directamente en los controladores REST)

src/main/resources/
├── templates/     Vistas Thymeleaf
│   ├── index.html, products.html, builder.html, cart.html   → tienda pública
│   ├── login.html, register.html, profile.html               → autenticación y perfil
│   ├── admin.html   → panel de administración (una sola página con pestañas, ~1200 líneas)
│   ├── baker.html   → panel del pastelero
│   └── fragments/header.html → header compartido (th:replace)
├── static/css/    tokens.css (colores, letra, espacios: lo usan todas las páginas), components.css (botones, campos, foco…),
│                  main.css (base vieja, no se toca), admin.css (paneles) y un CSS por página pública (home, products, builder, cart, login…)
├── static/js/
│   ├── public/store.js      → carrito (localStorage "dreamBakeCart"), creador de tortas, checkout
│   ├── header.js, wheel-carousel.js, login.js, register.js
│   └── admin/               → un archivo por pestaña del panel: main.js (sidebar, modales globales), dashboard.js,
│                              orders.js, products.js, inventory.js, recipes.js, clients.js, gallery.js,
│                              cake-config.js (armador de tortas), team.js, notifications-simple.js
└── static/img/    imágenes y videos del sitio

uploads/           imágenes subidas desde el panel (servidas en /uploads/**)
```

### Detalles importantes del frontend
- **`baker.html` reutiliza scripts del admin** (`admin/main.js`, `inventory.js`, `recipes.js`, `orders.js`). Un cambio en esos archivos afecta a **los dos paneles**: verificá que no rompa ninguno.
- El panel admin cambia de sección con pestañas `data-tab` (resumen, pedidos, productos, inventario, recetas, clientes, galería, armador, equipo, ajustes); cada pestaña tiene su JS en `static/js/admin/` (Ajustes → `settings.js`).
- **Sin JavaScript dentro del HTML**: la CSP no permite `<script>…</script>` ni `onclick="…"`/`onchange="…"` (tampoco en el HTML que arma el JS). Los botones se conectan con `addEventListener`, usando ids o atributos `data-*`: `data-close-modal="id"` cierra una ventana (main.js), `data-order-action` (orders.js), `data-cake-action`/`data-cake-toggle` (cake-config.js). El modo oscuro se aplica con `admin/theme-init.js` en el `<head>`.
- Modales y confirmaciones globales: `window.showConfirmModal(...)` y `window.showErrorAlert(...)` en `admin/main.js`. Reutilizalos en vez de usar `alert()`/`confirm()`. Todas las ventanas (`.modal-overlay`) se cierran con Esc; las que no tienen formulario, también tocando el fondo.
- Tablas de los paneles: en el celular cada fila se ve como tarjeta. Cada `<td>` que arma el JS lleva `data-label="…"` (la etiqueta de la tarjeta); la celda de botones lleva `class="cell-actions"`.
- `baker.html` oculta con su propio `<style>` los botones de admin por clase (`.btn-edit`, `.btn-delete`, `button.btn-red`…), la 5.ª columna de Pedidos (el total) y la caja de pago de "Ver pedido" (`.order-details-box--money`): no cambiar esas clases ni el orden de las columnas.
- Dinero solo en el admin: la columna de costo de Inventario existe solo en `admin.html` (`th[data-col="cost"]`) e `inventory.js` la dibuja si la encuentra; `recipes.js` muestra costos solo si `<body data-panel="admin">`.
- El JS se comunica con el backend vía `fetch` a los endpoints `/api/...` y arma el HTML dinámicamente.
- Variables de diseño: `tokens.css` (paleta y escalas) y, en los paneles, las variables `--admin-*` de `admin.css` (modo claro y oscuro). Color de acento `--accent: #e0487f` (rosa). Usá las variables existentes en vez de colores sueltos.
- Los íconos son SVG inline (se reemplazaron los emojis por SVG). No volver a usar emojis como íconos.
- El número de WhatsApp del negocio se cambia en Panel → Ajustes: en las plantillas públicas se usa `${whatsappNumber}` y `store.js` lo lee del atributo `data-whatsapp` del footer. No escribir el número fijo en el código.

## Seguridad y roles

Definido en `config/SecurityConfig.java`:

| Ruta | Acceso |
|---|---|
| `/`, `/builder`, `/products`, `/cart`, `/register`, `/login`, `/css/**`, `/js/**`, `/img/**`, `/uploads/**` | Público |
| `POST /api/orders`, `GET /api/cake-config/public`, `GET /api/coupons/validate/**` | Público (lo usa la tienda) |
| `/admin/**` | `ADMIN` |
| `/baker/**` | `PASTELERO` |
| `/api/orders/**`, `/api/inventory/**`, `/api/recipes/**`, `GET /api/products/**`, `GET /api/categories/**` | `ADMIN` o `PASTELERO` |
| resto de `/api/**` (usuarios, clientes, cupones, galería, configuración del creador, altas/bajas de productos, ajustes `/api/settings`) | `ADMIN` |

- Los roles son **authorities sin prefijo** (`ADMIN`, `PASTELERO`), guardados como texto en `User.roles`.
- Tras el login: ADMIN → `/admin`, PASTELERO → `/baker`, otro → `/`. Logout → `/login?logout`. Después de registrarse → `/login?registered`.
- "Mantener la sesión iniciada": `rememberMe()` con cookie firmada de 14 días. La clave sale de `dreambake.remember-me-key`; si no está configurada se genera una al arrancar (al reiniciar hay que volver a entrar).
- Contraseñas con BCrypt.
- **CSRF activado** (`csrf.spa()`): el token viaja en la cookie `XSRF-TOKEN`. Los formularios con `th:action` lo incluyen solos; los `fetch` lo agregan con `static/js/csrf.js`, que se carga primero en **todas** las páginas (públicas y paneles). Toda página nueva debe incluirlo.
- Encabezados: Content-Security-Policy (orígenes permitidos en `SecurityConfig`; si se agrega un CDN nuevo, sumarlo ahí; `script-src` **no** permite JavaScript dentro del HTML), `X-Frame-Options: DENY`, `Referrer-Policy`, cookie de sesión `SameSite=Lax`.
- Login: bloqueo de 15 min tras 5 intentos fallidos (`LoginAttemptService`). Cuentas con `enabled=false` no pueden entrar.
- `RateLimitFilter`: límite por IP de pedidos, cupones y registros.
- Pedidos públicos: el servidor fuerza `PENDING`, seña 0 y pedido nuevo, exige nombre/teléfono/fecha/ítems y limpia los textos (`util/InputSanitizer`) para evitar XSS en el panel.
- **El precio lo calcula el servidor** (`OrderPricingService`): el carrito manda `product.id` o `cakeSizeId` y el código del cupón; el servidor busca los precios en `products` y `cake_sizes`, valida el cupón, le descuenta un uso y calcula el total. El total que mande el navegador se ignora.
- Registro: solo se aceptan `name`, `email`, `phone`, `password` (`@InitBinder`) y el email debe ser único.
- Galería: solo imágenes JPG/PNG/WEBP/GIF, guardadas con nombre UUID, de hasta 10 MB (límite en `MvcConfig`, no en application.properties). Antes de subir, `gallery.js` achica la foto a 1600 px y la pasa a JPG (así entran las fotos HEIC del iPhone). Al borrar una foto se borra también el archivo (solo dentro de `uploads/`); si la usa un producto, no se borra.
- Producción: `application-prod.properties` (perfil `prod`) toma la base y las claves de variables de entorno, activa cookies `Secure` y oculta los detalles de los errores.
- Al agregar una ruta o endpoint nuevo, **actualizá SecurityConfig** si hace falta.

## Modelo de datos (entidades JPA)

| Entidad (tabla) | Contenido principal | Relaciones |
|---|---|---|
| `User` (users) | email (login), password, name, phone, roles, enabled | — |
| `Customer` (customers) | name, email, phone, address, birthDate, totalPurchases (sin uso), dateAdded; `ordersCount`/`totalSpent` calculados (@Transient) | ManyToOne `CustomerTag` |
| `CustomerTag` (customer_tags) | name, isDeletable, displayOrder | — |
| `Order` (orders_table) | customerName/Phone/Email (texto), deliveryDate, deliveryShift, deliveryMethod ("Retiro"/"Envío"), deliveryAddress, couponCode, totalPrice, deposit (seña), status, stockStatus, createdAt | OneToMany `OrderItem`, ManyToOne `Customer` (se une al confirmar) |
| `OrderItem` (order_items) | productType, isCustomCake, details (TEXT), price | ManyToOne `Order`, ManyToOne `Product` |
| `Product` (products) | name, description, price, enabled, imageUrl | ManyToOne `Category`, ManyToOne `Recipe` |
| `Category` (categories) | name, isDeletable, displayOrder | — |
| `Recipe` (recipes) | name, details | OneToMany `RecipeItem` |
| `RecipeItem` (recipe_items) | quantityRequired | ManyToOne `Recipe`, ManyToOne `InventoryItem` |
| `InventoryItem` (inventory_items) | name, currentStock, minimumStock, unit, expirationDate, unitCost (costo de 1 unidad) | — |
| `Coupon` (coupons) | code, type (`CouponType`), active, discountPercentage, customText, usageLimit, isUnlimited | — |
| `CakeSize` (cake_sizes) | name, price, maxRellenos, mixSponge, enabled | — |
| `CakeOption` (cake_options) | name, category ("Sponge", "Crema", "Fondant"), enabled | — |
| `GalleryImage` (gallery_images) | url, uploadedAt | — |
| `BusinessSetting` (business_settings) | key, value (ej: whatsapp) | — |

Enums: `OrderStatus` = PENDING → CONFIRMED → IN_PROGRESS → COMPLETED (o CANCELLED). `CouponType` = DESCUENTO, ENVIO_GRATIS, REGALO.

**Cadena clave del negocio:** Pedido → Ítem → Producto → Receta → Ítem de receta → Insumo. Al **confirmar** un pedido (`PUT /api/orders/{id}/confirm` o pasar de PENDING a CONFIRMED) se verifica el stock (con opción `force`), **se descuentan los insumos** del inventario y el pedido se une a su `Customer` (que se crea automáticamente si su teléfono no existe).

`OrderItem.cakeSizeId` es @Transient (solo viaja desde la tienda). El costo de una receta se calcula en `Recipe.getEstimatedCost()` / `isCostComplete()` (sin columna).

Limitaciones conocidas del modelo (no las "arregles" sin que te lo pidan; son decisiones pendientes de la tesis):
- `Order` sigue guardando nombre/teléfono/email como texto además de la relación con `Customer` (los pedidos viejos se asocian por teléfono).
- Las tortas del creador no tienen receta: no descuentan insumos ni entran en la ganancia estimada.
- Los roles son un String en `User`, no una tabla.

## Convenciones

- **Idioma**: la interfaz, los textos visibles, los mensajes de error y los comentarios van en **español (Argentina)**. Los nombres de clases, métodos y variables están en inglés; mantené esa mezcla.
- Comentarios didácticos: el código tiene comentarios que explican qué hace cada parte (es un proyecto académico que el autor tiene que defender). Al agregar código, comentá lo que no sea obvio, en español y en lenguaje simple.
- Usá Lombok como en las entidades existentes; inyección con `@Autowired`/constructor como en el código que rodea al cambio.
- Endpoints REST nuevos bajo `/api/...`, devolviendo `ResponseEntity`.
- Seguí el estilo del archivo que estás editando antes que cualquier preferencia propia.

## Cómo trabajar en este repo

- **Trabajá siempre en una rama**, nunca directo en `main` (la rama actual de trabajo de UI es `cambios-ui`).
- **Alcance del rediseño:** durante las fases del rediseño, el alcance lo define el prompt de cada fase. Dentro de ese alcance se pueden reorganizar HTML y CSS libremente (por ejemplo, mover estilos inline a archivos CSS). Fuera de él sigue valiendo la regla de cambios chicos y enfocados.
- **No borres archivos ni cambies el modelo de datos** (entidades) sin confirmación explícita: con `ddl-auto=update` un cambio de entidad altera la base real.
- **No modifiques `application.properties`** (tiene la conexión local a la base).
- Antes de dar algo por terminado, verificá que **compila** (`.\mvnw.cmd compile`) y, si es UI, revisá que se vea bien en **celular y PC** y en **ambos paneles** si tocaste JS compartido.
- Explicá los cambios en lenguaje simple: el autor tiene que poder entender y defender cada línea frente a sus profesores.
- No hagas commit ni push salvo que te lo pidan. Mensajes de commit en español.

## Pendientes de la tesis (contexto, no tareas automáticas)

- Diagramas por ingeniería inversa: **Casos de Uso** (actores: cliente, administrador, pastelero) y **Entidad-Relación**; también de Clases y de Dominio.
- Posible: costos por insumo/receta y reportes de márgenes, relación Pedido–Cliente, recordatorios automáticos de entrega, integración con WhatsApp.

## Diseño

- **Identidad fija:** la tipografía es **Poppins** y la paleta es la definida en `:root` de `static/css/main.css` y `static/css/admin.css` (`--accent: #e0487f`, `--accent2: #cf1810`, el rosa de fondo, y los colores de texto y muted). No agregar colores nuevos. Se permiten tonos más claros u oscuros derivados de esos mismos colores. Única excepción aprobada por el autor (Fase 7): un verde solo para los estados "Completado" / "Hay insumos" de los paneles.
- **Rediseño en curso (rama `cambios-ui`):** el diseño de la web pública se está rehaciendo por fases, con inspiración en magicspoon.com adaptada a una pastelería. El plan y las decisiones se guardan en `design-review/DESIGN-PLAN.md` y `PRODUCT.md` (se crean en la Fase 0). Cuando existan, seguirlos en cualquier cambio de interfaz.
- **Prioridad:** que sea simple de usar para personal no técnico y que se vea bien en celular. Lo vistoso nunca va por encima de lo claro.
- **Al usar skills de diseño** (Impeccable, Taste, Emil Kowalski), respetar esta identidad. No cambiar la tipografía ni la paleta aunque la skill lo sugiera.