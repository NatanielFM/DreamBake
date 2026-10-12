# DreamBake — Documentación técnica

Este documento explica **cómo está hecho DreamBake por dentro**: la arquitectura, qué hace cada archivo, el modelo de datos, la seguridad, las limitaciones y cómo instalarlo. Está escrito para la defensa del trabajo final y para quien tenga que mantener el sistema más adelante. Para una presentación general del proyecto (qué hace, capturas, tecnologías), ver el [README](../README.md).

## Resumen en una página

- **Qué es:** un sistema web de gestión para la pastelería Dulce Sueños Posadas (Posadas, Misiones). Reúne la tienda para los clientes y los paneles de trabajo de la dueña (administradora) y del pastelero.
- **Para quién:** personal no técnico. Todo se usa desde el navegador, en PC o celular, sin instalar nada.
- **Cómo está hecho:** una sola aplicación **Spring Boot 4.0.6** (Java 21) con vistas **Thymeleaf** y una API REST bajo `/api` que consume JavaScript sin frameworks. Los datos están en **MySQL 8**; Hibernate crea y actualiza las tablas a partir de las entidades.
- **Servicios externos:** Gmail (envío de emails), Google y Facebook (ingreso con esas cuentas) y Gemini (el asistente Bakey). Todos son opcionales: sin su clave, la función se desactiva y el resto anda igual.
- **Estado:** **beta de prueba.** Las funcionalidades están implementadas, pero quedaron pospuestas la fase 7 (endurecimiento para producción) y la fase 8 (pruebas automáticas y auditoría de seguridad completa). Ver [Limitaciones conocidas y mejoras futuras](tecnica/06-limitaciones-y-mejoras.md).

## Índice

| # | Documento | Qué contiene |
|---|---|---|
| 1 | [Arquitectura general](tecnica/01-arquitectura.md) | Capas del sistema, tareas programadas y el recorrido de un pedido de punta a punta. |
| 2 | [Código Java por paquete](tecnica/02-codigo-java.md) | Qué hace cada clase de `src/main/java`, agrupadas por paquete. |
| 3 | [Plantillas, estilos y scripts](tecnica/03-recursos.md) | Qué hace cada archivo de `src/main/resources`: configuración, vistas, emails, CSS y JavaScript. |
| 4 | [Modelo de datos](tecnica/04-modelo-de-datos.md) | Entidades, relaciones, diagrama entidad-relación y la cadena Pedido → Ítem → Producto → Receta → Insumo. |
| 5 | [Seguridad](tecnica/05-seguridad.md) | Roles y rutas, CSRF, CSP, límites por IP, auditoría con cadena de hashes y qué ve el pastelero. |
| 6 | [Módulos con documento propio](#módulos-con-documento-propio) | Bakey y la restauración de backups. |
| 7 | [Limitaciones conocidas y mejoras futuras](tecnica/06-limitaciones-y-mejoras.md) | Lo que falta, lo que no se probó y los riesgos, sin suavizar. |
| 8 | [Instalación y configuración](tecnica/07-instalacion.md) | Cómo instalar, configurar y levantar el sistema en Windows. |

### Cómo leerlo

- Para entender el sistema en 15 minutos: este resumen, la [arquitectura](tecnica/01-arquitectura.md) y el [modelo de datos](tecnica/04-modelo-de-datos.md).
- Para buscar un archivo puntual: [código Java](tecnica/02-codigo-java.md) o [recursos](tecnica/03-recursos.md) (usá la búsqueda del navegador con el nombre del archivo).
- Para preguntas de seguridad o de "¿esto está listo para usarse con clientes reales?": [seguridad](tecnica/05-seguridad.md) y [limitaciones](tecnica/06-limitaciones-y-mejoras.md).

## Módulos con documento propio

Dos partes del sistema tienen su propia documentación, más detallada. No se repite acá:

- **[Bakey, el asistente de IA](BAKEY.md):** cómo funciona el chat de los paneles, el flujo pedido → Gemini → herramientas → tarjeta de confirmación, la lista de herramientas por rol, las medidas de seguridad (el modelo nunca cambia datos por su cuenta) y sus límites.
- **[Cómo restaurar una copia de seguridad](RESTAURAR-BACKUP.md):** guía para personas no técnicas: restaurar desde el panel, deshacer una restauración y recuperar los datos a mano si se rompió la computadora.

## Convenciones del código

- **Idioma:** la interfaz, los mensajes y los comentarios están en español (Argentina); los nombres de clases, métodos y variables, en inglés.
- **Comentarios didácticos:** el código explica en comentarios qué hace cada parte, porque es un proyecto académico que se defiende frente a profesores.
- **Montos y fechas** en el navegador siempre con `DreamFormat` (`static/js/format.js`): `$ 12.345,50` y `12/10/2026`.
- **Toda acción que modifica datos se audita** (ver [Seguridad](tecnica/05-seguridad.md#58-registro-de-auditoría)).
