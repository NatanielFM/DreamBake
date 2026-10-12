# 7. Instalación y configuración

[← Volver al índice](../DOCUMENTACION-TECNICA.md)

Guía para instalar y levantar DreamBake en **Windows** (el entorno donde se desarrolló). Los comandos son de PowerShell.

## 7.1 Requisitos

| Programa | Versión | Cómo comprobarlo |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| MySQL Server | 8.0 (se probó con 8.0.46) | `mysql --version` |
| Maven | **No hace falta instalarlo**: se usa el wrapper incluido (`mvnw.cmd`, Maven 3.9.16) | — |
| Git | Cualquiera reciente | `git --version` |

MySQL tiene que estar corriendo en `localhost:3306`. Para los backups se usan `mysqldump.exe` y `mysql.exe`, que vienen con MySQL Server (por ejemplo en `C:\Program Files\MySQL\MySQL Server 8.0\bin\`).

## 7.2 Instalación paso a paso

1. **Obtener el código:**
   ```powershell
   git clone https://github.com/NatanielFM/DreamBake.git
   cd DreamBake
   ```
2. **Crear la base de datos** (MySQL Command Line Client o `mysql -u root -p`):
   ```sql
   CREATE DATABASE dreambake_db;
   ```
   Las tablas no se crean a mano: Hibernate las crea al arrancar la app por primera vez.
3. **Crear un usuario de MySQL propio (recomendado).** Hoy la app se usa con `root` (ver [limitaciones](06-limitaciones-y-mejoras.md#seguridad-y-datos)); lo correcto es un usuario solo para esta base:
   ```sql
   CREATE USER 'dreambake'@'localhost' IDENTIFIED BY 'una-contraseña-larga';
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, REFERENCES ON dreambake_db.* TO 'dreambake'@'localhost';
   ```
   Este paso **no se probó todavía** (es parte de la fase 7). Tener en cuenta: Hibernate necesita `ALTER` para agregar columnas cuando cambia una entidad, y la app crea los triggers de la auditoría al arrancar, que requieren el permiso `TRIGGER` (y, con el registro binario de MySQL activo, `SUPER`). La opción simple es arrancar la app una vez con un usuario con esos permisos y después dejarle al usuario de la app solo los de arriba.
4. **Crear el archivo de configuración local** a partir del ejemplo:
   ```powershell
   Copy-Item config\application.ejemplo.properties config\application.properties
   ```
   `config/application.properties` está en `.gitignore`: ahí pueden ir claves y contraseñas reales. Completar como mínimo la conexión, descomentando y llenando:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/dreambake_db?useSSL=false&serverTimezone=UTC
   spring.datasource.username=dreambake
   spring.datasource.password=
   ```
   **No cambiar `serverTimezone=UTC`**: la cadena de hashes de la auditoría incluye la hora. Lo de este archivo pisa a `src/main/resources/application.properties`.
5. **Levantar la aplicación** desde la carpeta del proyecto (la que contiene `config/`):
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```
   Abrir <http://localhost:8080>. Al arrancar, el log avisa qué funciones quedaron desactivadas por falta de su clave (`ConfigurationCheck`).
6. **Crear el primer administrador.** No hay usuarios precargados. Registrarse en <http://localhost:8080/register> y después, en MySQL:
   ```sql
   USE dreambake_db;
   UPDATE users SET roles='ADMIN' WHERE email='tu-email@ejemplo.com';
   ```
   Al volver a entrar, el administrador va a `/admin`. Los pasteleros se dan de alta desde **Panel → Equipo** (o con `roles='PASTELERO'`).
7. **Cargar los datos del negocio** en **Panel → Ajustes → Configuración del negocio**: nombre, WhatsApp, email, dirección, redes, horario, días de anticipación, seña sugerida. Ningún dato del negocio está escrito en el código.

## 7.3 Configuración opcional

Todas las claves van en `config/application.properties`; el archivo de ejemplo explica cada una. Las de correo, Google y Facebook vienen **comentadas**: si se activan con el valor vacío, la app no arranca.

| Función | Claves | De dónde sale el dato |
|---|---|---|
| **Backups** | `backup.directory` (carpeta, con `/`) y `backup.mysqldump-path` (ruta completa de `mysqldump.exe`) | Sin ellas los backups quedan desactivados y el Resumen lo avisa en rojo. Guía de restauración: [RESTAURAR-BACKUP.md](../RESTAURAR-BACKUP.md). |
| **Correo (Gmail)** | Lo más simple: **Panel → Ajustes → Correo de envío** (se guarda cifrado en la base). Alternativa: `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password` y las `spring.mail.properties.mail.smtp.*` | Cuenta de Gmail con verificación en dos pasos y una **contraseña de aplicación**. Host `smtp.gmail.com`, puerto `587`. |
| **Google** | `spring.security.oauth2.client.registration.google.client-id` y `…client-secret` | Google Cloud Console → Credenciales → ID de cliente OAuth (aplicación web). Redirección autorizada: `<app.public-url>/login/oauth2/code/google`. |
| **Facebook** | `spring.security.oauth2.client.registration.facebook.client-id` y `…client-secret` | Meta for Developers → la app → Configuración básica. Redirección: `<app.public-url>/login/oauth2/code/facebook`. |
| **Bakey (Gemini)** | `bakey.gemini.api-key` (o la variable de entorno `GEMINI_API_KEY`) y `bakey.gemini.model` | Google AI Studio → *Get API key*. Ver [BAKEY.md](../BAKEY.md). |
| **Dirección pública** | `app.public-url` (sin `/` final) | La URL con la que se entra al sitio; se usa en los links de los emails. |
| **Datos de respaldo para los emails** | `app.business.name`, `app.business.whatsapp`, `app.business.email` | Solo se usan si no están cargados en el panel. |

**Archivos que nunca se suben ni se comparten:** `config/application.properties` y `config/secret.key` (la clave que cifra la contraseña del correo; se crea sola y viaja dentro de cada backup).

## 7.4 Perfil de producción (`prod`)

`src/main/resources/application-prod.properties` se activa con:
```powershell
java -jar dreambake.jar --spring.profiles.active=prod
```
En ese perfil la base y la clave de la sesión se leen de variables de entorno (`DREAMBAKE_DB_URL`, `DREAMBAKE_DB_USER`, `DREAMBAKE_DB_PASSWORD`, `DREAMBAKE_REMEMBER_ME_KEY`), las cookies son `Secure` y `HttpOnly`, la sesión dura 60 minutos, los errores no muestran detalles y DevTools se apaga. **No se usó todavía en un despliegue real** (fase 7 pospuesta). Para generar el `.jar`: `.\mvnw.cmd clean package`.

## 7.5 Comandos útiles

| Para | Comando |
|---|---|
| Compilar sin levantar | `.\mvnw.cmd clean compile` (con `clean`: sin él, Maven puede decir que está todo bien sin recompilar) |
| Correr el test | `.\mvnw.cmd test` (necesita MySQL encendido: el test arranca la app completa) |
| Levantar en desarrollo | `.\mvnw.cmd spring-boot:run` |

Con DevTools, los cambios en plantillas, CSS y JS se ven recargando el navegador; los cambios en Java reinician la app al recompilar.

## 7.6 Problemas comunes

- **"Demasiadas solicitudes" (429) al probar:** se alcanzó un límite por IP (por ejemplo, 10 registros o 20 logins cada 10 minutos). Reiniciar la app los pone en cero.
- **`clean` falla:** Windows bloquea la carpeta `target/` mientras la app está corriendo. Detenerla antes.
- **No arranca después de descomentar una clave de correo, Google o Facebook:** quedó una línea sin `#` y sin valor. Completarla o volver a comentarla.
- **No llegan los emails:** no hay cuenta de correo configurada (el log lo avisa al arrancar).
- **Al reiniciar hay que volver a iniciar sesión:** falta `dreambake.remember-me-key` (en desarrollo es normal).
- **Pruebas con curl en Windows:** los acentos en el cuerpo JSON se rompen y el servidor responde 400; mandar el cuerpo desde un archivo UTF-8 (`--data-binary @archivo.json`).
- **Probar sin tocar los datos reales:** usar una base aparte (por ejemplo, con `SPRING_APPLICATION_JSON` cambiando `spring.datasource.url`) y datos de prueba con un prefijo único (`zz…@test.local`).
