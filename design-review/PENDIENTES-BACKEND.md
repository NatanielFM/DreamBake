# Pendientes de backend y lógica de negocio

> Anotados durante el rediseño de la UI (fases 0–7, 2026-10-05/06).
> **Resueltos en la rama `pendientes-backend` (2026-10-07)**, con las decisiones del autor:
> se aprobaron los cambios de base (todos agregan, ninguno borra), quitar "Consumir por pedido",
> "Más vendido" automático y los borrados de archivos y cuentas de prueba.

Estado: ✔ resuelto · ◐ resuelto en parte · ✖ no se puede resolver desde el código

## Seguridad
1. ✔ **Cupones gastados por cualquiera.** Se borró el endpoint público `POST /api/coupons/consume/{code}`. Ahora el carrito manda el código dentro del pedido y el servidor lo vuelve a validar y le descuenta un uso al guardarlo (`CouponService`, `OrderPricingService`). El pedido guarda el cupón usado (`orders_table.coupon_code`).
2. ✔ **Total calculado por el navegador.** El carrito manda el id del producto (`product.id`) o del tamaño de torta (`cakeSizeId`), y el servidor busca los precios reales, aplica el cupón y calcula el total (`OrderPricingService`). Los carritos guardados antes del cambio (sin ids) se resuelven por nombre. Si un producto o tamaño ya no está disponible, el cliente ve un mensaje claro.
3. ◐ **Configuración de producción.** `application.properties` no se tocó (regla del proyecto). Se creó `application-prod.properties`, que solo se usa al arrancar con `--spring.profiles.active=prod`: la base y las claves salen de variables de entorno, cookie `Secure`/`HttpOnly`, errores sin detalles técnicos y DevTools apagado. **Falta del lado del servidor:** crear un usuario de MySQL propio (no `root`), definir las variables de entorno y poner HTTPS.
4. ✔ **`'unsafe-inline'` en la CSP para scripts.** Se pasaron a archivos `.js` todos los `<script>` y los `onclick`/`onchange` de las plantillas y del HTML que arma el JS (`theme-init.js`, `catalog.js` y listeners en `main.js`, `orders.js`, `cake-config.js` y `store.js`). La CSP ahora es `script-src 'self' https://cdn.jsdelivr.net`. Para estilos se mantiene `'unsafe-inline'` (los atributos `style="…"` no ejecutan código).
5. ✔ **Cuentas de prueba.** Se borraron las 8 cuentas `@dreambake.test` (incluida `qa.rediseno`).
6. ✔ **`TestController`.** Borrado (la ruta `/test` ya no existe).

## Lógica de negocio
7. ✔ **WhatsApp configurable.** Tabla nueva `business_settings` (clave/valor) + `BusinessSettingService` + `GET/PUT /api/settings` (solo admin) + pestaña **Ajustes** en el panel, con "Probar el número". Todos los links de WhatsApp de la tienda y el mensaje del pedido usan ese número (`PublicViewAdvice` → `whatsappNumber`).
8. ✔ **Retiro o envío.** El checkout pregunta "¿Cómo lo recibís?" (Lo retiro / Envío a domicilio) y pide la dirección solo si es envío. Se guarda en `orders_table.delivery_method` y `delivery_address`, sale en el mensaje de WhatsApp, en el panel y en el recibo. El pedido manual y la edición del pedido también lo tienen.
9. ✔ **Turno de entrega en el panel.** Se ve debajo de la fecha en la tabla de pedidos, en "Ver pedido" (editable) y en el recibo.
10. ✔ **Sticker "Más vendido".** Automático, sin cambiar la base: lo llevan los 3 productos del catálogo más pedidos (pedidos confirmados, en preparación o entregados), si se pidieron al menos 2 veces (`WebController.findBestSellerIds`). Se ve en la vitrina de la home y en el catálogo.
11. ✔ **"Recordarme" en el login.** Casilla "Mantener la sesión iniciada" + `rememberMe()` en `SecurityConfig` (cookie firmada de 14 días; se borra al cerrar sesión). La clave de firma sale de `dreambake.remember-me-key` (variable de entorno en producción); si no está, se genera una al arrancar.
12. ✔ **Mensaje tras registrarse.** `/login?registered` muestra "¡Listo, tu cuenta se creó!" y el email ya escrito (viaja como *flash attribute*, no en la dirección).
13. ✔ **Relación Pedido–Cliente, costos y márgenes.**
    - `orders_table.customer_id`: al confirmar un pedido queda unido a su cliente (se crea si su teléfono no existe). En Clientes se ve cuántos pedidos tiene y cuánto gastó, con el botón "Ver pedidos". Los pedidos viejos se encuentran por teléfono. Borrar un cliente no borra sus pedidos (se desvinculan).
    - `inventory_items.unit_cost`: costo por unidad de cada insumo (campo en el formulario y columna en Inventario, solo admin).
    - Costo de cada receta calculado en el servidor (`Recipe.getEstimatedCost` / `isCostComplete`, sin columnas nuevas). Se ve en Recetas, en el formulario de Productos ("Costo de los insumos · Ganancia") y en el Resumen ("Ganancia estimada" del año y tabla por producto). El panel del pastelero no ve ningún costo.
    - Límite: las tortas del creador no tienen receta, así que los pedidos con tortas personalizadas quedan fuera de la ganancia estimada (el Resumen lo aclara).
14. ✖ **Fotos de stock.** Las fotos de la home (y la nueva imagen para compartir `img/og-portada.jpg`, hecha con una de ellas) son de Unsplash. Hay que reemplazarlas por fotos reales del negocio.
15. ✔ **Tamaños de respaldo con precios viejos.** Se borró la lista escrita en `store.js`. Si no llegan los tamaños, el creador muestra "No pudimos cargar los tamaños de torta" con un botón de WhatsApp. Además, el servidor ya no acepta precios del navegador (punto 2).

## Encontrado en la Fase 7 (paneles)
16. ✔ **Borrar fotos de la galería.** `DELETE /api/gallery/{id}` borra la fila y el archivo de `uploads/` (solo dentro de esa carpeta). Si un producto usa la foto, no la borra y dice cuál. En la pestaña Galería cada foto tiene su botón de tacho.
17. ✔ **"Consumir por pedido".** Quitado (botón, código JS y endpoint `/api/inventory/test-deduct`): confirmar un pedido ya descuenta los insumos, así que descontaba dos veces.
18. ✔ **CSP con `via.placeholder.com`.** Quitado de `img-src`.

## Otros arreglos hechos en esta rama
- **Confirmar un pedido sin insumos suficientes** lo confirmaba directo con seña $0, sin preguntar cuánto pagó el cliente. Ahora avisa "Faltan insumos" y después abre la ventana de confirmación de siempre.
- **Editar un pedido** juntaba todos los productos en un solo texto (se perdía el precio y la receta de cada uno) y borraba el email. Ahora los productos solo se reemplazan si se cambió su texto, y el email se conserva.
- **Recibo:** decía "¡Gracias por elegir DreamBake!"; ahora dice "Dulce Sueños".
- **Vista previa al compartir** (pendiente del QA de la Fase 6): etiquetas Open Graph en la home, el catálogo y el creador.
- **Foco del teclado en las ventanas** (pendiente de la Fase 7): al abrir una ventana el foco va al primer campo; al cerrarla vuelve al botón que la abrió.
- **Columna de dirección:** la base la creó de 255 caracteres; la app limita la dirección a 250.
- Se quitaron los `System.out.println` de prueba de `OrderRestController`.

## Lo que sigue pendiente (no es código)
- Punto 3: usuario propio de MySQL, variables de entorno y HTTPS en el servidor donde se publique.
- Punto 14: fotos reales del negocio.
- `cookies.txt` (sesión de una prueba vieja con curl) se borró de la carpeta, pero sigue en el historial de git. Si la cookie era de una sesión de administrador, ya venció; no hace falta hacer nada más.
- Decisiones de la tesis que siguen abiertas: recetas para las tortas del creador (para que entren en la ganancia), recordatorios automáticos de entrega, integración con WhatsApp.
