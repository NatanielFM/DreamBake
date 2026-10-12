package com.dreambake.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra y descifra datos secretos que se guardan en la base (por ejemplo, la contraseña
 * de aplicación del correo). Así, si alguien obtiene una copia de la base (un backup),
 * no puede leer esas contraseñas.
 *
 * Usa AES-GCM, un cifrado estándar que además detecta si el dato fue alterado.
 * La clave (32 bytes al azar) NO se guarda en la base:
 *  - se toma de la propiedad dreambake.secret-key (en Base64), o
 *  - si no está, del archivo config/secret.key; si ese archivo no existe, se crea solo
 *    la primera vez (está en .gitignore: no se sube al repositorio).
 * Si se pierde la clave, hay que volver a cargar la contraseña del correo en el panel.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String PREFIX = "v1:";         // versión del formato, por si algún día cambia
    private static final int IV_BYTES = 12;              // número al azar distinto en cada cifrado
    private static final int TAG_BITS = 128;
    private static final Path KEY_FILE = Paths.get("config", "secret.key");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public SecretCipher(@Value("${dreambake.secret-key:}") String configuredKey) {
        this.key = new SecretKeySpec(loadKey(configuredKey), "AES");
    }

    private static byte[] loadKey(String configuredKey) {
        try {
            if (configuredKey != null && !configuredKey.isBlank()) {
                byte[] k = Base64.getDecoder().decode(configuredKey.trim());
                if (k.length == 32) return k;
                log.warn("dreambake.secret-key no tiene 32 bytes en Base64; se usa config/secret.key.");
            }
            if (Files.exists(KEY_FILE)) {
                byte[] k = Base64.getDecoder().decode(Files.readString(KEY_FILE).trim());
                if (k.length == 32) return k;
                throw new IllegalStateException("config/secret.key está dañado (no tiene 32 bytes).");
            }
            byte[] k = new byte[32];
            RANDOM.nextBytes(k);
            Files.createDirectories(KEY_FILE.getParent());
            Files.writeString(KEY_FILE, Base64.getEncoder().encodeToString(k));
            log.info("Se creó la clave de cifrado config/secret.key (guardala junto con los backups de la base).");
            return k;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar la clave de cifrado: " + e.getMessage(), e);
        }
    }

    // Texto → "v1:" + Base64(número al azar + texto cifrado)
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el dato.", e);
        }
    }

    // Lo inverso. Tira IllegalStateException si el dato no se puede leer (por ejemplo, cambió la clave).
    public String decrypt(String stored) {
        try {
            if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalArgumentException("formato desconocido");
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el dato guardado.", e);
        }
    }
}
