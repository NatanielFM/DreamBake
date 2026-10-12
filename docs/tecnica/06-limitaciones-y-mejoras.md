# 6. Limitaciones conocidas y mejoras futuras

[← Volver al índice](../DOCUMENTACION-TECNICA.md)

DreamBake es hoy una **beta de prueba**. Lo funcional está implementado y se probó a mano durante el desarrollo, pero dos fases del plan **no se hicieron** y quedaron pospuestas a pedido del autor:

- **Fase 7 — Endurecimiento para producción:** perfil de producción en uso real, usuario de MySQL con permisos mínimos, arranque automático, datos reales y dominio propio.
- **Fase 8 — Pruebas y auditoría de seguridad:** base de tests, tests de servicios y de permisos, auditoría de seguridad completa, correcciones, pruebas en navegador, prueba manual con la clienta e informe final.

Este capítulo lista lo que eso implica, sin suavizarlo.

## Pruebas

- **Hay un solo test automático:** `DreambakeApplicationTests.contextLoads()`, que solo comprueba que la aplicación arranca. No hay tests de servicios (precios, stock, cupones, auditoría, backups) ni de permisos por rol. Cualquier cambio futuro puede romper una regla sin que nada lo detecte.
- Las pruebas que se hicieron fueron **manuales** (scripts con curl, el navegador y bases de datos descartables), registradas en las notas de trabajo del autor. La dependencia de Playwright está en `pom.xml` pero todavía no se usa.
- **No hubo una prueba de punta a punta con la dueña** de la pastelería usando el sistema en su trabajo diario.

## Seguridad y datos

- **No se hizo una auditoría de seguridad completa** (por ejemplo, contra el OWASP Top 10) ni una revisión de vulnerabilidades en las dependencias. Lo que está implementado (ver [Seguridad](05-seguridad.md)) surgió de revisiones puntuales durante el desarrollo.
- **La contraseña de MySQL está en el repositorio:** el archivo `src/main/resources/application.properties` (versionado) tiene el usuario `root` y su contraseña de desarrollo, y la misma contraseña figura en el historial de Git (commits `cd8d6e2`, `da7846f` y `f6efe56`). El repositorio de GitHub es privado, pero quien tenga acceso a él tiene la contraseña. **Hay que cambiarla en MySQL** y no volver a escribirla en un archivo versionado. Quitarla del historial requeriría reescribirlo, algo que no se hizo.
- **La aplicación usa `root` de MySQL**, con muchos más permisos de los necesarios. Un error o una vulnerabilidad en la app tendría control total de la base, y `root` puede quitar los triggers que protegen la auditoría. Falta crear un usuario propio solo con permisos sobre `dreambake_db` y sin `DROP`, `ALTER` ni `TRIGGER`.
- El perfil `prod` (`application-prod.properties`) está escrito pero **no se usó en un despliegue real**, y no cambia `ddl-auto=update` (en producción Hibernate seguiría modificando las tablas solo).
- La clave de "mantener la sesión iniciada" se genera al arrancar si no está configurada: al reiniciar la app, todos tienen que volver a entrar.
- Las sesiones abiertas en otro dispositivo no se cierran al cambiar la contraseña (la cookie de "mantener la sesión" sí deja de valer).
- Chart.js se carga desde un CDN **sin versión fija**.

## Servicios externos no probados con el servicio real

- **Correo:** el envío de emails está implementado (avisos de pedidos, recuperar contraseña, cumpleaños, campañas, alertas de backup y de auditoría), pero se probó solo contra un servidor de correo simulado. **No se envió ningún email real**: no hay una cuenta de Gmail configurada. Sin ella, todo lo que depende del correo no funciona en la práctica (la pantalla de recuperar contraseña ofrece WhatsApp en ese caso).
- **Google y Facebook:** el ingreso con esas cuentas se probó solo con proveedores simulados. No hay credenciales cargadas, así que los botones no aparecen. Falta crear las aplicaciones en Google Cloud y en Meta y, para Facebook, pasar su revisión.
- **Gemini (Bakey):** sí se probó con el servicio real. Usa el plan gratuito, que se satura rápido (errores de cupo o "alta demanda") y en el que Google puede usar lo que se le envía para mejorar sus productos; por eso Bakey nunca recibe datos personales completos.

## Operación

- **Los backups arrancan desactivados** hasta configurar `backup.directory` y `backup.mysqldump-path`. En la PC de desarrollo no están configurados y el panel lo avisa en rojo. La restauración se probó en bases de prueba.
- Los backups son **solo locales**: si se rompe el disco de la PC, se pierden junto con los datos. La copia automática a Google Drive se implementó y se quitó por decisión del autor (el código quedó en el historial, commit `e6e6716`).
- **La aplicación no arranca sola** con Windows (MySQL sí): después de un reinicio o un corte de luz hay que levantarla a mano.
- **No hay dominio propio:** durante las pruebas se publica desde la PC del autor con Tailscale Funnel. `app.public-url` es `localhost` por defecto, así que los links de los emails no servirían desde afuera hasta configurarla.
- Los contadores de límites, los bloqueos de login, las conversaciones de Bakey y las acciones pendientes viven **en memoria**: se pierden al reiniciar.

## Funcionalidad y contenido

- **Páginas legales incompletas:** `privacidad.html`, `terminos.html` y `eliminar-datos.html` tienen marcadores `[COMPLETAR: …]` (razón social, CUIT, dirección, email, plazos, forma de pago). No deben publicarse así con clientes reales.
- **Fotos de muestra:** las imágenes de la tienda son marcadores de posición de bancos de imágenes; hay que reemplazarlas por fotos reales del negocio.
- **Sin reporte de márgenes:** existe el costo por insumo y por receta y una ganancia estimada en el Resumen, pero no un reporte de márgenes por producto o período.
- **Tortas del armador sin receta:** no descuentan insumos ni entran en la ganancia estimada.
- **Modelo de datos:** nombre y teléfono duplicados en el pedido, roles como texto, estado "En preparación" sin botón que lo active (ver [Modelo de datos](04-modelo-de-datos.md#44-limitaciones-conocidas-del-modelo)).
- Parte de la lógica de negocio está en los controladores en lugar de en servicios (ver [Arquitectura](01-arquitectura.md#11-las-capas)).
- Las fotos WebP se guardan sin achicar ni recomprimir (Java no las lee sin una librería extra).
- Un email de aviso que falla no se reintenta solo.

## Mejoras futuras

Ordenadas de más a menos urgentes para usar el sistema con clientes reales:

1. **Cerrar la fase 7:** cambiar la contraseña de MySQL y sacarla del archivo versionado; usuario de MySQL con permisos mínimos; perfil `prod` con `ddl-auto=validate`; DreamBake como servicio de Windows; dominio propio con HTTPS; cuenta de correo real; activar los backups y llevar una copia fuera de la PC.
2. **Cerrar la fase 8:** tests de servicios y de permisos (la lista de casos ya está anotada), auditoría de seguridad completa, revisión de dependencias, pruebas en navegador y una prueba guiada con la dueña.
3. **Completar el contenido:** páginas legales y fotos reales.
4. **Funcionalidades pedidas en las entrevistas:** reporte de ganancias y márgenes; recetas para las tortas del armador; integración con WhatsApp para los avisos; evolucionar la tienda a una tienda online con pago.
5. **Mejoras técnicas:** mover la lógica de pedidos a un servicio; roles en una tabla; fijar la versión de Chart.js; reintentos de emails fallidos; copia automática de backups fuera de la PC.
6. **Documentación de la tesis:** diagramas de casos de uso, de clases y de dominio (por ingeniería inversa del código).
