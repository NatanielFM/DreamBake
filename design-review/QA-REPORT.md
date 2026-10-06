# QA final del sitio público — Fase 6

> Rama `cambios-ui` · 2026-10-06 · sin commit.
> Páginas: `/`, `/products`, `/builder`, `/cart` (vacío y con productos), `/login`, `/register`, `/profile`.
> Anchos: 320, 375, 390, 414, 768, 1024, 1280, 1440 y 1920 px.

## 1. Resumen

El sitio público quedó sin defectos medibles en los 9 anchos:

- sin scroll horizontal ni elementos cortados;
- sin textos que se salgan de su caja;
- todo lo que se toca mide al menos 44 px;
- contraste AA en todo el texto;
- foco de teclado visible en cada control;
- 0 errores de consola y 0 pedidos fallidos;
- 0 corrimiento de contenido al cargar (CLS).

Los 8 recorridos principales funcionan de punta a punta a 390 y 1280 px. Los paneles de administración y del pastelero siguen funcionando.

En esta fase se arreglaron 22 detalles. Lo que queda pendiente es contenido real (fotos, productos de prueba en la base), la prueba en un teléfono físico y decisiones de lógica, que están en `PENDIENTES-BACKEND.md`.

| Auditoría Impeccable | Fase 0 (antes) | Fase 6 (ahora) |
|---|---|---|
| Accesibilidad | 1/4 | 4/4: contraste AA medido, foco visible y no tapado, teclado en el creador, movimiento reducido |
| Rendimiento | 1/4 | 3/4: fotos WebP livianas con carga diferida y sin video. Queda: el JS del creador se carga en todas las páginas |
| Responsive | 2/4 | 4/4: de 320 a 1920 sin desbordes y con zonas táctiles de 44 px |
| Theming / tokens | 1/4 | 3/4: un solo sistema de tokens. No hay modo oscuro (no se pidió) |
| Integridad | 1/4 | 4/4: el detector no marca nada en los templates públicos |
| **Total** | **6/20** | **18/20** |

## 2. Qué se revisó y cómo

Todo con Playwright, en la pantalla secundaria, con la app local.

1. **Capturas de página completa** en `design-review/after/` para las 8 vistas en los 9 anchos (72 imágenes, con el mismo nombre que `before/`).
   - Se tomaron con "movimiento reducido" para que las secciones con aparición al hacer scroll salgan completas.
   - Además se revisaron a tamaño real, por tramos, a 390, 768, 1024, 1280 y 1920.
2. **Medición automática** en cada página y ancho:
   - scroll horizontal;
   - elementos fuera de pantalla o recortados por su contenedor;
   - texto que desborda su caja;
   - zonas táctiles menores a 44 px;
   - contraste WCAG AA, calculado sobre el color de fondo real;
   - imágenes rotas o deformadas;
   - errores de consola y pedidos de red fallidos.
3. **Teclado:** se recorrió cada página con Tab a 390 y 1280, verificando que cada control tenga anillo de foco y que nada fijo (header, botón flotante, barra del creador) lo tape.
4. **Corrimientos al cargar (CLS):** las 7 páginas, con el carrito vacío y con productos, a 390 y 1280.
5. **Detector de Impeccable:** sobre los templates y sobre las páginas renderizadas a 390 y 1280.
6. **Guías aplicadas:**
   - `impeccable` audit, critique y polish;
   - `mobile-native`: bordes seguros, unidades de alto, resaltado al tocar, zoom de iOS, scroll;
   - `break-ui`: datos extremos.
7. **Recorridos de usuario** de punta a punta (ver punto 5) y **paneles** (ver punto 6).

## 3. Antes (Fase 0) → ahora

Estado de los problemas graves de la auditoría inicial (`DESIGN-PLAN.md` §1.3):

| # | Problema en `before/` | Ahora |
|---|---|---|
| P0-1 | "Nosotros" y "Contacto" llevaban al login | Anclas `/#nosotros` y `/#contacto` que funcionan |
| P0-2 | WhatsApp del header con número de relleno | Número real, en el footer y en el menú móvil |
| P0-3 | El checkout podía decir "Pedido enviado" sin abrir WhatsApp | Panel de éxito con número de pedido y botón de WhatsApp (probado) |
| P1-4 | Header desbordado en 375/390 | Entra desde 320 px (arreglado otra vez en esta fase para 320) |
| P1-5 | Scroll horizontal por `100vw` | Sin scroll horizontal en ningún ancho |
| P1-6 | Contraste insuficiente en todo el sitio | AA en todo el texto medido |
| P1-7 | Foco de teclado invisible | Anillo de 3 px en todos los controles |
| P1-8 | Creador sin teclado | Tarjetas con botones reales, recorridas con Tab |
| P1-9 | Botones e inputs en Arial | Todo en Poppins |
| P1-10 | Home de más de 45 MB (video + fotos de 3–6 MB) | Sin video; fotos WebP livianas con carga diferida |
| P1-11/12/13 | Registro roto y sin header; login sin menú móvil | Rediseñados en la Fase 4, sin errores de consola |
| P1-14 | Emojis como íconos | SVG en todo el sitio público |
| P1-15/16 | Galería vacía y banner roto | Galería con fotos y carrusel; sin imágenes rotas |
| P1-17 | Zonas táctiles chicas | Todas de 44 px o más |
| P1-18 | Sin `prefers-reduced-motion` | Respetado en todas las animaciones (Fase 5) |
| P3-45 | Sin favicon | Agregado en esta fase (isotipo del cupcake) |

## 4. Qué se arregló en esta fase

**Diseño y responsive**

1. **Header a 320 px:** el botón de menú quedaba cortado. Se achican un poco la marca y la separación por debajo de 360 px (`header.css`).
2. **Botones largos a 320 px:** "Actualizar contraseña" (perfil) y "Escribinos por WhatsApp" (Nosotros) empujaban la tarjeta fuera de la pantalla. Un botón ya no puede ser más ancho que su contenedor, y por debajo de 360 px el texto puede pasar a dos renglones (`components.css`).
3. **Contacto de la home a 1024 px:** la lista de datos se salía 4 px de la pantalla. Columnas con `minmax(0, …)` y botones que bajan de renglón si no entran (`home.css`).
4. **Link "Ver todos los productos":** medía 26 px de alto; ahora 44 (`home.css`).
5. **Galería:** el título y la bajada estaban pegados; ahora tienen el mismo aire que las demás secciones (`home.css`).
6. **Doble espacio entre "Lo que hacemos" y "Para encargar ya":** tienen el mismo fondo y sumaban dos rellenos. Ahora es el espacio de una sola sección (`home.css`).
7. **"Tu torta, a tu manera":** la "a" quedaba sola al final del renglón. Ahora va unida a "tu" (`index.html`).
8. **Precio vacío del creador:** el "—" en negro y grande parecía una barra. Ahora se ve liviano, con contraste suficiente (`builder.css` + una línea en `store.js`).
9. **Títulos de paso del creador:** un poco más de aire entre el título y la bajada (`builder.css`).
10. **"Confirmar pedido por WhatsApp":** en escritorio se partía en dos renglones; ahora entra en uno (`cart.css`).
11. **Footer:** WhatsApp e Instagram tienen dos renglones y quedaban pegados; ahora tienen separación (`footer.css`).

**Accesibilidad**

12. **Foco tapado:** en el celular, el botón flotante "Ver pedido" tapaba el control enfocado al navegar con Tab (WCAG 2.4.11). El navegador ahora deja ese espacio libre al desplazarse (`public.css`).
13. **Botón flotante y toast sobre el footer:** se confundían con el fondo oscuro. Tienen un aro claro de 2 px (`public.css`, `components.css`).

**Celular (`mobile-native`)**

14. **Bordes seguros del iPhone:** `viewport-fit=cover` en las 7 páginas. Sin eso, los `env(safe-area-inset-*)` que ya usaban el footer, el menú, la barra del creador y el botón flotante valían 0. Con el celular acostado, el margen lateral nunca es menor que la zona del notch (`tokens.css`, `--safe-x`).
15. **Color de la barra del navegador:** `theme-color` con el mismo fondo del header.
16. **Toques rápidos:** `touch-action: manipulation` en links y botones (sin la espera del doble toque). Mantener apretado un chip o un botón de ícono ya no selecciona su texto.
17. **Alto de modales en celular:** el mini-carrito y los avisos usan `dvh`, el alto visible real con la barra del navegador. El alto mínimo de las páginas usa `svh`.

**Datos extremos (`break-ui`)**

18. **Categorías largas en los stickers:** "Souvenirs personalizados" se cortaba por los dos lados. Ahora se corta al final con "…" (`products.css`, `home.css`).
19. **Carrito en celular:** con el precio y las cantidades al lado, la columna de texto quedaba tan angosta que "personalizada" se partía en dos. Por debajo de 480 px, precio y cantidades van en una fila debajo del detalle (`cart.css`).
20. **Creador a 320 px con precios altos:** con "$1.250.000" y los dos botones, la barra de abajo desbordaba la pantalla. Ahora los botones bajan de renglón si no entran (`builder.css`).
21. **Nombres largos de rellenos o tamaños:** se partían en cualquier letra ("reposter / o"). Ahora se cortan por sílabas con guion, o solo si una palabra no entra (`builder.css`, `public.css`).

**Detalles finales**

22. **Favicon:** ícono de la pestaña con el cupcake de la marca (`static/img/favicon.svg`) en las 7 páginas. Antes el navegador pedía `/favicon.ico` y recibía 404.

> Todos los arreglos se volvieron a medir después de cada tanda. La última pasada completa (8 vistas × 9 anchos) dio 0 hallazgos.

## 5. Recorridos de punta a punta (390 y 1280 px)

| Recorrido | 390 | 1280 |
|---|---|---|
| Ver productos, buscar ("pas" → 1 resultado) | ✓ | ✓ |
| Agregar desde el catálogo (toast + contador) | ✓ | ✓ |
| Armar una torta en 5 pasos y agregarla | ✓ | ✓ |
| Aplicar cupón (`DESCUENTO`: $162.000 → $81.000, se ve la fila del descuento) | ✓ | ✓ |
| Enviar el pedido vacío (4 errores en los campos) | ✓ | ✓ |
| Hacer el pedido (panel de éxito con número, link de WhatsApp armado, carrito vaciado) | ✓ | ✓ |
| Registrarse (lleva al login) | ✓ | ✓ |
| Login con contraseña incorrecta (aviso de error) | ✓ | ✓ |
| Login correcto (lleva al inicio) | ✓ | ✓ |
| Editar el perfil (aviso "Tus datos se guardaron") | ✓ | ✓ |
| Cerrar sesión (aviso de sesión cerrada) | ✓ | ✓ |

- 0 errores de consola en todos los recorridos.
- WhatsApp no se abrió de verdad: la prueba capturó el link para no enviar nada a un servicio externo.

## 6. Paneles de administración y del pastelero

- **Admin (1280):** las 9 pestañas cargan sin errores de consola ni pedidos fallidos (resumen, pedidos, productos, inventario, recetas, clientes, galería, armador, equipo).
- **Pastelero (1280):** con una cuenta de prueba creada desde "Equipo" y después borrada.
  - Entra a `/baker` y sus 3 pestañas (pedidos, inventario, recetas) cargan sin errores.
  - Si intenta abrir `/admin`, recibe 403, como corresponde.
- **Esta fase no tocó nada que usen los paneles:** cargan solo `main.css` y `admin.css`, y los JS de `admin/` no cambiaron.
- **Algo que ya estaba (no se rediseña):** en el celular (390 px), el **resumen del panel de admin tiene scroll horizontal**, porque las tarjetas de gráficos (`.chart-box` en `admin.css`) miden hasta 575 px. No es consecuencia del rediseño. Queda anotado para cuando se trabajen los paneles.

## 7. Prueba de datos extremos (`break-ui`)

Los datos se inyectaron donde entran normalmente, sin tocar la base:

- la respuesta HTML de `/products`;
- la respuesta de `/api/cake-config/public`;
- el `localStorage` del carrito.

**Casos probados:**

- **Catálogo:** 40 productos; nombres de 2 a 75 caracteres; descripciones largas, de una letra o ausentes; precios de $0 a $1.250.000; categorías largas; imagen rota; fotos que tardan 2,5 s.
- **Creador:** tamaños y rellenos con nombres largos; precio de $1.250.000; la API caída o vacía.
- **Carrito:** 16 productos con detalles de 300 caracteres; total de $5.097.091; mini-carrito con scroll.
- **Filtro sin resultados.**

**Lo que resiste bien:**

- nombres y descripciones se cortan con "…" en 2–3 renglones;
- precios y botones quedan alineados abajo en cada fila;
- la imagen rota muestra el cupcake de reemplazo;
- las fotos lentas muestran el brillo de carga;
- el mini-carrito hace scroll por dentro, con el total y el botón siempre visibles;
- el estado "No encontramos productos" se ve bien;
- el saludo del header usa solo el primer nombre, así que un nombre largo no lo rompe.

**Lo que fallaba** ya está arreglado (puntos 18 a 21).

**Para decidir:**

- **Nombre largo cortado con "…":** hoy no hay forma de leerlo completo en la tarjeta. Se podría agregar un `title` o, a futuro, una página de detalle del producto. Mi recomendación es dejarlo así hasta que exista esa página.
- **Creador con la API caída o vacía:** muestra tamaños de respaldo con **precios viejos** ($44.000…). Es lógica de negocio y quedó en `PENDIENTES-BACKEND.md` (punto 15).

## 8. Lo que queda pendiente

**Necesita un teléfono real** (no se puede verificar en el navegador de escritorio):

- bordes seguros del iPhone con notch o isla;
- que no haya zoom al tocar un campo en iOS (los campos ya tienen 16 px, así que no debería pasar);
- la respuesta inmediata al toque;
- el comportamiento con el teclado abierto en el checkout.

**Contenido y datos** (no son errores de diseño):

- Las fotos de la home son de stock (Unsplash) y conviene reemplazarlas por fotos reales del negocio antes de publicar.
- En la base hay **productos de prueba** ("Torta", "Pasta") y una foto de pasta en la galería del admin. Se ven en la vitrina, el catálogo y la galería. Conviene borrarlos o reemplazarlos desde el panel.
- Con solo 2 productos, la vitrina y el catálogo se ven vacíos a la derecha en escritorio. Con productos reales se completa la grilla.

**Interfaz, menor:**

- **Vista previa al compartir:** faltan las etiquetas Open Graph para que el link muestre imagen y título en WhatsApp e Instagram. Necesitan el dominio final y una imagen de portada.
- **Botón "Confirmar pedido por WhatsApp" en celular:** ocupa dos renglones a 390 px. Se ve prolijo; acortar el texto sería un cambio de contenido.
- **Hero a 768 px:** el texto queda alineado a la izquierda y la foto centrada debajo. Funciona; es una elección de diseño.
- **Cinta de texto en movimiento:** el detector la marca como "loop infinito". Es intencional (inspiración magicspoon), se pausa con el mouse y se apaga con "reducir movimiento".

**Lógica y backend:** ver `design-review/PENDIENTES-BACKEND.md`, 15 puntos. El 15 es nuevo de esta fase.

## 9. Datos de prueba

- **Pedidos #23 y #24** (cliente "QA Fase Seis"): creados en los recorridos y **borrados**. No queda ningún pedido de prueba.
- **Cuenta `qa.pastelero@dreambake.test`:** creada para probar el panel del pastelero y **borrada**.
- **Usuarios registrados en los recorridos:** `fase6.390.…@dreambake.test` y `fase6.1280.…@dreambake.test`. Quedan en la base junto con las otras cuentas de prueba. Se borran todas con:
  `DELETE FROM users WHERE email LIKE '%@dreambake.test';`
- **Cupón `DESCUENTO`:** se usó 2 veces. No tiene límite de usos.

## 10. Dónde están las capturas

- `design-review/after/<página>-<ancho>.png`: 72 capturas de página completa, comparables con `design-review/before/`.
- `design-review/after/break-ui/`: datos extremos (40 productos, carrito con 16 ítems, creador con nombres largos, fotos lentas, API caída).
- `design-review/after/flows/`: resumen del creador, checkout completo, pedido enviado, paneles de admin y del pastelero.
