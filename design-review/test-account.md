# Cuenta de prueba (solo entorno local)

La cuenta `qa.rediseno@dreambake.test`, creada en la Fase 0 del rediseño para capturar `/profile`
y probar los paneles, **se borró el 2026-10-07** junto con las demás cuentas `@dreambake.test`
(ver `PENDIENTES-BACKEND.md`, punto 5).

Para volver a probar: registrarse en `/register` con un email `@dreambake.test` y, si hace falta,
darle un rol desde MySQL (`UPDATE users SET roles='ADMIN' WHERE email='...';`). Al terminar:
`DELETE FROM users WHERE email LIKE '%@dreambake.test';`
