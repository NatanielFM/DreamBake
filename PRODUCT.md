# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Clientas y clientes de Dulce Sueños Posadas** (público principal del sitio público). Personas de Posadas, Misiones (Argentina) que quieren encargar una torta personalizada, una mesa dulce, dulces o souvenirs para un cumpleaños, un bautismo o un evento. Llegan casi siempre **desde el celular**, a partir de un link en Instagram o WhatsApp. Su tarea: ver qué se hace, tentarse, armar su torta o elegir productos, y mandar el pedido por WhatsApp con fecha de entrega (mínimo 3 días de anticipación).
- **Personal de la pastelería** (Vero y Maca, más el equipo de pastelería). No son personas técnicas. Usan el panel de administración y el panel del pastelero, y también entran al sitio público para iniciar sesión. Si el sistema les resulta complicado, prefieren volver al papel.

## Product Purpose

DreamBake es el sistema web de gestión de la pastelería artesanal **Dulce Sueños Posadas**. La parte pública funciona como vidriera y tienda: catálogo de productos, creador de tortas paso a paso, carrito y checkout que registra el pedido en la base de datos y abre WhatsApp con el mensaje armado. La parte interna centraliza pedidos, clientes, inventario, recetas, equipo y fidelización, que antes estaban dispersos en un pizarrón, WhatsApp/Instagram y un cuaderno.

Éxito en el sitio público: que una persona que llega desde Instagram en el celular entienda en segundos qué ofrece la pastelería, confíe en la calidad y termine un pedido por WhatsApp sin ayuda.

Es el trabajo final (tesis) de Nataniel Medina, Técnico Superior en Analista de Sistemas (IPESMI, ciclo 2026). Cada decisión tiene que poder explicarse y defenderse.

## Positioning

Pastelería artesanal y local, atendida por sus dueñas (Vero y Maca), que hace tortas **personalizadas** con temática a pedido. Lo distintivo es el creador de tortas: la persona elige tamaño, cobertura (crema o fondant), sabores de bizcochuelo y rellenos, ve el precio al instante y lo manda por WhatsApp. Es la experiencia de "encargar como en el mostrador", pero desde el teléfono.

## Operating Context

- El tráfico entra desde Instagram (@dulcessuenos_posadas) y WhatsApp; la conversación final del pedido sigue por WhatsApp.
- El checkout guarda el pedido (`POST /api/orders`) y después abre `wa.me` con el número de referencia, los ítems, la fecha, el medio de pago y el cupón.
- Los pedidos requieren **mínimo 3 días de anticipación**.
- Formas de recibir el pedido: **retiro y envío** (existe un cupón de envío gratis).
- Medios de pago ofrecidos en el checkout: transferencia bancaria y efectivo en el local.
- Cupones de descuento, envío gratis o regalo se validan en el carrito.
- Promociones existentes en el contenido: "Cliente frecuente" y "Promo cumpleañero".

## Capabilities and Constraints

- Stack fijo: Spring Boot + Thymeleaf, HTML/CSS/JS vanilla sin build step ni npm. Poppins por Google Fonts.
- Las páginas públicas son `/`, `/products`, `/builder`, `/cart`, `/login`, `/register`, `/profile`, con un header compartido (`fragments/header.html`).
- El carrito vive en `localStorage` (`dreamBakeCart`) y lo maneja `static/js/public/store.js`, que también genera HTML dinámico (mini-carrito, botón flotante, tarjetas del creador).
- Los tamaños, bizcochuelos y rellenos del creador vienen de `/api/cake-config/public` y los configura el admin.
- Los productos y categorías del catálogo vienen de la base y los carga el admin (con imagen opcional).
- **Navegación:** "Nosotros" y "Contacto" pasan a ser anclas de secciones en la home (`/#nosotros`, `/#contacto`). Las rutas `/about` y `/contact` no existen y no se van a crear.
- **Newsletter:** la sección de suscripción se reemplaza por llamados a seguir en Instagram y escribir por WhatsApp. El endpoint `/subscribe` no existe y no se crea.
- **WhatsApp del negocio (pendiente, fuera del rediseño visual):** la clienta quiere poder elegir desde el panel de administración el número de WhatsApp al que llegan los mensajes del checkout. Hoy está fijo en `store.js` (`5493764159312`) y el header tiene un placeholder (`549XXXXXXXXXX`). Implementarlo requiere guardar una configuración nueva en la base (cambio de modelo), así que necesita confirmación explícita y va en una fase propia. Mientras tanto, el número vigente es `5493764159312`.
- No cambiar entidades ni `application.properties` sin confirmación.

## Brand Commitments

- Nombre comercial: **Dulce Sueños Posadas**. Frase de marca existente: "Porque soñar es lindo". Otra frase en uso: "Un pedacito de amor en forma de torta."
- Logo existente: `src/main/resources/static/img/logo.svg`.
- Tipografía **Poppins** y paleta existente (`--accent #e0487f`, `--accent2 #cf1810`, el rosa de fondo, texto y muted) son fijas. Se permiten tonos derivados, no colores nuevos.
- Íconos en SVG inline, nunca emojis como íconos.
- Idioma: español de Argentina (voseo: "Armá tu torta", "Escribinos").
- Tono: cálido, alegre, premium pero cercano.

## Evidence on Hand

- Las fotos y el video originales eran de prueba y se borraron en la Fase 2 (confirmado por el autor el 2026-10-05), incluida `about/veroymaca.jpg`: **no hay ninguna foto real de las dueñas**. No usar fotos de otras personas presentadas como Vero y Maca.
- Fotos actuales: **stock de Unsplash (licencia libre) como marcador de posición**, en WebP livianos: `static/img/hero/`, `categorias/`, `galeria/`, `promos/`. Cada una tiene su origen guardado en un archivo `.json` al lado. Antes de publicar el sitio conviene reemplazarlas por fotos reales del Instagram del negocio; mientras tanto la galería se presenta como "Ideas para tu próxima torta", no como trabajos propios.
- Imágenes que sube el admin: `uploads/` (servidas en `/uploads/**`) y la galería (`GalleryImage`). La home muestra primero las fotos de la galería del admin y completa con las de ejemplo si hay menos de 6.
- Texto de "Sobre nosotros" con Vero y Maca (en `index.html`).
- Logo: `static/img/logo.svg` (pesa 491 KB porque tiene un PNG adentro; en el header y el footer se usa un isotipo SVG liviano).
- **No existen** testimonios, reseñas, cantidad de clientes, premios ni prensa. No inventarlos. Tampoco existe un dato de "más vendido" en el modelo: cualquier sello de ese tipo tiene que basarse en una decisión explícita, no en un número inventado.

## Product Principles

1. **Simple antes que vistoso.** Si una persona no técnica duda, el diseño falló. Lo vistoso nunca va por encima de lo claro.
2. **Celular primero.** El recorrido Instagram → producto → pedido por WhatsApp tiene que ser impecable en un teléfono de 375 px.
3. **Del antojo al pedido en pocos toques.** Cada pantalla empuja hacia armar la torta o enviar el pedido, sin callejones sin salida.
4. **Artesanal y real.** Mostrar el trabajo verdadero (fotos propias, las dueñas, el proceso) y no prometer nada que el negocio no haga.
5. **Defendible.** Cada parte tiene que poder explicarse en una mesa de examen: código ordenado, comentado y sin magia.

## Accessibility & Inclusion

- Datos personales de clientes tratados con seguridad moderada (Ley 25.326 de Protección de Datos Personales).
- Público general de todas las edades navegando en celulares de gama media: contraste legible, áreas táctiles cómodas (mínimo 44 px) y textos claros.
- Respetar `prefers-reduced-motion` en animaciones y en el video/carrusel automático.
