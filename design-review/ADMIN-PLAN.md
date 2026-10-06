# Fase 7: plan de los paneles de administración y del pastelero

> Rama `cambios-ui` · 2026-10-06 · solo plan, **no se tocó código**.
> Capturas del estado actual (390 y 1440 px, las 9 pestañas y el menú lateral) en `design-review/admin-before/`.

## 1. Diagnóstico

**Lo que funciona y se conserva**

- Una sola página con pestañas y un menú lateral.
- Los mismos módulos en los dos paneles; el JS ya está separado por pestaña.
- Íconos SVG.
- Modales y confirmaciones globales (`showConfirmModal`).
- El calendario de pedidos como vista principal de Pedidos.

**Problemas (por gravedad)**

| # | Problema | Dónde |
|---|---|---|
| 1 | **En el celular no se puede trabajar con los pedidos.** El calendario se sale de la pantalla (la página mide 464 px en un celular de 390), las 7 columnas de la tabla quedan cortadas y los botones Confirmar / Ver / Cancelar no se ven sin scroll horizontal. | Pedidos · 390 |
| 2 | **Tablas cortadas en el celular**, en Inventario, Clientes, Recetas y en las tablas del Resumen. Las columnas que importan (stock mínimo, acciones) quedan fuera de la pantalla. | Varias · 390 |
| 3 | **El Resumen tiene scroll horizontal** en el celular: las tarjetas de gráficos llegan a 575 px. | Resumen · 390 |
| 4 | **Colores de estado de Bootstrap**, fuera de la paleta: amarillo, celeste, verde y rojo genéricos. "En preparación" y "Agendado" se ven iguales. | Pedidos, Resumen, Inventario |
| 5 | **Botones parecidos que no dicen cuál es el principal.** "Generar Pedido de Prueba" tiene el mismo peso que "+ Nuevo Pedido" y se parte en 3 renglones. "Cancelar" es una ✕ roja sin texto. | Pedidos, Inventario |
| 6 | **Zonas táctiles chicas**: íconos del header de 38 px, ✕ de 28 px, botones `btn-sm` de 30 px, flechas del calendario. | Todo |
| 7 | **Emoji 🔍** en los buscadores (prohibido por CLAUDE.md). Los botones `.btn` no siempre heredan Poppins. | Buscadores |
| 8 | **Header rosa intenso con texto blanco** (contraste 3,9:1) y "Hola, Prueba" oculto en el celular. Ninguna pestaña tiene título propio: el "dónde estoy" solo se ve en el menú. | Header |
| 9 | **~370 estilos inline** (admin.html 225, baker.html 143) y ~165 dentro del HTML que arma el JS, con colores sueltos (`#666`, `#d97706`, `#dc2626`…). | Todo |
| 10 | **Estados vacíos pobres**: un renglón gris ("No hay pedidos…"), "Sin datos aún" pegado al eje del gráfico, galería y equipo sin explicación. | Varias |
| 11 | **Modales**: ✕ de texto sin área táctil y alturas fijas (`80vh`) que en el celular dejan el botón de guardar fuera de vista. | Modales |
| 12 | **Movimiento sin control**: `transition: all`, elevación al pasar el mouse sobre las tarjetas de KPI y sin `prefers-reduced-motion`. | admin.css |
| 13 | **Gráficos con colores fuera de la paleta** (azul, violeta, verde) y "Sin datos aún" mal ubicado. | Resumen |

## 2. Enfoque

**Refinar, no reinventar:** misma estructura, mismas pestañas, mismo JS. Cambia la capa visual y se arreglan el celular y la legibilidad. Prioridad: que Vero, Maca y el equipo entiendan cada pantalla sin pensar, y que la dueña pueda ver y confirmar pedidos desde el teléfono.

### 2.1 Base compartida (antes de la primera pestaña)

- **Carga de estilos** en `admin.html` y `baker.html`, en este orden: `main.css` (no se toca) → `tokens.css` → `components.css` → `admin.css`. Así los paneles usan los mismos colores, tipografía, foco y componentes que el sitio público. También se suma el favicon.
  - Ya verifiqué que no hay choques de nombres de clase. La única coincidencia es `.chip`, que el panel usa con `.active`; se resuelve en `admin.css`.
- **`admin.css` reescrito sobre los tokens**, conservando **todos los nombres de clase** que usan el HTML y el JS: `.btn`, `.btn-pink`, `.btn-red`, `.btn-grey`, `.btn-uniform`, `.btn-sm`, `.form-input`, `.admin-table`, `.status-badge`, `.modal-*`, `.tab-btn`, `.tab-content`, `.kpi-*`, `.calendar-*`, `.chip`, `.toggle-switch`, `.product-card-admin`, `.gallery-*`, etc. Solo cambia cómo se ven.
  - `--accent`, `--danger` y `--warning` quedan como alias de los tokens, para el JS y los estilos inline que todavía las usen.
- **Header**:
  - fondo claro (`--blush-50`), como el sitio, con el isotipo + "Panel" / "Panel del pastelero";
  - botones de 44 px;
  - en el celular: menú, título de la pestaña actual, campana y salir;
  - el saludo pasa al menú lateral.
- **Menú lateral**:
  - ítems de 48 px con ícono SVG + texto y la pestaña activa en tinta (`--ink`), como los chips del sitio;
  - pie con "Ir a la tienda" y "Cerrar sesión";
  - en el celular, el mismo cajón que hoy, con fondo oscurecido, cierre con Esc y foco atrapado.
- **Encabezado de cada pestaña** (nuevo, solo HTML/CSS): título + una línea de ayuda + **una sola acción principal** a la derecha (en el celular, debajo, a todo el ancho). Ejemplo: "Pedidos · Confirmá, prepará y entregá" + [Nuevo pedido].
- **Botones**:
  - principal = rosa `--color-primary` (el mismo del sitio, con contraste AA);
  - secundario = borde tinta;
  - peligro = cereza (`--cherry-500`, ya en la paleta);
  - todos de 44 px de alto como mínimo y con texto; las ✕ solas pasan a "Cancelar" con ícono.
- **Formularios**: `.form-input` toma el estilo de `.input` del sitio (52 px, 16 px de letra, foco rosa) y los labels pasan a ser legibles (tinta, semibold).
- **Tablas legibles**:
  - encabezado fijo arriba, filas de 56 px, números con cifras parejas y separadores suaves;
  - **en el celular (< 768 px) cada fila se convierte en una tarjeta** con "Etiqueta: valor" y las acciones abajo, a todo el ancho;
  - se logra con CSS + un atributo `data-label` en cada `<td>` que arma el JS. Es un atributo nuevo: no se quitan ids ni clases.
- **Estados con texto + color + ícono** (nunca solo color). Ver la decisión 1.
- **Estados vacíos**: ícono de línea + una frase de qué significa + la acción que corresponde. Ejemplo: "Todavía no hay pedidos nuevos. Cuando un cliente compre en la web, aparece acá." + [Nuevo pedido].
- **Modales**:
  - tarjeta con radio grande y ✕ de 44 px;
  - en el celular, hoja que sube desde abajo, a todo el ancho, con los botones fijos al pie;
  - cierre con Esc y tocando el fondo;
  - `showConfirmModal(title, message, okText, okBtnClass, onConfirm)` queda **igual** (sigue recibiendo `btn-red` o `btn-pink`).
- **Movimiento mínimo**: solo presionar (escala 0,97), aparición de modales y cajón (opacidad + desplazamiento corto) y el cambio de pestaña sin animación. Sin elevación al pasar el mouse. Todo respeta `prefers-reduced-motion`.
- **Estilos inline**: se pasan a clases en `admin.css` a medida que se trabaja cada pestaña, en el HTML y en el HTML que arma el JS.

### 2.2 Pestaña por pestaña (en este orden; cada una se verifica antes de seguir)

1. **Resumen**
   - Encabezado "Resumen", con el filtro de año y "Descargar informe" como acción secundaria.
   - KPIs en una grilla de 2 columnas en el celular y 4 en escritorio, sin degradado: la tarjeta destacada en tinta.
   - Gráficos con colores de la paleta (rosa, cereza, tinta y sus tonos) y "Sin datos todavía" centrado.
   - Las tablas del final pasan a tarjetas en el celular y se elimina el scroll horizontal.
2. **Pedidos** (la más importante para el celular)
   - Calendario compacto en el celular: puntos con la cantidad, no etiquetas que no entran. Al tocar el día se abre la lista.
   - Pestañas Nuevos / Agendados / Completados como chips grandes con contador.
   - Buscador sin emoji.
   - Tabla → tarjetas en el celular: número, cliente, fecha de entrega, total, estado y stock, con [Confirmar] como botón principal y [Ver] / [Cancelar] secundarios.
   - Modal de detalle legible.
3. **Productos**
   - El visor de edición queda como formulario de 2 columnas en escritorio y de 1 en el celular.
   - Tarjetas del catálogo con el nombre y el precio legibles (sin degradado negro), y "Oculto" como estado con texto.
   - Paginación de 44 px.
4. **Inventario**
   - Filtros como chips.
   - Tabla → tarjetas, con estados "Stock bajo" y "Vence pronto" con texto + ícono (no solo el borde de color).
   - Botones con jerarquía: [Nuevo insumo] principal; "Consumir por pedido" y "Generar pedido" secundarios.
5. **Recetas**: lista de recetas e ingredientes legible, con el modal de edición cómodo en el celular.
6. **Clientes**: tabla → tarjetas, filtros por etiqueta como chips y modal de ficha.
7. **Galería**: grilla de fotos con la tarjeta "Subir foto" clara, borrar con confirmación y un estado vacío explicativo.
8. **Armador de tortas**: tamaños y opciones en tarjetas/listas legibles, con los interruptores de "habilitado" con texto.
9. **Equipo**: lista de pasteleros en tarjetas con [Agregar pastelero] principal y un estado vacío.

Después de cada pestaña del panel del pastelero que comparte JS (Pedidos, Inventario, Recetas), **se verifica también `baker.html`**.

### 2.3 Lo que NO se toca

- Java, entidades, `SecurityConfig`, `application.properties`.
- La lógica de los JS: qué endpoints llaman, qué datos mandan, validaciones.
- Ids, clases, `data-*` y la firma de `showConfirmModal` / `showErrorAlert`.
- `main.css`.
- El área de impresión del recibo (`.print-area`, `@media print`): se revisa que siga imprimiendo igual.

## 3. Contratos que se mantienen

- **Navegación:** `.tab-btn[data-tab]`, `.tab-content#resumen…#equipo`, el evento `tabChanged`, `#sidebar-toggle`, `#sidebar-backdrop`, `body.sidebar-toggled`, el corte de 880 px que usa `main.js`.
- **Modales:** `.modal-overlay[hidden]`, `.close-modal`, `#modal-confirm` / `#confirm-title` / `#confirm-message` / `#btn-confirm-ok` / `#btn-confirm-cancel`, `#modal-alert` / `#alert-message`, y los modales de pedidos (`#modal-day-orders`, `#modal-view-order`, `#modal-confirm-order`) con su orden de capas.
- **Clases que el JS pone o busca:**
  - `.chip.active` en `#inventory-filters`, `#client-filters`, `#cake-option-chips` y productos;
  - `.btn-*` que se pasan a `showConfirmModal`;
  - `.status-*`, `.tr-expired`, `.tr-low-stock`, `.order-badge`, `.completed-badge`, `.calendar-day.today/.empty`, `.product-card-admin.disabled`, `.readonly-mode`, `.page-btn.active`.
- **Header:** `#btn-notifications`, `#notif-badge`, `#notif-dropdown`, `#notif-list`, `#btn-logout-header`, `#logoutForm`, `#btn-theme-toggle` (según la decisión 2).
- **`baker.html`:** usa `admin/main.js`, `inventory.js`, `recipes.js` y `orders.js`; todo se prueba en los dos paneles.

## 4. Verificación (en cada pestaña)

- Playwright en la pantalla secundaria, a **390 y 1440 px**, con capturas en `design-review/admin-after/`.
  - Sin scroll horizontal, zonas táctiles de 44 px, contraste AA y foco visible.
  - Sin errores de consola.
- **Funcionalidad de la pestaña** con datos de prueba que después se borran: por ejemplo, crear/editar/borrar un insumo, confirmar un pedido de prueba, abrir y cerrar cada modal y usar `showConfirmModal`.
- **Las pestañas compartidas, también en el panel del pastelero**, con una cuenta de prueba temporal.
- Al final:
  - movimiento reducido emulado;
  - impresión del recibo (vista previa);
  - `.\mvnw.cmd compile`;
  - resumen en español, sin commit.

## 5. Decisiones que necesito

1. **Colores de estado.** La paleta del sitio no tiene verde ni azul; el `:root` de `admin.css` sí tiene ámbar (`--warning`) y rojo (`--danger`). Propuesta, siempre con texto + ícono:
   - Pendiente = ámbar;
   - Agendado = rosa;
   - En preparación = rosa oscuro;
   - Completado = tinta con tilde;
   - Cancelado = cereza tachado;
   - Stock: "Alcanza" = tinta, "Stock bajo" = ámbar, "Sin stock" = cereza.

   La alternativa es sumar un verde solo para "Completado" y "Alcanza" (se entiende al instante, pero es un color nuevo).
2. **Modo oscuro.** Hoy los dos paneles tienen un botón de tema oscuro. Mantenerlo implica rehacer los tokens en versión oscura (tintas ciruela, nada de grises nuevos) y probar el doble. Quitarlo simplifica (menos botones para personal no técnico).
3. **"Generar pedido de prueba"** (Pedidos e Inventario) es una herramienta de desarrollo visible para el personal. Opciones:
   - dejarlo como enlace discreto;
   - ocultarlo con CSS: el botón sigue en el HTML, así el JS no se rompe.
4. **Pestaña inicial en el celular.** Hoy siempre abre "Resumen". Propongo que en el celular el admin abra en **Pedidos**, que es lo que la dueña revisa desde el teléfono (es una línea de JS en el panel; el pastelero ya abre en Pedidos).

## 6. Decisiones tomadas (2026-10-06)

1. **Estados:** se suma un **verde** solo para "Completado" y "Alcanza / Realizable" (excepción explícita del autor a la regla de no sumar colores). El resto sigue la propuesta: Pendiente ámbar, Agendado rosa, En preparación rosa oscuro, Cancelado cereza; siempre con texto + ícono.
2. **Modo oscuro:** se **mantiene**. Tokens oscuros derivados de la tinta ciruela; cada pestaña se verifica en los dos temas.
3. **"Generar pedido de prueba":** se **elimina** el botón (y su bloque en `orders.js`, que queda sin uso).
4. **Pestaña inicial en el celular:** sigue siendo **Resumen**, como hoy.

## 7. Resultado (2026-10-06)

Hecho pestaña por pestaña, verificado con Playwright a 390 y 1440 px, en modo claro y oscuro y en los dos paneles cuando comparten JS. En todas: sin scroll horizontal, zonas táctiles de 44 px, contraste AA, sin errores de consola. Capturas en `design-review/admin-after/`.

**Errores de comportamiento que aparecieron y se corrigieron** (solo JS de interfaz, sin tocar endpoints):
- `team.js` enganchaba Editar/Eliminar a todos los botones `.btn-edit`/`.btn-delete` del panel: al borrar un insumo, cliente o receta aparecía "¿eliminar este pastelero?" y podía mandar `DELETE /api/users/null`.
- `products.js` usaba `.chip` de toda la página: los filtros de Productos desmarcaban los de Inventario y Clientes.
- Productos sin foto: se guardaba "/300" como foto (venía de `via.placeholder.com`).
- Sub-pestañas de Pedidos y filtros de Inventario, Clientes y Armador: si se tocaba el contador o el ícono del botón, el filtro quedaba vacío (`e.target` → `e.currentTarget`).
- `confirm()`/`alert()` nativos en Equipo y Pedidos → ventanas del panel.

**Diferencias con el plan:** Galería no tiene "borrar" (no existe el endpoint; anotado en PENDIENTES-BACKEND, punto 16). En el panel del pastelero se quitaron 6 ventanas que solo usa el admin y la carga de Chart.js (no se usaban ahí).
