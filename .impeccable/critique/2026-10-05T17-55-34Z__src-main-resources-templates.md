---
target: sitio público (templates)
total_score: 17
max_score: 40
na_heuristics: 
p0_count: 3
p1_count: 15
target_identity: "file:C:\\Users\\medin\\DreamBake\\src\\main\\resources\\templates"
timestamp: 2026-10-05T17-55-34Z
slug: src-main-resources-templates
---
## 1. Resumen de la auditoría

### 1.1 Cómo se relevó
- **Capturas** de página completa de `/`, `/products`, `/builder`, `/cart`, `/login`, `/register`, `/profile` en 375×812, 390×844, 768×1024, 1280×800 y 1440×900 → `design-review/before/<página>-<ancho>.png` (35 capturas) + 10 capturas de estados (`state-*.png`: menú móvil, menú de cuenta, botón flotante, mini-carrito, carrito con ítems, paso 2 del creador, tarjetas de producto simuladas).
  - El navegador de captura muestra una barra de scroll clásica de 15 px (el ancho útil es ancho − 15).
  - La base no tiene productos activos: las tarjetas de `state-products-mock-*.png` se inyectaron en el navegador con el mismo HTML del template.
- **Desborde horizontal** (`scrollWidth > clientWidth`) y **errores de consola** en cada página y ancho.
- **Contrastes WCAG** calculados sobre la paleta real.
- `/impeccable audit` (técnico), `/impeccable critique` (dos evaluaciones independientes: revisión de diseño + detector automático), checklist de `redesign-existing-projects` y de `emil-design-eng`.

### 1.2 Puntajes
| Auditoría técnica (`/impeccable audit`) | Puntaje | Hallazgo principal |
|---|---|---|
| Accesibilidad | 1/4 | Contrastes que fallan en todo el sitio, anillo de foco casi invisible, tarjetas del creador son `div onclick` (no se pueden usar con teclado), sin `prefers-reduced-motion` |
| Rendimiento | 1/4 | Video de 31,7 MB en autoplay también en celular; fotos de 3–6 MB en la home; agregar al carrito recarga la página |
| Responsive | 2/4 | Apila bien, pero el header desborda en 375/390, `100vw` genera scroll horizontal en todos los anchos, botones de 34–40 px |
| Theming / tokens | 1/4 | `:root` redeclarado en 7 archivos, ~108 estilos inline, 10 radios y 10+ sombras distintas, colores fuera de paleta |
| Integridad de implementación | 1/4 | Links rotos al login, código muerto, JS que se rompe en registro, galería vacía, clases sin definir |
| **Total** | **6/20 — Pobre** (requiere rediseño profundo) | |

| Heurísticas de Nielsen (`/impeccable critique`) | Puntaje |
|---|---|
| 1 Visibilidad del estado · 2 Lenguaje del usuario · 3 Control y libertad · 4 Consistencia · 5 Prevención de errores · 6 Reconocer antes que recordar · 7 Flexibilidad · 8 Estética minimalista · 9 Recuperación de errores · 10 Ayuda | 2 · 2 · 2 · **1** · 2 · 2 · 2 · 2 · **1** · **1** |
| **Total** | **17/40** (por debajo de la banda típica 20–32, sobre todo por links rotos y callejones sin salida en el camino de compra) |

**Veredicto de especificidad:** genérico. Cambiando el logo y las tres fotos de categoría, nada dice "Dulce Sueños, Posadas". Lo que la haría única está oculto o subutilizado: la frase "Porque soñar es lindo" está en el HTML pero escondida por CSS en todos los anchos (`.hero-sub{display:none}`); el header usa un círculo genérico en vez de `logo.svg`; la foto de Vero y Maca queda en un círculo de 140 px en celular; el creador de tortas (el diferencial) no tiene ni una imagen de torta.

**Detector automático (`impeccable detect`)** — dos pasadas independientes de la revisión de diseño:
- *Escaneo estático de templates:* 17 avisos. 13 son **falsos positivos** porque el escáner no entiende Thymeleaf: 10 "imagen rota" (`th:src` no se lee como `src`), "jerarquía tipográfica plana" en index (el CSS se enlaza con `th:href` y no se cargó), y "padding apretado" en `.product-image` (solo contiene una imagen a sangre). Los dos de `.public-modal-header` no se pudieron confirmar (modales ocultos). **Reales:** salto de encabezado h1 → h3 en products (línea 28 → 70), verde `#25D366` sobre blanco 2.0:1 en el modal de éxito (cart.html:107), `color:red` 4.0:1 en el error de login (login.html:25).
- *Overlay en el navegador (5 páginas):* confirma los fallos de contraste medidos sobre la página renderizada: títulos `#e0487f` sobre rosa 2.3–2.7:1 (home, products, login), botones con degradado 2.4:1, badge del carrito blanco sobre `#e0487f` 3.9:1, pasos inactivos del creador `#aaa` sobre blanco 2.3:1, grises sobre rosa en Nosotros/Promos/login (2.0–3.9:1), placeholder del newsletter 2.8:1. Los botones de 1.6:1 estaban deshabilitados al medir (WCAG los exime, pero igual se ven mal; ver 4.1). También detectó el patrón "borde fino + sombra ancha" en `#userMenu`.
- *Consola:* 404 de `/media/banner.jpg` (main.css:108) en la home y `TypeError` en register (5/5 anchos). Sin otros errores.
- **Coincidencias** entre revisión humana y detector: contraste del rosa como texto, grises sobre rosa, verde de WhatsApp, botón con degradado. **Lo que el detector no puede ver** y la revisión sí: links rotos, checkout que puede no abrir WhatsApp, header que desborda, creador sin teclado, emojis, galería vacía.

### 1.3 Hallazgos por severidad

**P0 — Bloquean el pedido o mandan al cliente a un callejón sin salida**
1. **Links de navegación y contacto llevan al login.** "Nosotros" y "Contacto" (menú desktop y móvil) apuntan a `/about` y `/contact`, que no existen; Spring Security responde 302 → `/login`. Lo mismo pasa con "Escribinos!" (home, sección Nosotros) y con el formulario de newsletter (`/subscribe`). *Todas las páginas, todos los anchos.*
2. **El botón de WhatsApp del header apunta a un placeholder** (`wa.me/549XXXXXXXXXX`). *Header, todos los anchos.*
3. **El checkout puede no abrir WhatsApp y aun así decir "¡Pedido Enviado!".** `window.open()` se ejecuta después de dos `await` (guardar el pedido y consumir el cupón); en celulares el navegador suele bloquear esa ventana porque ya no está ligada al toque. El modal de éxito aparece igual, el cupón ya se consumió y "Seguir comprando" conserva el carrito → riesgo de pedido doble. *`/cart`, `store.js` sección 7.* (Arreglo de comportamiento, no solo visual; se propone hacerlo en F5.)

**P1 — Dificultan mucho la tarea o violan WCAG AA**
4. **Header desborda en celular.** En 375/390: hamburguesa + marca en dos líneas + 4 botones de 40 px; el botón de cuenta queda cortado a la derecha (sale a x=380 en un viewport de 360) y la página se puede desplazar de costado. *Todas las páginas · 375, 390.*
5. **Scroll horizontal por `width:100vw`** en `.hero-hero` y `.hero-bg` (100vw incluye la barra de scroll): desborda 7–8 px en 375, 390, 768, 1280 y 1440. *Home.*
6. **Contraste insuficiente en todo el sitio:** `#e0487f` sobre el rosa de fondo = 2.72:1 (todos los títulos de sección, H1 de páginas, precios); blanco sobre `#e0487f` = 3.89:1 en botones de 14–16 px; degradado `#ff7aa8 → #d43b73` con texto blanco ≈ 2.4:1 en el inicio ("Hace tu torta", "Entrar", "Escribinos!", "Suscribirse"); `#777`/`#8a8a8a` sobre rosa ≈ 2.3–3.1:1; verde `#25D366` sobre blanco = 1.98:1 (mensaje de cupón y título del modal de éxito); botón deshabilitado `#f5c2c7` con texto blanco = 1.56:1.
7. **Foco de teclado casi invisible:** `:focus{outline:none}` reemplazado por un contorno `rgba(224,72,127,.18)`. *Todas las páginas.*
8. **Creador no usable con teclado ni lector de pantalla:** tamaños, coberturas, bizcochuelos y rellenos son `div` con `onclick`, sin `role`, sin `tabindex`, sin `aria-pressed`. *`/builder`.*
9. **Botones e inputs en Arial:** `button`, `input`, `select` y `textarea` no heredan Poppins ("Siguiente Paso", "+ Agregar al pedido", "Entrar", "Crear cuenta", inputs de login, registro y perfil). *Builder, products, login, register, profile, cart.*
10. **Video de 31,7 MB en autoplay también en celular**, más fotos de categoría de 5,8 / 3,8 / 3,3 MB y banner de 3 MB. Para alguien que entra desde Instagram con datos móviles, la home pesa más de 45 MB. *Home.*
11. **Registro: validación de contraseñas rota.** `register.js` hace `togglePass.addEventListener` sobre `#toggle-password`, que no existe en register.html → `TypeError` al cargar (aparece en consola en los 5 anchos) y los listeners de "las contraseñas no coinciden" nunca se registran. *`/register`.*
12. **Registro sin header ni footer ni link a "Iniciá sesión":** no hay forma de volver. La tarjeta queda pegada arriba en desktop. *`/register`, todos los anchos.*
13. **Login: el menú móvil no funciona** (la página no carga `header.js`) y el botón 👁️ de mostrar contraseña no hace nada (ningún script lo maneja ahí). *`/login`.*
14. **Emojis usados como íconos** (prohibido por CLAUDE.md): 🎂 🔍 (products), 🎂 ☁️ 🎨 ➡️ ⬅️ 🛒 (builder), 🛒 🎉 (cart), 🛒 ➡️ (botón flotante y mini-carrito), ❤️ (home), 👁️ (login), ✅ ❌ (perfil).
15. **Galería vacía:** el título "Galería" se muestra sobre un `#wheelCarousel` sin contenido (el template no tiene el markup que espera `wheel-carousel.js` y `/api/gallery` requiere login). Se lee como sitio abandonado. *Home, todos los anchos.*
16. **Imagen del banner rota:** `.phrase-banner` usa `url("../media/banner.jpg")`, pero el archivo está en `/img/banner.jpg` (y `/media/**` redirige al login). Queda un bloque rosa plano de 280 px con una frase chica. *Home.*
17. **Áreas táctiles chicas:** íconos del header 40×40, hamburguesa 38×32, "+ Agregar al pedido" ≈ 34 px de alto, quitar del carrito (× de 16 px), checkbox "Recordarme" 13×13, input del newsletter ≈ 32 px, mostrar contraseña 34×33.
18. **Sin `prefers-reduced-motion`** en ningún CSS ni JS (video, carrusel por `requestAnimationFrame`, fadeIn del creador, hovers).

**P2 — Molestias, inconsistencias, hay forma de seguir**
19. **Cinco estilos de botón distintos:** `.btn-primary` con degradado (hero/login), `.btn-primary` sólido (register.css lo redefine), `.btn-pink` sólido, `.about-btn` y `.promo-btn` (copias del degradado), `.btn-grey`; además `.btn` está definido en `hero.css` y en `register.css` con valores distintos, y no existe en el resto de las páginas.
20. **Tokens dispersos:** `:root` redeclarado en 7 archivos; `--accent` repetido 5 veces; `--text` pasa a `rgba(34,34,34,.85)` en la home por `hero.css`; radios 6/8/9/10/12/14/16/20/50 %/999 px; más de 10 sombras distintas; colores fuera de paleta (`#25D366`, `red`, `#2563eb`, `#d1fae5`, `#fee2e2`, `#dc3545`, degradado de Instagram).
21. **~108 estilos inline** en los templates (products 25, cart 33, profile 33, builder 9, register 5, login 2, header 1) + todo el HTML que genera `store.js`.
22. **Tú y vos mezclados:** "Armá tu torta" junto a "Sigue los pasos", "Elige el Tamaño", "Cuéntanos", "Escribe aquí", "Repite la contraseña", "¡Ve a armar una torta!". Y el CTA principal tiene un error: "Hace tu torta" (falta la tilde de "Hacé").
23. **Nombre interno "DreamBake" visible al cliente:** títulos de pestaña de builder y cart, y modal de éxito ("confiar en DreamBake"). Los `<title>` usan tres formatos distintos y solo products tiene meta description.
24. **Tres footers distintos** (index/builder/cart/login: `footer` simple; products/profile: `.site-footer` con padding extra; register: ninguno). Debajo del footer queda una franja rosa vacía (≈230 px en index-375) por el `margin-top` negativo y el `min-height:130vh` del hero.
25. **Hero móvil de 130vh:** los CTAs flotan a mitad de una foto y 40 % de la pantalla es torta sin contenido. *Home · 375/390.*
26. **Grilla de la home sin contenedor:** en 1440 las tarjetas llegan casi al borde (12 px) mientras el header se limita a 1100 px; en 768 queda una tarjeta huérfana (2+1). Las tarjetas Dulces/Tortas/Souvenirs tienen hover de "clickeable" pero no son links.
27. **Creador:** paso 2 titulado "3. Sabor de Bizcochuelo"; `#grid-sponges` usa `.options-grid`, que no existe en ningún CSS (una columna sin separación incluso en desktop); tras `scrollIntoView` el header tapa el título (sin `scroll-margin-top`); en 375 el botón "Siguiente" queda ~1.100 px debajo del título; "Volver" y "Siguiente" con alturas distintas y la flecha emoji en otra línea; no hay resumen ni precio visible después del paso 1; el estado hover (#ccc) se confunde con el seleccionado. *`/builder` · 375 y 1280.*
28. **Tarjetas blancas pegadas al borde derecho en 375** (creador, carrito, registro, perfil): `.container-wizard` con 2,5 rem de padding interno y sin margen externo.
29. **Dos carritos a la vez:** ícono del header + botón flotante "🛒 Ver Pedido (2)" que además tapa contenido (estado vacío en products-375, tercera columna de rellenos en builder-1280). El badge muestra "0" con el carrito vacío.
30. **Agregar al pedido recarga la página entera** (`window.location.reload()`), sin confirmación visual. *`/products`.*
31. **Ítems del carrito ilegibles:** el detalle de la torta es un solo texto separado por " | "; "Catálogo general" aparece como descripción; quitar es una × roja de 16 px con `title` y sin `aria-label`. *`/cart` y mini-carrito.*
32. **Formulario de checkout:** labels sin `for`/`id`; numeración "1.–4." y después "*" sin leyenda; `#chk-phone` es `type="text"` (abre el teclado de letras); sin `autocomplete`; el cupón pesa visualmente igual que el pedido; el total no muestra el descuento aplicado; no se pregunta retiro o envío.
33. **"Recordarme" no hace nada** (Spring no tiene `rememberMe()` configurado) y no hay mensaje de "cuenta creada" después de registrarse.
34. **El personal PASTELERO no tiene link a su panel** en el menú de cuenta (solo existe el de ADMIN).
35. **Modales:** sin cierre con Escape ni click en el fondo; el × es el carácter `&times;` sin `aria-label`; aparecen sin transición; títulos genéricos ("Atención").
36. **Página de productos vacía para el cliente:** con la base vacía solo se ve "No se encontraron productos" (el único botón es para ADMIN). La imagen faltante es una caja gris con "Sin imagen".
37. **`store.js` pide `/api/cake-config/public` en todas las páginas**, aunque solo lo necesita el creador.
38. **`label` "Tu email" visible** porque `.visually-hidden` no está definida en ningún CSS; el botón "Suscribirse" queda fuera del formulario, debajo de la nota. *Home · todos.*

**P3 — Pulido**
39. Títulos en *Title Case* ("Nuestros Productos", "Sobre Nosotros", "Resumen de Compra", "Datos Personales") mezclados con *sentence case*.
40. Comillas rectas `"…"` en la frase del banner; falta "¡" en "Escribinos!".
41. `alt="WA"` / `alt="IG"` redundantes con el `aria-label`; íconos PNG en vez de SVG.
42. `<source>` a `hero-loop.webm`, que no existe (404 silencioso).
43. Comentarios internos en el HTML ("Punto 2", "Punto 3", "Punto 5") y CSS comentado (login.css).
44. Pesos de Poppins distintos por página; la home usa 800 sin cargarlo.
45. Sin favicon, sin etiquetas Open Graph (importante: los links se comparten por WhatsApp e Instagram y hoy no muestran vista previa).
46. Placeholder de teléfono con formato de Buenos Aires ("+54 9 11 …") para un público de Posadas (376).

### 1.4 Movimiento e interacción (checklist de Emil Kowalski)

| Antes | Después | Por qué |
|---|---|---|
| `.select-card { transition: 0.2s }`, `.step { transition: 0.3s }`, `.btn-large { transition: 0.2s }` | `transition: background-color 160ms var(--ease-out), border-color 160ms, transform 120ms` | Nombrar propiedades; `all` anima cosas que no queremos |
| Ningún elemento tiene `:active` | `:active { transform: scale(.97) }` en botones, tarjetas de opción y chips | El toque tiene que sentirse "escuchado" |
| `.btn-icon:hover { translateY(-2px) }`, `.product-card:hover { translateY(-6px) }`, `.floating-cart:hover { scale(1.05) }` sin media query | Envolver en `@media (hover: hover) and (pointer: fine)` | En celular el hover queda "pegado" después de tocar |
| Menú móvil, menú de cuenta y modales aparecen/desaparecen de golpe (`hidden`) | Menú: `translateY(-8px)`+opacidad 220ms `--ease-drawer`; dropdown: `scale(.96)` desde la esquina del botón; panel de carrito: desliza 320ms, sale en 200ms | Evitar cambios bruscos; las salidas más rápidas que las entradas |
| Dropdown de cuenta con `transform-origin` por defecto | `transform-origin: top right` | Los popovers nacen de su disparador (los modales sí van centrados) |
| `.wizard-step { animation: fadeIn 0.4s ease }` (keyframes) | Transición de opacidad + `translateX(16px)` 320ms `--ease-in-out`, interrumpible | Cambiar de paso rápido no debe "reiniciar" la animación |
| `goToStep()` → `scrollIntoView({behavior:'smooth'})` y el header tapa el título | `scroll-margin-top: calc(header + 16px)` en `#cake-builder` | El destino tiene que quedar visible |
| Carrusel por `requestAnimationFrame` siempre activo y `will-change: transform` permanente en imágenes | Marquee CSS con `animation` lineal, `will-change` solo durante la animación, pausa en hover/foco | Menos trabajo en el hilo principal |
| Agregar al carrito → `window.location.reload()` | Actualizar en el lugar + "pop" del badge (`--ease-pop`, 220ms) + toast | Feedback inmediato sin perder el scroll |
| `.pulse-animation` con `scale(1.1)` y `box-shadow` animado (definida, sin uso) | Eliminar | Animar `box-shadow` es caro y no se usa |
| Video y carrusel ignoran `prefers-reduced-motion` | Póster estático, marquee detenido, solo fundidos | Accesibilidad (mareo por movimiento) |
| Product image `transition: transform 300ms cubic-bezier(.2,.8,.2,1)` + `scale(1.06)` | Mantener la idea con `--ease-out` y `scale(1.04)` solo con puntero fino | Hover sutil, no "salta" |

### 1.5 Lo que funciona (y se conserva)
- El **flujo correcto ya existe**: creador → carrito → pedido guardado en la base → WhatsApp con número de referencia. El mínimo de 3 días se aplica en el selector de fecha y "Siguiente" queda deshabilitado hasta que el paso es válido.
- **Material propio de calidad**: logo ilustrado, foto real de Vero y Maca, video y fotos de productos, frase de marca cálida. El rediseño no necesita assets nuevos (solo comprimirlos).
- **Una sola tipografía y una paleta acotada**: la base de identidad es coherente.
- Datos precargados en el checkout cuando hay sesión, y filtro/búsqueda en productos.

---
