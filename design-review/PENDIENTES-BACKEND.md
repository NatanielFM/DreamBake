# Pendientes de backend y lógica de negocio

> Anotados durante el rediseño de la UI (fases 0–6 y arreglo de seguridad, 2026-10-05/06).
> No se resuelven en las fases de UI: cada uno necesita una decisión del autor y, en varios casos, tocar Java o la base.

## Seguridad
1. **Consumir cupones sigue siendo público** (`POST /api/coupons/consume/{code}`): cualquiera que conozca un código puede gastar sus usos. Hoy tiene límite por IP (`RateLimitFilter`). Solución real: que el servidor descuente el cupón al guardar el pedido (`POST /api/orders`), lo que implica mandar el código del cupón dentro del pedido (cambia los datos enviados).
2. **El total del pedido lo calcula el navegador**: alguien podría mandar un precio falso. Hoy lo mitiga que el personal confirma el pedido y la seña por WhatsApp. Solución real: recalcular en el servidor (precios de productos por id y tamaños de torta desde `cake_sizes`), lo que requiere mandar ids en los ítems.
3. **`application.properties` para producción** (no se toca por regla del proyecto): apagar DevTools, HTTPS (cookie `Secure`), usuario de MySQL propio en vez de `root`, contraseña fuera del repositorio.
4. **`'unsafe-inline'` en la Content-Security-Policy**: necesario mientras los paneles usen scripts y `onclick` dentro del HTML. Para quitarlo hay que pasar ese código a archivos `.js`.
5. **Cuentas de prueba en la base local**: `qa.rediseno@dreambake.test` (quedó como ADMIN), `atacante.prueba@dreambake.test` y las del registro de las fases 4 y 6 (`fase4.…@dreambake.test`, `fase6.…@dreambake.test`). Borrar con:
   `DELETE FROM users WHERE email LIKE '%@dreambake.test';`
6. `TestController` (`GET /test`) no tiene uso: se puede borrar (requiere confirmación).

## Lógica de negocio
7. **WhatsApp configurable desde el panel** (pedido por la clienta): hoy el número está fijo en `store.js` (`BUSINESS_PHONE`). Requiere guardar un ajuste en la base (entidad/tabla de configuración) + endpoint + campo en el panel.
8. **Retiro o envío en el checkout**: el negocio hace ambos pero el pedido no lo registra (hoy se aclara en "Aclaraciones"). Decidir si se agrega un campo.
9. **Turno de entrega**: se envía `deliveryShift` ("Por la mañana / Por la tarde") desde la Fase 3, pero el panel de admin todavía no lo muestra.
10. **Sticker "Más vendido"**: no existe el dato. Opción: campo booleano `featured` en `Product` (cambio de entidad).
11. **"Recordarme" en el login**: no estaba configurado `rememberMe()`, así que la casilla no hacía nada; en la Fase 4 se quitó del formulario. Si se quiere, implementar `rememberMe()` en `SecurityConfig` y volver a agregar la casilla (`name="remember-me"`).
12. **Mensaje tras registrarse**: `AuthController` redirige a `/login` sin avisar que la cuenta se creó (se podría usar `?registered`).
13. **Relación Pedido–Cliente, costos por insumo/receta y reportes de márgenes**: pendientes de la tesis (ver CLAUDE.md).
14. **Fotos de stock**: las imágenes de Unsplash son marcadores de posición; reemplazar por fotos reales del negocio antes de publicar (ver PRODUCT.md).
15. **Tamaños de respaldo del creador con precios viejos** (encontrado en la Fase 6): si `/api/cake-config/public` falla o devuelve la lista vacía (por ejemplo, si el admin deshabilita todos los tamaños), `store.js` muestra 6 tamaños escritos en el código con precios que no son los reales ("10 porciones $44.000", etc.). Un cliente podría mandar un pedido con un precio equivocado. Opciones: mostrar un aviso "No pudimos cargar los tamaños, escribinos por WhatsApp" en lugar del respaldo, o que el servidor recalcule el precio (ver punto 2).

## Encontrado en la Fase 7 (paneles)
16. **No se pueden borrar fotos de la galería**: `GalleryRestController` solo tiene listar y subir. Para borrar una foto haría falta un `DELETE /api/gallery/{id}` (y borrar el archivo de `uploads/`).
17. **"Consumir por pedido" (Inventario)** llama a `/api/inventory/test-deduct/{id}`: parece una herramienta de prueba (antes pedía "el ID del pedido para depurar deducción"). Decidir si se deja como función real o se quita, igual que se quitó "Generar pedido de prueba".
18. **CSP**: `https://via.placeholder.com` en `img-src` ya no se usa (los productos sin foto ahora muestran un dibujo propio); se puede sacar de `SecurityConfig`.
