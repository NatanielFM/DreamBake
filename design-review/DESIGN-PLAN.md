# DESIGN-PLAN — Rediseño del sitio público de DreamBake

> Fase 0 (investigación y planificación). Este documento no cambia código: define qué se va a hacer en las fases siguientes y qué no se puede romper.
> Fuentes: `PRODUCT.md`, `CLAUDE.md`, capturas en `design-review/before/`, `/impeccable audit` + `/impeccable critique`, skills `redesign-existing-projects` (Taste) y `emil-design-eng` (Emil Kowalski).
> Fecha: 2026-10-05 · Rama: `cambios-ui`

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

## 2. Dirección de diseño

### "La vidriera de Dulce Sueños"

El sitio tiene que sentirse como pararse frente a la vidriera de una pastelería un sábado a la tarde: cajas rosas con cinta, tortas brillantes sobre bandejas, etiquetas de precio escritas a mano y stickers pegados en las cajas. De **Magic Spoon** tomamos la *seguridad* del lenguaje visual, no su contenido: titulares enormes y pesados (Poppins 800 con el interletrado ajustado), secciones completas de borde a borde pintadas con tintes suaves del rosa de la marca, botones píldora de alto contraste, stickers girados que señalan lo importante ("Hecho a mano", "Desde $44.000"), tarjetas de producto donde la foto aparece centrada sobre un "plato" de color, una cinta (marquee) que recorre la pantalla, mucho aire y un ritmo vertical claro, y un header fijo y limpio con el carrito siempre visible. La adaptación a una pastelería artesanal está en tres decisiones: **(1)** fotos reales de los productos en vez de ilustración plana de caja de cereal; **(2)** un motivo propio de borde festoneado (el borde de un papel de torta o de una puntilla) que separa las secciones de color; **(3)** la "tinta" del sitio no es negro ni azul marino como en Magic Spoon, sino un ciruela profundo derivado del mismo `--accent` (#481729), que suma calidez de chocolate-frambuesa sin agregar un color nuevo.

**Mood:** cálido, alegre, goloso, prolijo. Premium pero de barrio: se tiene que notar que lo hacen dos personas con las manos, no una cadena. Nada de brillos, degradados violetas, vidrio esmerilado ni "efectos tech". El color es el protagonista; las sombras casi no existen (solo en lo que flota: menús, modales, botón de carrito). La simpatía viene de los stickers, del festoneado y de los textos en voseo, nunca de emojis.

**Lo que NO tomamos de Magic Spoon:** su paleta multicolor por producto, sus ilustraciones, su tipografía, sus textos, sus suscripciones, su densidad de promociones.

**Regla de oro (de PRODUCT.md):** si una persona no técnica duda en algún paso, el diseño falló. Lo vistoso nunca va por encima de lo claro.

---

## 3. Sistema de diseño (Fase 1)

Todo vive en un archivo nuevo de tokens para el sitio público (ver "Estrategia de CSS" al final de esta sección). Los valores hexadecimales derivados se calcularon mezclando cada color base con blanco (tintes) o con negro (sombras); no hay tonos nuevos.

### 3.1 Color

**Colores base (fijos, existentes):** `--accent #e0487f`, `--accent2 #cf1810`, rosa de fondo `rgba(240,156,156,.5)` (= `#f09c9c` al 50 %, que sobre blanco da `#f8cece`), texto `#222`, muted `#777`.

| Token | Hex | Derivado de | Uso | Contraste clave |
|---|---|---|---|---|
| `--rose-50` | `#fdf4f7` | accent + 94 % blanco | Fondo de tarjetas suaves, inputs en hover | #222 → 14.7 |
| `--rose-100` | `#fbe9f0` | accent + 88 % blanco | Bloques de sección "rosa claro" | #222 → 13.7 |
| `--rose-200` | `#f7d1df` | accent + 75 % blanco | Platos de producto, chips seleccionados | #222 → 11.5 |
| `--rose-300` | `#f1adc5` | accent + 55 % blanco | Bordes activos suaves, ilustración | — |
| `--rose-400` | `#e97fa5` | accent + 30 % blanco | Decorativo únicamente | — |
| `--accent` (500) | `#e0487f` | base | Rellenos grandes, marquee, stickers, texto ≥ 24 px | blanco → 3.89 (solo texto grande) |
| `--rose-600` | `#be3d6c` | accent + 15 % negro | **Botón primario**, links, texto rosa chico | blanco → 5.16 ✓ AA |
| `--rose-700` | `#9d3259` | accent + 30 % negro | Hover del primario, texto rosa sobre tintes | sobre rose-50 → 6.4 ✓ |
| `--rose-800` | `#702440` | accent + 50 % negro | Texto secundario sobre bloques rosa | — |
| `--ink` (900) | `#481729` | accent + 68 % negro | **Titulares, botón "tinta", footer** | blanco → 14.7 ✓ AAA |
| `--blush-50` | `#fef9f9` | rosa fondo + 94 % blanco | **Fondo base de página** | #222 → 15.3 |
| `--blush-100` | `#fdf3f3` | rosa fondo + 88 % blanco | Superficie alternativa | — |
| `--blush-200` | `#fbe6e6` | rosa fondo + 75 % blanco | Bloques cálidos | — |
| `--blush-300` | `#f8d2d2` | rosa fondo + 55 % blanco | ≈ el fondo actual; bloques "durazno" | #222 → 11.5 |
| `--blush-500` | `#f09c9c` | base | Decorativo (festoneado, ilustración) | — |
| `--cherry-50` | `#fcf1f1` | accent2 + 94 % blanco | Fondo de mensajes de error | — |
| `--cherry-100` | `#f9e3e2` | accent2 + 88 % blanco | Plato alternativo de producto | — |
| `--cherry` (500) | `#cf1810` | accent2 base | Stickers de oferta, errores | blanco → 5.54 ✓ |
| `--cherry-700` | `#91110b` | accent2 + 30 % negro | Texto de error sobre cherry-50 | ✓ |
| `--text` | `#222222` | existente | Texto de cuerpo | — |
| `--muted` | `#6b6b6b`* | ajuste de `#777` | Texto secundario | sobre blush-50 → 5.2 ✓ |
| `--white` | `#ffffff` | — | Tarjetas, inputs | — |

\* `#777` sobre los fondos rosas da 3.1–4.3 (falla AA). Se propone oscurecerlo apenas a `#6b6b6b` (mismo gris, 10 % más oscuro). Si se prefiere no tocarlo, `#777` queda solo para texto ≥ 18 px.

**Reglas de uso del color**
- El rosa `#e0487f` **nunca** se usa para texto de menos de 24 px (contraste 3.89 sobre blanco y 2.72 sobre el rosa actual). Para texto rosa chico se usa `--rose-600` o `--rose-700`.
- Botón primario: fondo `--rose-600`, texto blanco. Botón "tinta" (máximo énfasis, por ejemplo confirmar por WhatsApp): fondo `--ink`, texto blanco.
- El verde de WhatsApp (`#25D366`) deja de usarse como fondo de botón (texto blanco encima: 1.98, ilegible). Se conserva solo dentro del ícono de WhatsApp para que se reconozca. El degradado de Instagram desaparece.
- Secciones: se alternan `--blush-50` (base), `--rose-100`, `--blush-300`, un bloque fuerte `--accent` (marquee / llamado final) y `--ink` (footer). Nunca dos secciones seguidas del mismo tono sin un festoneado entre ellas.
- Estados: éxito = `--ink` sobre `--rose-100` con ícono de check (no se agrega verde); error = `--cherry-700` sobre `--cherry-50`; aviso = `--rose-800` sobre `--blush-200`.

### 3.2 Tipografía (Poppins)

Se carga **una sola vez** y con los mismos pesos en todas las páginas: `family=Poppins:wght@400;500;600;700;800&display=swap` + `preconnect` a `fonts.googleapis.com` y `fonts.gstatic.com`. Hoy cada página pide pesos distintos (300/400/600/700 vs 400/600/700/800).

| Token | Tamaño (clamp, 375 → 1440) | Peso | Interlineado | Interletrado | Uso |
|---|---|---|---|---|---|
| `--fs-display` | `clamp(2.75rem, 1.6rem + 5.2vw, 6rem)` (44 → 96 px) | 800 | 0.95 | -0.035em | Titular del hero |
| `--fs-h1` | `clamp(2.25rem, 1.55rem + 3vw, 4rem)` (36 → 64) | 800 | 1.0 | -0.03em | Título de página |
| `--fs-h2` | `clamp(1.875rem, 1.35rem + 2.3vw, 3.25rem)` (30 → 52) | 800 | 1.05 | -0.025em | Título de sección |
| `--fs-h3` | `clamp(1.25rem, 1.12rem + 0.55vw, 1.5rem)` (20 → 24) | 700 | 1.2 | -0.01em | Títulos de tarjeta |
| `--fs-lead` | `clamp(1.0625rem, 1rem + 0.35vw, 1.25rem)` (17 → 20) | 400 | 1.55 | 0 | Bajadas |
| `--fs-body` | `1rem` (16) | 400 | 1.6 | 0 | Texto corrido, **inputs (evita el zoom de iOS)** |
| `--fs-small` | `0.875rem` (14) | 500 | 1.5 | 0.005em | Ayudas, metadatos |
| `--fs-label` | `0.9375rem` (15) | 600 | 1.3 | 0 | Labels de formulario, botones chicos |
| `--fs-eyebrow` | `0.8125rem` (13) | 700 | 1.2 | 0.08em, MAYÚSCULAS | Stickers y antetítulos (con moderación) |
| `--fs-price` | hereda | 700 | — | `font-variant-numeric: tabular-nums` | Precios |

Reglas: `text-wrap: balance` en titulares y `text-wrap: pretty` en párrafos; ancho de lectura máximo `62ch`; títulos en *sentence case* ("Nuestros productos", no "Nuestros Productos"); los `button`, `input`, `select` y `textarea` heredan la fuente (`font: inherit`), porque hoy varios se ven en Arial.

### 3.3 Espaciado (base 4 px)

| Token | Valor | | Token | Valor |
|---|---|---|---|---|
| `--space-1` | 4px | | `--space-6` | 32px |
| `--space-2` | 8px | | `--space-7` | 48px |
| `--space-3` | 12px | | `--space-8` | 64px |
| `--space-4` | 16px | | `--space-9` | 96px |
| `--space-5` | 24px | | `--space-10` | 128px |

- `--gutter`: 16px (< 768) · 24px (768–1279) · 32px (≥ 1280).
- `--section-y`: `clamp(64px, 9vw, 128px)` arriba y un 10 % más abajo (corrección óptica).
- `--container`: 1200px · `--container-wide`: 1320px (grillas de productos) · `--container-narrow`: 720px (formularios, perfil).
- Separación dentro de formularios: label → input 8px · campo → campo 20px · grupo → grupo 32px.

### 3.4 Radios

| Token | Valor | Uso |
|---|---|---|
| `--radius-sm` | 10px | Chips internos, miniaturas |
| `--radius-md` | 14px | Inputs, selects, ítems del carrito |
| `--radius-lg` | 24px | Tarjetas, paneles, modales |
| `--radius-xl` | 36px | Platos de producto, bloques de sección redondeados, foto del hero |
| `--radius-pill` | 999px | Botones, chips, stickers, badge del carrito |

Regla: radio interior = radio exterior − padding (una tarjeta de 24px con 8px de padding lleva una foto de 16px).

### 3.5 Sombras / elevación

Casi todo es plano (color). Las sombras se tiñen con `--ink` y se usan solo en lo que flota.

| Token | Valor | Uso |
|---|---|---|
| `--shadow-0` | `none` | Tarjetas en reposo, secciones |
| `--shadow-1` | `0 1px 2px rgb(72 23 41 / .06), 0 2px 8px -2px rgb(72 23 41 / .10)` | Header al hacer scroll, inputs en foco |
| `--shadow-2` | `0 10px 30px -10px rgb(72 23 41 / .25)` | Dropdown de cuenta, botón flotante de carrito, tarjeta en hover |
| `--shadow-3` | `0 24px 60px -16px rgb(72 23 41 / .35)` | Modales y panel del carrito |
| `--shadow-sticker` | `2px 3px 0 rgb(72 23 41 / .9)` | Stickers (efecto calcomanía) |

### 3.6 Capas (z-index)

| Token | Valor | | Token | Valor |
|---|---|---|---|---|
| `--z-base` | 0 | | `--z-dropdown` | 300 |
| `--z-raised` | 10 | | `--z-overlay` | 400 |
| `--z-header` | 100 | | `--z-modal` | 500 |
| `--z-floating` | 200 | | `--z-toast` | 600 |

(Hoy conviven 1000, 1200, 2000 y 3000 sin criterio.)

### 3.7 Breakpoints (mobile-first, `min-width`)

`--bp-sm 480px` · `--bp-md 768px` · `--bp-lg 1024px` · `--bp-xl 1280px`. Se escriben como media queries `@media (min-width: 48rem)` etc. (las variables CSS no funcionan dentro de `@media`; se documentan como referencia). Hoy hay cortes en 420, 520, 600, 720, 768, 880 y 881 px, cada archivo con los suyos.

### 3.8 Movimiento

| Token | Valor | Uso |
|---|---|---|
| `--dur-press` | 120ms | Escala al presionar |
| `--dur-fast` | 160ms | Hover de color, foco |
| `--dur-base` | 220ms | Menús, dropdown, chips |
| `--dur-slow` | 320ms | Modales, panel del carrito, cambio de paso del creador |
| `--dur-reveal` | 600ms | Aparición de secciones al hacer scroll (una sola vez) |
| `--dur-marquee` | 38s | Cinta de texto (lineal, infinita) |
| `--ease-out` | `cubic-bezier(.23, 1, .32, 1)` | Entradas, respuestas a la usuaria |
| `--ease-in-out` | `cubic-bezier(.77, 0, .175, 1)` | Movimientos en pantalla (cambio de paso) |
| `--ease-drawer` | `cubic-bezier(.32, .72, 0, 1)` | Panel del carrito y menú móvil |
| `--ease-pop` | `cubic-bezier(.16, 1, .3, 1)` | Sticker al aparecer, badge del carrito al sumar (frenado exponencial; el "pop" lo dan los keyframes, sin curvas con rebote) |

Reglas (Emil): solo se animan `transform` y `opacity`; nada nace de `scale(0)` (mínimo `scale(.95)` + opacidad); los hovers se limitan a `@media (hover: hover) and (pointer: fine)`; todo lo presionable tiene `:active { transform: scale(.97) }`; salidas más rápidas que entradas; las transiciones nombran propiedades (nunca `transition: all`/`transition: .2s`); `prefers-reduced-motion: reduce` frena la cinta, reemplaza el video por su póster y deja solo fundidos de opacidad.

### 3.9 Estrategia de CSS (importante)

- **`main.css` también lo cargan `admin.html` y `baker.html`.** Cambiar sus reglas globales (`body`, `:focus`, `.product-card`, `.products-grid`…) rompería los paneles. Por eso el sitio público pasa a usar archivos nuevos y `main.css` queda congelado para los paneles (en una fase final se puede limpiar lo que ya no use nadie, verificando ambos paneles).
- Archivos nuevos propuestos (sin build step, `<link>` comunes):
  - `css/site/tokens.css` — variables de las secciones 3.1–3.8.
  - `css/site/base.css` — reset, tipografía, `[hidden]{display:none!important}`, `:focus-visible`, `.visually-hidden`, `.skip-link`, contenedores y secciones.
  - `css/site/components.css` — botones, inputs, tarjetas, stickers, chips, header, footer, modales, toasts, marquee.
  - `css/site/pages/*.css` — un archivo por página (`home.css`, `products.css`, `builder.css`, `cart.css`, `auth.css`, `profile.css`).
- Los ~108 `style="…"` inline de los templates (products 25, cart 33, profile 33, builder 9, register 5, login 2, header 1) y el HTML con estilos inline que arma `store.js` pasan a clases.
- Los archivos viejos del sitio público (`header.css`, `hero.css`, `promo.css`, `about.css`, `products.css`, `public.css`, `login.css`, `register.css`, `footer.css`) dejan de enlazarse cuando su página migre. **No se borran sin tu confirmación.**

---

## 4. Inventario de componentes

### 4.1 Botones
| Variante | Aspecto | Uso |
|---|---|---|
| `.btn--primary` | Píldora `--rose-600`, texto blanco 600 | Acción principal de cada pantalla ("Armá tu torta", "Siguiente", "Agregar al pedido") |
| `.btn--ink` | Píldora `--ink`, texto blanco | Acción final y de mayor compromiso ("Confirmar pedido por WhatsApp") |
| `.btn--outline` | Píldora transparente, borde 2px `--ink`, texto `--ink` | Secundaria ("Ver productos", "Volver") |
| `.btn--soft` | Píldora `--rose-100`, texto `--rose-700` | Terciaria dentro de tarjetas ("Aplicar" cupón) |
| `.btn--link` | Texto `--rose-700` subrayado al hover | Enlaces dentro de texto ("Registrate") |
| `.btn--icon` | Círculo 44×44, fondo transparente o `--white` | Menú, cuenta, cerrar, mostrar contraseña, quitar ítem |
| `.btn--whatsapp` | `.btn--ink` + ícono de WhatsApp verde | Contacto y checkout |

Tamaños: `sm` 40px de alto (solo desktop), `md` 48px (por defecto), `lg` 56px (CTA de hero y checkout). En celular nada interactivo mide menos de 44×44.
Estados: *hover* (oscurece un paso: 600→700; solo con puntero fino) · *active* (`scale(.97)`, 120ms) · *focus-visible* (anillo 3px `--rose-600` con separación 3px; en fondos oscuros, anillo blanco) · *disabled* (fondo `--rose-100`, texto `--rose-800` al 60 %, `cursor: not-allowed`, sin sombra; hoy el deshabilitado es rosa lavado con texto blanco ilegible) · *loading* (spinner SVG + `aria-busy="true"`, el ancho no cambia).

### 4.2 Inputs
Texto, email, teléfono, contraseña (con botón de mostrar en SVG), búsqueda (lupa SVG a la izquierda, no emoji), textarea, select (chevron SVG propio), fecha, checkbox propio (no el nativo de 13px), combo "cupón" (input + botón unidos). Alto 52px, radio `--radius-md`, borde 1.5px `#2222221f`, fondo blanco, fuente 16px.
Estados: hover (borde más oscuro) · focus (borde `--rose-600` + `--shadow-1` + anillo) · inválido (borde `--cherry`, mensaje `--cherry-700` con ícono debajo, `aria-invalid` + `aria-describedby`) · deshabilitado/solo lectura (fondo `--blush-100`, texto `--muted`, candado) · ayuda (texto 14px `--muted` debajo).
Labels siempre visibles arriba del campo (el placeholder nunca reemplaza al label). Obligatorio: no se escribe "(Obligatorio)" en cada label; se marca lo **opcional** con "(opcional)" en `--muted`.

### 4.3 Tarjetas
- **Tarjeta de producto**: "plato" de color (`--rose-200`, `--blush-300` o `--cherry-100`, rotando por posición) con la foto centrada y radio `--radius-xl`; sticker opcional en la esquina; nombre (h3), descripción a 2 líneas; fila inferior con precio en etiqueta y botón redondo "Agregar" (con texto accesible). Sin imagen: el plato muestra el isotipo de la marca, no "Sin imagen". En celular: 2 columnas.
- **Tile de categoría** (home): foto grande a sangre en el plato + nombre en titular + flecha; toda la tarjeta es un link.
- **Tarjeta de opción del creador**: radio/checkbox visual. Tamaño: número de porciones gigante + medida + precio. Cobertura: ícono SVG + nombre + descripción corta. Sabores/rellenos: chips de 2 columnas.
- **Ítem del carrito**: nombre, detalles en lista (separando lo que hoy va con " | "), precio, botón quitar 44px.
- **Panel** (perfil, formularios): fondo blanco, `--radius-lg`, sin borde ni sombra sobre bloque de color.

### 4.4 Badges y stickers
- **Sticker**: píldora o círculo `--accent`/`--cherry` con texto blanco 13px en mayúsculas (o `--ink` sobre `--rose-200`), girado −6° a 8°, `--shadow-sticker`. Solo con textos verdaderos: "Hecho a mano", "Desde $44.000" (precio real del tamaño más chico, leído de la API), "3 días de anticipación", "Retiro y envío". **"Más vendido" no se usa** hasta que exista un dato real (ver Decisiones pendientes).
- **Chip de categoría**: píldora `--rose-100` / texto `--rose-700`; seleccionada `--ink` / blanco.
- **Badge del carrito**: círculo `--ink` con número blanco, con "pop" al sumar.
- **Indicador de pasos** del creador: 3 círculos numerados unidos por una línea que se llena.

### 4.5 Contenedores de sección
`.section` + modificadores de tono (`--tone-blush`, `--tone-rose`, `--tone-peach`, `--tone-accent`, `--tone-ink`), `.container` / `--wide` / `--narrow`, divisor festoneado SVG (`.scallop` arriba o abajo, color heredado del bloque siguiente), `.marquee` (cinta con texto repetido, pausa en hover/foco, estática con movimiento reducido).

### 4.6 Header
Fijo (`position: sticky`), 64px en celular / 76px en desktop, fondo `--blush-50` sólido (sin desenfoque ni blanco translúcido gris), línea inferior y `--shadow-1` que aparecen al hacer scroll. Desktop: logo a la izquierda, navegación al centro (Inicio, Productos, Armá tu torta, Nosotros, Contacto) con estado activo, a la derecha cuenta + **píldora de carrito** ("Carrito" + badge). Celular: logo, cuenta (44px), carrito (44px con badge), menú (44px). WhatsApp e Instagram salen del header (hoy desbordan la pantalla en 375px) y pasan al menú móvil, a Contacto y al footer.
- Menú móvil: panel que baja desde el header con links grandes (56px de alto), CTA "Armá tu torta", WhatsApp, Instagram e "Ingresar"/"Mi cuenta". Animación `--dur-base` `--ease-drawer`; se cierra con Escape, click afuera o al elegir un link.
- Dropdown de cuenta: escala desde la esquina del botón (`transform-origin: top right`), `--shadow-2`; para ADMIN muestra "Panel de administración" y para PASTELERO "Panel del pastelero" (hoy el pastelero no tiene acceso desde el menú).

### 4.7 Footer
Bloque `--ink` con festoneado arriba: logo en blanco, frase "Porque soñar es lindo", links (Productos, Armá tu torta, Nosotros, Contacto), contacto (WhatsApp, Instagram @dulcessuenos_posadas, Posadas, Misiones · Retiro y envío · Pedidos con 3 días de anticipación) y línea final "© 2026 Dulce Sueños Posadas". **Un solo footer** reutilizable como fragment Thymeleaf (`fragments/footer.html`); hoy hay tres versiones distintas y register no tiene.

### 4.8 Modales, panel de carrito y toasts
- **Panel del carrito** (`#modal-checkout-preview`): pasa de modal centrado a panel lateral derecho en desktop y hoja inferior en celular (`--ease-drawer`, 320ms entrada / 200ms salida), con lista de ítems, total y CTA "Ir a confirmar pedido". Se cierra con Escape, click en el fondo o botón X en SVG.
- **Botón flotante de carrito** (`#floating-cart-btn`): píldora `--ink` abajo al centro en celular ("Ver pedido · 2 · $94.500"), respetando `env(safe-area-inset-bottom)`; en desktop desaparece porque está la píldora del header (hoy hay dos carritos visibles a la vez).
- **Modal de aviso** (`#modal-alert-v1`) y **modal de éxito** (`#modal-success-v1`): tarjeta centrada `--radius-lg`, ícono SVG, título, texto y botones `.btn`. Entrada `scale(.96)`+opacidad 220ms.
- **Toast** (nuevo): "Agregado a tu pedido" al sumar un producto desde el catálogo, en lugar de recargar la página entera. Abajo al centro, `--z-toast`, se va solo a los 3 s, `role="status"`.

---

## 5. Plan página por página

Orden de fases sugerido: **F1** tokens + base + componentes + header + footer → **F2** Home → **F3** Productos → **F4** Creador → **F5** Carrito y checkout → **F6** Login / Registro / Perfil → **F7** pulido, accesibilidad, rendimiento y verificación en ambos paneles.

### 5.1 Header y footer (F1)
Como en 4.6 y 4.7. Los links "Nosotros" y "Contacto" pasan a `/#nosotros` y `/#contacto` (decisión confirmada). Se agrega `scroll-margin-top` para que las anclas no queden tapadas por el header. Se agrega "skip link" ("Saltar al contenido") y favicon con el logo.

### 5.2 Home `/` (F2)
1. **Hero** (fondo `--blush-50`). Celular: titular display "Porque soñar es lindo." (la frase de marca que hoy está oculta por CSS), bajada "Tortas personalizadas, mesas dulces y souvenirs hechos a mano en Posadas.", CTA primaria "Armá tu torta" y secundaria "Ver productos", y debajo la foto/video en un marco `--radius-xl` con dos stickers ("Hecho a mano", "Desde $44.000"). Desktop: dos columnas (texto 6/12, imagen 6/12) con el titular gigante. El logo deja de ocupar el centro del hero (pasa al header). El video de 31,7 MB **no se carga en celular** (se usa el póster); en desktop se carga solo si no hay `prefers-reduced-motion` y conviene reemplazarlo por una versión comprimida (< 3 MB) que tiene que generar el autor (no hay ffmpeg en este equipo).
2. **Marquee** (fondo `--accent`, texto blanco 800 de 24px+): "Tortas personalizadas · Mesas dulces · Souvenirs · Retiro y envío en Posadas · Pedí con 3 días de anticipación ·" con un pequeño ícono SVG entre frases.
3. **"Lo que hacemos"** (fondo `--blush-50`): tres tiles de categoría (Tortas, Dulces, Souvenirs) en grilla asimétrica en desktop (Tortas grande ocupando dos filas) y carrusel con *scroll-snap* en celular. Cada tile lleva a `/products?categoria=…` (el script de productos lee el parámetro y preselecciona el filtro). Fotos comprimidas (hoy pesan 3–6 MB cada una).
4. **El creador en 3 pasos** (fondo `--rose-100`, festoneado arriba): titular "Tu torta, a tu manera", tres pasos numerados grandes (Tamaño y cobertura → Sabores y rellenos → Detalles de diseño), sticker "Desde $44.000" (precio mínimo leído de `/api/cake-config/public`) y CTA "Empezar a armarla".
5. **Nosotros** `#nosotros` (fondo `--blush-300`): foto de Vero y Maca en marco redondeado grande (no círculo chico), sticker "Vero & Maca", el texto actual alineado a la izquierda (no centrado en celular), CTA "Escribinos por WhatsApp".
6. **Galería** (fondo `--blush-50`): fila con *scroll-snap* de fotos reales en formatos mezclados (vertical/cuadrado); con movimiento permitido, desplazamiento lento automático que se pausa al tocar o enfocar. Hoy la sección está vacía (ver Decisiones pendientes).
7. **Promociones** (fondo `--rose-100`): "Promo cumpleañero" y "Cliente frecuente" como tarjetas con sticker y la foto actual; texto "Consultanos por WhatsApp" (no se inventan condiciones). Reemplaza al newsletter que no funcionaba (decisión confirmada).
8. **Contacto** `#contacto` (fondo `--accent` o `--ink`): titular "¿Tenés un evento en mente?", botones WhatsApp e Instagram, y datos: Posadas, Misiones · Retiro y envío · Pedidos con 3 días de anticipación.
9. Footer.

### 5.3 Productos `/products` (F3)
1. Encabezado compacto (fondo `--rose-100` con festoneado abajo): H1 "Nuestros productos", bajada actual, CTA "Armá tu torta" (sin emoji).
2. Barra de filtros fija bajo el header al hacer scroll: búsqueda con lupa SVG + chips de categoría con scroll horizontal en celular ("Todas" + categorías de la base). Se mantiene el `<select id="categoryFilter">` o se reemplaza por chips actualizando el script inline (ver contratos).
3. Grilla: 2 columnas en celular, 3 en tablet, 4 en desktop (`--container-wide`). Tarjetas como en 4.3. "Agregar" suma sin recargar y muestra toast + "pop" en el badge del carrito.
4. Estado vacío diseñado: ilustración con el isotipo, "No encontramos productos con ese nombre", botón "Ver todos" (limpia filtros) y "Armá tu torta". Si la base no tiene productos: "Muy pronto vas a ver acá nuestro catálogo. Mientras tanto, armá tu torta." (hoy la página aparece vacía con un cartel de "No se encontraron productos").
5. Estado de carga no aplica (render en servidor).

### 5.4 Creador `/builder` (F4)
1. Encabezado: H1 "Armá tu torta", bajada en voseo ("Seguí los pasos y mirá el precio al instante"), indicador de 3 pasos sticky bajo el header.
2. **Paso 1 — Tamaño y cobertura**: tamaños como tarjetas-radio en 2 columnas en celular (número de porciones grande, medida, precio); cobertura como 2 tarjetas grandes con ícono SVG (sin los emojis ☁️ 🎨).
3. **Paso 2 — Sabores**: subtítulos "Bizcochuelo (elegí 1)" / "Rellenos (elegí hasta 3)" sin la numeración errónea "3." actual; chips en 2 columnas; contador "2 de 3 elegidos"; las opciones restantes se atenúan cuando se llega al límite y un mensaje explica por qué no se puede elegir más.
4. **Paso 3 — Detalles**: textarea con ejemplos, y un **resumen** de la torta (tamaño, cobertura, sabores, rellenos, precio) antes de "Agregar al pedido".
5. **Barra inferior fija en celular**: resumen corto + precio + botón "Siguiente"/"Volver" de igual altura (hoy "Volver" y "Siguiente" tienen alturas distintas y el botón queda al final de una lista larga). En desktop, columna lateral con el resumen.
6. Al cambiar de paso: transición `--dur-slow` y scroll al inicio del creador **sin quedar tapado por el header** (`scroll-margin-top`).

### 5.5 Carrito y checkout `/cart` (F5)
1. H1 "Tu pedido" (sin emoji).
2. Desktop: formulario a la izquierda (7/12) y resumen fijo a la derecha (5/12). Celular: resumen arriba (ítems + total) y formulario abajo, con barra fija "Confirmar por WhatsApp · $94.500".
3. Ítems: detalles en lista legible (Tamaño, Cobertura, Bizcochuelo, Rellenos, Detalles) en lugar del texto con " | ". Quitar con botón de 44px y confirmación suave (o "Deshacer" en toast).
4. Cupón: combo unido, mensajes con los tokens de éxito/error (hoy `#25D366` y `red` desde el JS).
5. Formulario: "Tus datos" (nombre, WhatsApp, email opcional), "Entrega" (fecha con ayuda "Elegí una fecha a partir del {fecha mínima}", medio de pago como dos tarjetas-radio en vez de select), "Aclaraciones (opcional)". Sin numeración "1. 2. 3." en los labels.
6. CTA final `.btn--ink` grande con ícono de WhatsApp y texto "Confirmar pedido por WhatsApp" + línea de confianza: "Vamos a abrir WhatsApp con tu pedido listo para enviar."
7. Carrito vacío diseñado: "Tu pedido está vacío" + CTAs "Armá tu torta" / "Ver productos".
8. Modales de aviso y éxito como en 4.8; el éxito usa voz de la marca ("¡Listo! Te esperamos en WhatsApp para coordinar.") y deja de decir "DreamBake" al cliente (la marca pública es Dulce Sueños).

### 5.6 Login `/login`, Registro `/register`, Perfil `/profile` (F6)
- **Login**: desktop en dos columnas (panel `--rose-100` con foto y "Hola de nuevo" + formulario); celular solo formulario con el isotipo arriba. Error como alerta con ícono. Botón mostrar contraseña en SVG y funcionando (hoy el 👁️ del login no hace nada). "Recordarme" se quita o se implementa (hoy no hace nada: Spring no tiene `rememberMe()` configurado). Link "¿No tenés cuenta? Registrate" con `--rose-700`.
- **Registro**: misma estructura que login, **con header y footer** (hoy no tiene ninguno y no hay forma de volver), link "¿Ya tenés cuenta? Iniciá sesión", errores de validación con el estilo de 4.2, mostrar contraseña (arregla el error de JS que hoy rompe la validación de "las contraseñas no coinciden"), beneficio: "Guardamos tus datos para que pedir sea más rápido". Tras registrarse, el login muestra "Tu cuenta está lista. Iniciá sesión." (requiere `?registered` en la redirección: cambio mínimo en `AuthController`, a confirmar).
- **Perfil**: H1 "Mi cuenta" + saludo; paneles "Datos personales" y "Contraseña" en `--container-narrow`, uno debajo del otro en celular y lado a lado en desktop; alertas de éxito/error con ícono SVG (hoy ✅ ❌); email como solo lectura con candado.

---

## 6. Contratos que NO se pueden romper (JS + Thymeleaf)

Todo lo siguiente se puede reubicar y reestilar, pero **los ids, clases, atributos `data-*`, nombres de campos y funciones globales tienen que seguir existiendo** (o cambiarse en el JS en la misma fase, a propósito y documentado).

### 6.1 Reglas transversales
- `[hidden]` se usa para mostrar/ocultar menús y modales (`#mobileMenu`, `#userMenu`, `#modal-checkout-preview`, `#modal-alert-v1`, `#modal-success-v1`). Ningún estilo nuevo puede ganarle con `display:flex` → regla global `[hidden]{display:none!important}` y animaciones que respeten el atributo.
- Clases de estado que pone el JS: `.active` (pasos del creador), `.selected` (tarjetas de opción), `.visually-hidden` (spinner de login.js; **hoy no está definida en ningún CSS**, por eso el label "Tu email" del newsletter se ve), `.paused` (carrusel, sin uso real).
- `localStorage['dreamBakeCart']` = array de `{ productType, details, price }`. No cambiar el formato.
- `store.js` corre en todas las páginas que lo cargan salvo `/admin*`, y compara `location.pathname` con `'/cart'`.
- `main.css` lo cargan también `admin.html` y `baker.html` (ver 3.9).

### 6.2 `fragments/header.html` + `static/js/header.js`
| Elemento | Contrato |
|---|---|
| `<header th:fragment="header" class="site-header">` | Incluido con `th:replace="~{fragments/header :: header}"` en index, products, builder, cart, login, profile (no en register) |
| `#menuButton` | header.js alterna `aria-expanded` y `#mobileMenu.hidden`; click afuera cierra (`contains`) |
| `#mobileMenu` | contenedor con atributo `hidden` |
| `#accountBtn` / `#userMenu` | alterna `hidden`; click afuera cierra |
| `.btn-cart` | `store.js` toma **el primero** con `querySelector('.btn-cart')` y, fuera de `/cart`, intercepta el click (`preventDefault`) para abrir el mini-carrito. Debe seguir siendo un `<a href="/cart">` (funciona sin JS) |
| `.cart-count`, `.header-cart-count` (y `#cart-count`, `#cart-count-float`) | `store.js` escribe `innerText = cart.length` en todos |
| `sec:authorize="isAnonymous()"` / `isAuthenticated()` / `hasAuthority('ADMIN')` | qué íconos y links se ven |
| `th:text="${currentUser.name.split(' ')[0]}"` con `th:if="${currentUser != null}"` | saludo (lo inyecta `GlobalControllerAdvice`) |
| `<form th:action="@{/logout}" method="post">` + `button[type=submit]` | logout de Spring Security (tiene que seguir siendo POST) |
| `th:href="@{/}"`, `@{/products}`, `@{/admin}`, `@{/profile}`, `@{/cart}`, `@{/login}` | rutas |

### 6.3 `static/js/wheel-carousel.js` (home)
Espera `#wheelCarousel > .wheel-track-wrapper > .wheel-track > .carousel-item` (con `img`, opcional `.product-image` y `.product-label`); clona los ítems (`data-cloned="true"` en el track), escribe `transform` inline, lee el `gap` del track, pausa con mouseenter/focusin/touchstart y al ocultarse la pestaña. **Hoy no hace nada** porque `#wheelCarousel` está vacío en el template. Si la galería nueva usa CSS *scroll-snap* o una animación CSS, el script deja de enlazarse (el archivo se conserva hasta que confirmes borrarlo).

### 6.4 `static/js/public/store.js`
**Funciones globales** (usadas desde `onclick` en templates o en HTML generado): `openMiniCart()`, `closeMiniCart()`, `updateCartUI()`, `removeCart(index)`, `selectSize(id)`, `selectCoverage(cov)`, `goToStep(step)`, `toggleSponge(name)`, `toggleFilling(name)`, `addCakeToCart()`, `showPublicAlert(msg)`.

**HTML que inyecta en `<body>`** (todas las páginas con store.js menos `/cart`):
```html
<button id="floating-cart-btn" class="floating-cart" style="display:none" onclick="openMiniCart()">… <span id="cart-count-float">0</span></button>
<div id="modal-checkout-preview" class="public-modal-overlay" hidden>
  <div class="public-modal-card">
    <div class="public-modal-header"><h3>Tu Carrito</h3><button class="public-close-modal" onclick="closeMiniCart()">×</button></div>
    <div class="public-modal-body">
      <div id="cart-items-preview"></div>
      <h3>Total: $<span id="cart-total-preview">0</span></h3>
      <a href="/cart" class="btn btn-pink btn-large">Ir a Confirmar Compra</a>
    </div>
  </div>
</div>
```
`updateCartUI()` pone `floatBtn.style.display = 'block' | 'none'` (si el diseño quiere `inline-flex`, hay que cambiar esa línea del JS).

**Filas del carrito que genera** (`#cart-items-preview` y `#full-cart-items`): `div.cart-item-row > div > div.cart-item-title (+ div.cart-item-desc en /cart)` + precio + `button[onclick="removeCart(i)"]` con SVG; vacío: un `<p>` con texto. Todo con estilos inline que hay que pasar a clases editando las plantillas de texto del JS.

**Carrito / checkout (`cart.html`)**: `#full-cart-items`, `#full-cart-total`, `#final-checkout-form` (su `button[type="submit"]` se deshabilita si el carrito está vacío), `#cart-coupon`, `#btn-apply-coupon`, `#coupon-message` (el JS le pone `style.color` `#25D366` / `red`), `#chk-name`, `#chk-phone`, `#chk-email`, `#chk-general-details`, `#chk-date` (el JS le pone `min` = hoy + 3), `#chk-payment` (valores `Transferencia` / `Efectivo`, viajan en el mensaje de WhatsApp), `#modal-alert-v1` + `#alert-v1-msg`, `#modal-success-v1` + `#btn-keep-shopping` + `#btn-accept-success`. El botón "Entendido" cierra con `onclick="document.getElementById('modal-alert-v1').hidden = true"`. `th:value` con `currentUser.name/phone/email` para precargar.

**Creador (`builder.html`)**: `#cake-builder` (destino del `scrollIntoView`), `#grid-sizes` (se llena con `.select-card.size-card[data-id]` + `span.price`), `#grid-coverages .select-card[data-val="Crema"|"Fondant"]` con `onclick="selectCoverage('…')"` (los valores `Crema`/`Fondant` son claves de negocio), `#btn-next-1`, `#btn-next-2` (el JS alterna `disabled`), `.wizard-step` + `#wizard-step-1..3` (clase `.active`), **`.step`** (el JS hace `querySelectorAll('.step')` y activa por índice: **ningún otro elemento de la página puede usar la clase `step`**), `#step-indicator-1..3`, `#sponge-info`, `#fillings-info`, `#lbl-cov` (`innerText`), `#grid-sponges` (`.select-card.sponge-card[data-val]`), `#grid-fillings` (`.select-card.fill-card[data-val]`), `#cake-details`. `addCakeToCart()` quita `.selected` de **todas** las `.select-card` de la página.

**Endpoints que usa**: `GET /api/cake-config/public` (en todas las páginas, aunque solo lo necesita el creador), `GET /api/coupons/validate/{code}`, `POST /api/coupons/consume/{code}`, `POST /api/orders`; abre `https://wa.me/5493764159312?text=…` (número fijo en el JS; ver Decisiones pendientes).

### 6.5 Script inline de `products.html`
`#search` (input), `#categoryFilter` (valor `all` o el nombre de la categoría), `.catalog-item` con `data-name` (en minúsculas) y `data-category`, `#emptyState`; el script pone `card.style.display = 'flex' | 'none'` (la tarjeta tiene que poder ser `flex`, o se cambia el script) y `emptyState.style.display = 'block' | 'none'`. `addCatalogProductToCart(btn)` lee `data-prod-name`, `data-prod-desc`, `data-prod-price`, escribe en `localStorage` y **recarga la página** (se reemplaza por una función de `store.js` que actualice sin recargar). Thymeleaf: `th:each="prod : ${products}"`, `th:each="cat : ${categories}"`, `th:attr` para los `data-*`, `#numbers.formatDecimal(prod.price, 0, 'POINT', 0, 'COMMA')`, `th:if` de imagen/categoría/descripción, `th:style` del estado vacío, `sec:authorize="hasAuthority('ADMIN')"`.

### 6.6 `login.html` + `static/js/login.js`
- Formulario `th:action="@{/login}" method="post"`; **`name="username"`** (Spring Security espera `username`, aunque el campo sea el email) con `id="email"`; `name="password"` con `id="password"`; `name="remember-me"` (sin efecto hoy); `th:if="${param.error}"` para el mensaje; `#toggle-password` (sin JS que lo maneje).
- `login.js` busca `#login-form`, `#remember`, `#login-status`, `#login-submit`, `#login-spinner` (no existen) y llama a `http://localhost:8080/api/auth/*` (no existe). En la práctica solo publica `window.auth`. Es código muerto: se puede reemplazar por un script mínimo de "mostrar contraseña" (confirmar con el autor antes de borrar el archivo).

### 6.7 `register.html` + `static/js/register.js`
- `th:action="@{/register}"`, `th:object="${user}"`, `th:field="*{name}"`, `*{email}`, `*{phone}`, `*{password}` (generan `id`/`name`), `th:errors` + `th:if="${#fields.hasErrors('…')}"`.
- `register.js` usa `#password`, `#confirmPassword`, `#passwordError` (`style.display`), `#register-submit` (`disabled`) y **`#toggle-password`, que no existe en register.html** → `TypeError` al cargar, y como el error ocurre antes de registrar los listeners, **la validación de "las contraseñas no coinciden" nunca se activa**. Al rediseñar se agrega el botón (o se protege el JS con `if (togglePass)`).

### 6.8 `profile.html`
Formularios `th:action="@{/profile/update}"` (campos `name`, `phone`) y `th:action="@{/profile/password}"` (`currentPassword`, `newPassword`, `confirmPassword`), `th:if="${currentUser != null}"`, `th:value` con datos del usuario, mensajes `th:if` con `param.updated`, `param.passUpdated`, `param.error[0] == 'current' | 'match'`. Carga `store.js` (por eso aparece el botón flotante del carrito en el perfil).

### 6.9 `index.html`
`th:src` de imágenes y video (`@{/img/...}`), `th:poster`, links `@{/builder}`, `@{/products}`, `@{/contact}` (pasa a `#contacto`), formulario `th:action="@{/subscribe}"` (se elimina: decisión confirmada), `#wheelCarousel` (ver 6.3).

### 6.10 Rutas y seguridad
Públicas: `/`, `/builder`, `/products`, `/cart`, `/register`, `/css/**`, `/js/**`, `/img/**`, `/uploads/**`, `/login`. **Cualquier carpeta de estáticos nueva fuera de esas (por ejemplo `/media/**` o `/fonts/**`) redirige al login** → todo asset nuevo va dentro de `/css`, `/js` o `/img`. `/profile` requiere sesión. `GET /api/gallery` requiere ADMIN o PASTELERO (la home no puede leerla sin un cambio de backend).

---

## 7. Decisiones pendientes (necesitan tu OK)

> **Resueltas en la Fase 2 (2026-10-05):** #2 galería → opción (a): `WebController` pasa las `GalleryImage` (y hasta 8 productos activos) a la home, con fotos de ejemplo de respaldo. #4 imágenes → se borraron los archivos de prueba y se reemplazaron por fotos libres de Unsplash en WebP (la home pasó de ~45 MB a ~0,35 MB en la primera carga del celular); el video se reemplazó por una foto con movimiento lento. La foto `veroymaca.jpg` era de prueba y se eliminó.

1. **WhatsApp configurable desde el admin** (pedido por la clienta): requiere guardar una configuración nueva en la base (entidad o tabla de ajustes) + endpoint + campo en el panel. Es un cambio de modelo: va en una fase propia, fuera del rediseño visual. Mientras tanto se usa `5493764159312` y el header deja de tener el placeholder `549XXXXXXXXXX`.
2. **Galería de la home**: hoy está vacía. Opciones: (a) que `WebController` pase las `GalleryImage` a la vista de `/` (cambio chico, sin tocar entidades) — recomendada, así lo que sube el admin aparece solo; (b) usar las 10 fotos de `img/carousel/` de forma fija.
3. **Sticker "Más pedido"**: no hay dato real. Opciones: no usarlo (recomendado por ahora) o agregar un campo booleano `featured` a `Product` (cambio de entidad).
4. **Pesos de imagen y video**: el video del hero pesa 31,7 MB y las fotos de la home 3–6 MB cada una. Hay que exportarlas comprimidas (WebP/JPG ~200–400 KB, video < 3 MB). No hay herramientas de conversión en este equipo: lo tiene que hacer el autor (o habilitar ffmpeg/cwebp).
5. **"Recordarme" en login**: quitarlo o configurar `rememberMe()` en `SecurityConfig`.
6. **Mensaje tras registrarse**: agregar `?registered` a la redirección de `AuthController` (una línea).
7. **Retiro o envío en el checkout**: el negocio hace ambos, pero el checkout no lo pregunta. Agregar la opción cambia el pedido que se guarda (iría en el texto de detalles para no tocar la entidad). ¿Lo sumamos?
8. **`--muted`**: oscurecer `#777` a `#6b6b6b` para cumplir contraste, o mantenerlo solo para textos grandes.
9. **Limpieza de archivos**: `static/js/login.js` (código muerto), `wheel-carousel.js` (si la galería nueva no lo usa), CSS públicos viejos, `admin.js`/`products.js` sueltos, scripts de la raíz. No se borra nada sin confirmación.
10. **Cuenta de prueba**: se creó `qa.rediseno@dreambake.test` en la base local para capturar `/profile` (datos en `design-review/test-account.md`). Se puede borrar cuando quieras.
