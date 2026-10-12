# Cómo restaurar una copia de seguridad

Esta guía explica cómo volver los datos de DreamBake (pedidos, clientes, stock, recetas, productos…)
a cómo estaban en una fecha anterior, usando una copia de seguridad (backup).

> **Antes de empezar:** restaurar **reemplaza** los datos actuales. Todo lo que se cargó después de
> la fecha de la copia se pierde. Hacelo solo si de verdad hace falta (por ejemplo, se borró algo
> importante por error o se rompió la computadora).

## ¿Qué es una copia de seguridad?

Todos los días a las 3:00 el sistema guarda una copia de todos los datos en esta computadora. Cada copia
es un archivo con un nombre como `dreambake_2026-10-11_0300.zip` (año-mes-día_horaminutos). Adentro tiene:

- `basedatos.sql.gz`: todos los datos del sistema.
- `uploads/`: las fotos de la galería y de los productos.
- `config/secret.key`: la clave que protege la contraseña del correo.

Al lado de cada copia hay un archivo `.sha256`, su "huella". Sirve para comprobar que la copia no se
dañó ni la modificó nadie.

Las copias se guardan en estas carpetas (dentro de la carpeta de backups):

| Carpeta | Qué guarda | Cuántas |
|---|---|---|
| `diarios` | la copia de cada día | las últimas 7 |
| `semanales` | una por semana | las últimas 4 |
| `mensuales` | una por mes | las últimas 12 |
| `manuales` | las que se hacen con "Hacer backup ahora" | las últimas 10 |
| `antes-de-restaurar` | cómo estaba todo justo antes de cada restauración | las últimas 5 |

---

## A) Restaurar desde el panel (la forma normal)

Sirve cuando el sistema funciona pero hay que volver los datos atrás.

1. Entrá al panel de administración y abrí la pestaña **Backups** (menú de la izquierda).
2. En la lista, buscá la copia de la fecha a la que querés volver y tocá **Restaurar**.
3. Leé con atención la ventana. Dice a qué fecha vuelven los datos y qué se pierde.
4. Tildá **"Entiendo que se reemplazan los datos actuales"**.
5. Escribí la palabra **RESTAURAR** (en mayúsculas).
6. Escribí **tu contraseña** (la misma con la que entrás al panel).
7. Tocá **Restaurar** y esperá unos segundos **sin cerrar la ventana**.
8. Cuando termina, el sistema te pide que vuelvas a ingresar. Entrá y revisá que esté todo bien.

**Qué hace el sistema para cuidarte:**

- Antes de tocar nada, comprueba la huella de la copia. Si el archivo está dañado, no restaura.
- Prueba la copia en una base aparte. Si algo falla en la prueba, no restaura.
- Guarda una copia de cómo estaba todo justo antes, en la carpeta **"Antes de restaurar"**.
- Mientras restaura, la tienda y el panel muestran "En mantenimiento", para que nadie cargue un pedido justo en ese momento.
- Si escribís mal la contraseña 5 veces, se bloquea 15 minutos.

**Qué NO se restaura** (queda como estaba):

- El **registro de actividad**. Está protegido para que nadie pueda borrarlo, así que se conserva entero y además anota la restauración.
- La **lista de copias de seguridad**.
- Las **fotos** que ya estaban: solo se agregan las que faltaban. Ninguna se borra.

**Si aparece un aviso:**

| Aviso | Qué significa | Qué hacer |
|---|---|---|
| "El archivo de la copia está dañado o fue modificado" | La huella no coincide. | Elegí otra copia. |
| "Esta copia es de una versión anterior del sistema" | La copia se hizo antes de una actualización del sistema y le faltan datos nuevos. | Seguí la parte **C** de esta guía, o pedí ayuda. |
| "La contraseña no es correcta" | — | Revisá mayúsculas y minúsculas. |
| "Hay una copia de seguridad o una restauración en curso" | — | Esperá unos minutos y probá de nuevo. |
| "Volvé a escribir la contraseña del correo" | La copia es de otra instalación. | Andá a **Ajustes → Correo de envío** y cargá la contraseña de nuevo. |

## B) Deshacer una restauración

Si restauraste y te arrepentiste:

1. Pestaña **Backups**.
2. Buscá la copia de tipo **"Antes de restaurar"** con la fecha y hora de la restauración.
3. Tocá **Restaurar** y seguí los mismos pasos que en la parte A.

Así los datos vuelven a como estaban justo antes de restaurar.

---

## C) Si la computadora se rompió (restauración a mano)

Esta parte es para cuando el sistema no funciona o hay que instalarlo en otra computadora. Conviene
que lo haga alguien con un poco de práctica, siguiendo los pasos en orden.

**Lo que necesitás:**

- La copia de seguridad: el `.zip` y su `.sha256`. Sacala de la carpeta de backups de la computadora vieja, de un pendrive o de donde la hayas guardado.
- MySQL 8 instalado. Se descarga desde mysql.com: "MySQL Installer for Windows".
- El sistema DreamBake instalado. Ver el README del proyecto.

**Pasos:**

1. **Comprobá que la copia esté sana.** En la carpeta donde está el zip, tocá con el botón derecho un lugar vacío y elegí **"Abrir en Terminal"**. Escribí (cambiando el nombre del archivo):
   ```
   certutil -hashfile dreambake_2026-10-11_0300.zip SHA256
   ```
   El código que aparece tiene que ser **igual** al que está dentro del archivo `.sha256` (abrilo con el Bloc de notas). Si no es igual, la copia está dañada: usá otra.

2. **Descomprimí el zip.** Botón derecho → **"Extraer todo…"**. Vas a ver `basedatos.sql.gz`, la carpeta `uploads` y la carpeta `config`.

3. **Descomprimí `basedatos.sql.gz`.** Necesitás un programa como **7-Zip** (gratis, 7-zip.org): botón derecho → 7-Zip → **"Extraer aquí"**. Queda el archivo `basedatos.sql`.

4. **Creá la base vacía.** Abrí **"MySQL 8.0 Command Line Client"** (está en el menú Inicio), escribí la contraseña de root y después:
   ```
   CREATE DATABASE dreambake_db CHARACTER SET utf8mb4;
   exit
   ```

5. **Cargá los datos.** En la terminal, dentro de la carpeta donde está `basedatos.sql`, escribí:
   ```
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p dreambake_db -e "source basedatos.sql"
   ```
   Te pide la contraseña de root. Si no aparece ningún error, los datos ya están cargados.

6. **Copiá las fotos y la clave.**
   - La carpeta `uploads` del backup va dentro de la carpeta del sistema DreamBake (reemplazando la vacía).
   - El archivo `config\secret.key` del backup va dentro de la carpeta `config` del sistema.

7. **Arrancá el sistema** y entrá al panel con tu usuario de siempre. Revisá pedidos, clientes y stock.

> Si algo de esto no funciona, no sigas probando cosas al azar: guardá el zip en un lugar seguro y pedí ayuda.
> Mientras tengas el archivo de la copia, los datos no se pierden.
