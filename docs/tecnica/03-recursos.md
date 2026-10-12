# 3. Plantillas, estilos y scripts

[← Volver al índice](../DOCUMENTACION-TECNICA.md)

Todo lo que no es Java está en `src/main/resources/` (más el archivo de configuración de ejemplo en `config/`). El frontend no usa frameworks ni un paso de compilación: son plantillas HTML que completa el servidor (Thymeleaf), CSS y JavaScript "a mano". Regla importante: **no hay JavaScript dentro del HTML** (ni `<script>…</script>` ni `onclick="…"`), porque la política de seguridad del sitio (CSP) lo prohíbe; cada página carga sus archivos `.js` y los botones se conectan con `addEventListener`.

## 3.1 Configuración

| Archivo | Qué configura |
|---|---|
| `src/main/resources/application.properties` | Configuración de **desarrollo**: conexión a MySQL (`localhost:3306/dreambake_db`), `ddl-auto=update` (Hibernate crea y actualiza las tablas solo) y `show-sql`. **Contiene la contraseña de la base de desarrollo**: ver [limitaciones](06-limitaciones-y-mejoras.md#seguridad-y-datos). Por regla del proyecto no se modifica. |
| `src/main/resources/application-prod.properties` | Perfil **`prod`** (se activa con `--spring.profiles.active=prod`): la base y la clave de la sesión salen de variables de entorno, cookies `Secure` y `HttpOnly`, sesión de 60 min, errores sin detalles, DevTools apagado, caché de plantillas. No se usó todavía en un despliegue real. |
| `config/application.ejemplo.properties` (raíz del proyecto) | **Plantilla** de la configuración local, con cada clave explicada: zona horaria, base y clave de sesión para producción, `/actuator/health`, datos del negocio, correo (SMTP), Google, Facebook, Gemini y backups. Se copia como `config/application.properties` (que **no** se sube a Git) y se completa. |

Lo que se escribe en `config/application.properties` **pisa** a lo de `src/main/resources/application.properties`. Una clave vacía solo desactiva su función (lo avisa `ConfigurationCheck` en el log). Ver [Instalación](07-instalacion.md).

## 3.2 Plantillas (`templates/`)

Son HTML con atributos `th:*` de Thymeleaf que el servidor completa antes de enviar la página. Todo texto que viene de la base se muestra con `th:text`, que lo escapa (un `<script>` escrito por alguien se ve como texto y no se ejecuta).

**Tienda pública**
| Plantilla | Ruta | Qué muestra |
|---|---|---|
| `index.html` | `/` | Inicio: portada, vitrina de productos con el sticker "Más vendido", el armador en 3 pasos, nosotros, galería, promociones y contacto. |
| `products.html` | `/products` | Catálogo con buscador y filtro por categoría. |
| `builder.html` | `/builder` | Armador de tortas paso a paso (tamaño, bizcochuelo, rellenos, detalles, resumen) con precio al instante. |
| `cart.html` | `/cart` | Carrito y checkout: productos, datos, entrega, cupón, fecha mínima según la anticipación, y la pantalla de éxito con el código de seguimiento. |
| `seguimiento.html` | `/seguimiento` | Formulario del código y línea de tiempo del pedido. |
| `privacidad.html`, `terminos.html`, `eliminar-datos.html` | `/privacidad`, `/terminos`, `/eliminar-datos` | Páginas legales. **Tienen marcadores `[COMPLETAR: …]`** (razón social, CUIT, plazos) que hay que llenar antes de publicar. |
| `baja.html` | `/baja` | Darse de baja de los emails promocionales (pregunta primero; no indexable). |

**Cuentas**
| Plantilla | Qué muestra |
|---|---|
| `login.html`, `register.html` | Inicio de sesión (con "Mantener la sesión iniciada") y registro, con los botones de Google y Facebook si están configurados. |
| `recuperar.html`, `recuperar-nueva.html` | "Olvidé mi contraseña": pedir el link y elegir la contraseña nueva. |
| `completar-email.html` | "Completá tu email" para quien entra con Facebook sin email. |
| `profile.html` | "Mi cuenta": datos, "Mis pedidos", confirmar email, cambiar contraseña, vincular Google, eliminar la cuenta. |

**Paneles**
| Plantilla | Qué es |
|---|---|
| `admin.html` | Panel de administración: **una sola página con pestañas** (`data-tab`): Resumen, Pedidos, Productos, Inventario, Recetas, Clientes, Galería, Armador, Equipo, Registro de actividad, Backups y Ajustes. Contiene todas las ventanas (modales) del panel. |
| `baker.html` | Panel del pastelero: Pedidos, Inventario y Recetas. Reutiliza los JS del admin y oculta con su propio `<style>` los botones que no corresponden; **los permisos reales los controla el servidor** (ver [Seguridad](05-seguridad.md#qué-ve-y-qué-puede-hacer-el-pastelero)). |

**Fragmentos (`fragments/`)** — piezas que se insertan en varias páginas con `th:replace`:
- `header.html` — Encabezado de la tienda (menú, cuenta, carrito; también carga `format.js`).
- `footer.html` — Pie con los datos del negocio (`${negocio}`), enlaces legales y aviso de cookies.
- `seo.html` — `icons` (íconos en todos los tamaños, lo usan todas las páginas) y `share(...)` (descripción, canonical y Open Graph de las páginas públicas).
- `social-login.html` — Botones "Continuar con Google" y "Continuar con Facebook".
- `store-notice.html` — Avisos de la tienda: pedidos pausados y mensaje opcional del negocio.

**Páginas de error (`error/` y `error.html`)** — `error/layout.html` es el diseño común (sin header ni footer, textos fijos, nunca detalles técnicos); `error/404.html`, `403.html`, `500.html` y `error.html` (el resto de los códigos) lo usan.

**Emails (`email/`)** — Todos usan el diseño común `email/base.html` (tablas y estilos en línea, porque los programas de correo no entienden CSS moderno; logo incrustado).
| Plantilla | Cuándo se manda |
|---|---|
| `pedidorecibido.html` | Al cliente, al hacer un pedido en la tienda. |
| `nuevopedido.html` | Al negocio, cuando entra un pedido nuevo. |
| `pedidoestado.html` | Al cliente, cuando su pedido se confirma, está listo o se cancela. |
| `recordatoriopedido.html` | Al cliente, el día antes de la entrega. |
| `resumenentregas.html` | Al negocio, todos los días a las 8:00 (entregas de los próximos 3 días). |
| `pedido-detalle.html` | Bloque reutilizable con el detalle del pedido y el código de seguimiento (lo incluyen los anteriores). |
| `cumpleanos.html` | Saludo de cumpleaños con cupón. |
| `campana.html` | Campañas de promociones (todo el contenido es texto escapado). |
| `codigo.html` | Código de 6 dígitos para confirmar un email. |
| `recuperar-contrasena.html`, `recuperar-social.html`, `contrasena-cambiada.html` | "Olvidé mi contraseña" (link, aviso para cuentas de Google/Facebook) y "Tu contraseña fue cambiada". |
| `recordatorio-tarea.html` | Recordatorio del personal, una hora antes. |
| `auditoria-alerta.html`, `backup-alerta.html` | A los administradores: auditoría alterada o backup fallido. |
| `prueba.html` | Email de prueba del panel. |

## 3.3 Estilos (`static/css/`)

| Archivo | Para qué |
|---|---|
| `tokens.css` | **Variables de diseño** de todo el sitio: paleta (acento rosa `#e0487f`), tipografía Poppins, tamaños, espacios, radios y duraciones. Cualquier color nuevo sale de acá. |
| `components.css` | Base y componentes compartidos: botones, campos, tarjetas, foco visible, contenedores. |
| `main.css` | Base vieja del proyecto. Se sigue cargando primero y **no se modifica** (decisión del rediseño). |
| `header.css`, `footer.css` | Encabezado fijo y pie de la tienda. |
| `public.css` | Piezas comunes de la tienda: encabezado rosa de las páginas, mini carrito, avisos. |
| `home.css`, `products.css`, `builder.css`, `cart.css`, `tracking.css`, `legal.css`, `profile.css` | Una hoja por página pública. |
| `login.css`, `register.css`, `baja.css` | Páginas de acceso (dos columnas en escritorio) y la de baja. |
| `error.css` | Páginas de error. |
| `admin.css` | **Los dos paneles** (admin y pastelero): barra lateral, tablas que en el celular se ven como tarjetas, ventanas, insignias de estado, modo claro y oscuro (variables `--admin-*`). |
| `bakey.css` | La ventana del asistente Bakey en los paneles. |

## 3.4 Scripts (`static/js/`)

**Comunes**
- `csrf.js` — Se carga **primero en todas las páginas**: agrega el token anti-CSRF (cookie `XSRF-TOKEN`) a cada `fetch` que modifica datos.
- `format.js` — `DreamFormat`: precios (`$ 12.345,50`) y fechas (`12/10/2026`) en formato argentino, y la fecha local de hoy.
- `header.js` — Menú del celular, menú de la cuenta y animaciones del encabezado.
- `cookie-notice.js` — Aviso de cookies del pie (se recuerda en el navegador).
- `form-helpers.js` — `DreamForm`: mostrar/ocultar contraseña, errores por campo y estado "cargando" en los formularios de cuenta.

**Tienda**
- `public/store.js` — La lógica de la tienda: configuración del armador (desde `/api/cake-config/public`), carrito en `localStorage` (`dreamBakeCart`), cupones, checkout (`POST /api/orders`), validaciones de fecha y la pantalla de éxito.
- `public/home.js` — Animaciones de aparición del inicio. `wheel-carousel.js` — carrusel de la galería del inicio.
- `public/catalog.js` — Búsqueda y filtro por categoría del catálogo.
- `login.js`, `register.js`, `recuperar.js`, `complete-email.js`, `profile.js` — Validaciones y mejoras de cada formulario de cuenta. El servidor valida todo igual.

**Paneles (`admin/`)** — un archivo por pestaña. Los marcados con ★ los carga **también** `baker.html`: un cambio ahí afecta a los dos paneles.
| Archivo | Pestaña o función |
|---|---|
| `main.js` ★ | Barra lateral, cambio de pestañas, ventanas globales `showConfirmModal` / `showErrorAlert`, cierre con Esc y `data-close-modal`, registro de descargas. |
| `ui.js` ★ | `DreamUI`: bloquear el botón mientras se envía (evita pedidos duplicados por doble clic), mensajes de "Cargando", vacío y error en tablas, mostrar el mensaje del servidor. |
| `theme-init.js` ★ | Aplica el modo oscuro antes de dibujar la página (va en el `<head>`). |
| `orders.js` ★ | Pedidos: calendario, listas Nuevos / Agendados / Completados, confirmar, entregar, ver, editar, pedido manual, recibo e impresión. Funciona sin los campos de dinero (pastelero). |
| `inventory.js` ★ | Inventario: lista con alertas de stock bajo y vencimiento, filtros, lista de compras; la columna de costo solo si existe (admin). |
| `recipes.js` ★ | Recetas: ver y, en el admin, crear y editar; costos solo si `<body data-panel="admin">`. |
| `reminders.js` ★ | Recordatorios: caja del día, ventana para crear y aviso cuando llega la hora. |
| `notifications-simple.js` ★ | Campanita: número de no leídos, lista, marcar leído, actualización cada 60 s y sonido opcional. |
| `dashboard.js` | Resumen: tarjetas, gráficos (Chart.js), ganancia estimada, uso de Bakey e informe descargable. |
| `products.js` | Productos y categorías. |
| `clients.js` | Clientes, etiquetas y cupones. |
| `loyalty.js`, `campaigns.js` | Fidelización: cumpleaños y campañas de email con vista previa. |
| `gallery.js` | Galería: achica y convierte a JPG las fotos antes de subirlas. |
| `cake-config.js` | Armador de tortas: tamaños, precios y sabores. |
| `team.js` | Equipo: alta y baja de pasteleros. |
| `audit.js` | Registro de actividad: filtros, paginación, detalle, CSV/PDF y verificar integridad. |
| `backups.js` | Backups: estado, lista, descarga, backup manual y restaurar. |
| `settings.js` | Ajustes: datos del negocio y correo de envío. |

`bakey.js` (en la raíz de `js/`) arma la ventana del asistente en los dos paneles; el texto del modelo se dibuja con `textContent`, nunca como HTML.

## 3.5 Imágenes y archivos estáticos

- `static/img/logo.svg` (logo maestro), `logo-email.png` (logo para los emails), `favicon.svg`, `icons/` (íconos para celulares) y `static/favicon.ico` + `static/site.webmanifest`.
- `static/img/og-marca.jpg` — Imagen 1200×630 para la vista previa al compartir un link.
- `static/img/galeria/`, `categorias/`, `promos/` — Fotos de muestra (marcadores de posición de Unsplash, uso libre). Cada una tiene al lado un `.json` con su origen y licencia. **Hay que reemplazarlas por fotos reales del negocio antes de publicar.**
- `uploads/` (fuera de `src`, no versionada) — Fotos subidas desde el panel, servidas en `/uploads/**`.
