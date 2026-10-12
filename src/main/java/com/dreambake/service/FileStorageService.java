package com.dreambake.service;

import com.dreambake.repository.CampaignRepository;
import com.dreambake.repository.GalleryImageRepository;
import com.dreambake.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

/**
 * Guarda y borra las imágenes subidas desde el panel (paso 6.7). TODA subida de archivos pasa por acá.
 *
 * Al subir:
 *  1. Máximo 5 MB.
 *  2. Se mira el CONTENIDO real (los primeros bytes, la "firma" de cada formato) y no la extensión ni lo que
 *     dice el navegador: un .html renombrado a .jpg se rechaza.
 *  3. JPG y PNG se abren con javax.imageio (si no se pueden leer, no son imágenes de verdad), se achican a
 *     1600 px de ancho como máximo y se vuelven a guardar comprimidos. Volver a guardarlas también borra los
 *     datos ocultos (EXIF con la ubicación GPS del celular) y cualquier cosa "pegada" al final del archivo.
 *  4. WebP: Java no trae lector de WebP, así que se valida la firma y el tamaño y se guarda tal cual
 *     (el panel igual convierte todas las fotos a JPG antes de subirlas: gallery.js).
 *  5. El nombre lo genera el servidor (UUID): nunca se usa el nombre original (evita "../" y nombres raros).
 *
 * Al borrar: solo archivos dentro de uploads/, y solo si nadie más los usa (galería, productos, campañas).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    public static final String UPLOAD_DIR = "uploads";
    public static final String URL_PREFIX = "/uploads/";
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_WIDTH = 1600;
    private static final long MAX_PIXELS = 50_000_000L;   // 50 megapíxeles: más es sospechoso (bomba de descompresión)
    private static final float JPEG_QUALITY = 0.85f;

    /** Formatos aceptados, reconocidos por su firma. */
    public enum Kind { JPG, PNG, WEBP }

    /** Archivo guardado: dirección pública (/uploads/…), formato, medidas finales y peso en bytes. */
    public record Stored(String url, Kind kind, int width, int height, long bytes) {}

    /** Error de validación con un mensaje para mostrarle a la persona. */
    public static class UploadException extends Exception {
        public UploadException(String message) { super(message); }
    }

    private final GalleryImageRepository galleryRepository;
    private final ProductRepository productRepository;
    private final CampaignRepository campaignRepository;

    public FileStorageService(GalleryImageRepository galleryRepository, ProductRepository productRepository,
                              CampaignRepository campaignRepository) {
        this.galleryRepository = galleryRepository;
        this.productRepository = productRepository;
        this.campaignRepository = campaignRepository;
    }

    /** Carpeta física de las imágenes (ruta completa). */
    public static Path uploadDir() {
        return Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
    }

    // ===================== SUBIR =====================

    public Stored storeImage(MultipartFile file) throws UploadException, IOException {
        if (file == null || file.isEmpty()) throw new UploadException("No llegó ninguna foto. Probá de nuevo.");
        if (file.getSize() > MAX_BYTES) throw new UploadException("La foto pesa más de 5 MB. Probá con una más liviana.");

        byte[] data;
        try (InputStream in = file.getInputStream()) {
            data = in.readNBytes((int) MAX_BYTES + 1);
        }
        if (data.length > MAX_BYTES) throw new UploadException("La foto pesa más de 5 MB. Probá con una más liviana.");

        Kind kind = detect(data);
        if (kind == null) throw new UploadException("Solo se pueden subir fotos JPG, PNG o WebP.");

        byte[] output;
        int width;
        int height;
        if (kind == Kind.WEBP) {
            output = data;            // sin lector de WebP en Java: se guarda tal cual (ya validado)
            width = 0;
            height = 0;
        } else {
            BufferedImage image = readSafely(data);
            BufferedImage resized = resizeToMaxWidth(image, MAX_WIDTH);
            width = resized.getWidth();
            height = resized.getHeight();
            output = kind == Kind.JPG ? writeJpeg(resized) : writePng(resized);
        }

        Path dir = uploadDir();
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID() + "." + extension(kind);
        // Se escribe primero en un temporal y después se mueve: nunca queda un archivo a medio escribir publicado
        Path tmp = Files.createTempFile(dir, "subida-", ".tmp");
        try {
            Files.write(tmp, output);
            Files.move(tmp, dir.resolve(fileName), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        log.info("Imagen guardada: {} ({}, {}x{}, {} KB; original {} KB)", fileName, kind, width, height,
                output.length / 1024, data.length / 1024);
        return new Stored(URL_PREFIX + fileName, kind, width, height, output.length);
    }

    /** Reconoce el formato por los primeros bytes del archivo. null si no es JPG, PNG ni WebP. */
    static Kind detect(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return Kind.JPG;
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (d.length >= 8 && Arrays.equals(Arrays.copyOf(d, 8), png)) return Kind.PNG;
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return Kind.WEBP;
        return null;
    }

    // Lee la imagen mirando ANTES sus medidas: una imagen de 50.000 x 50.000 píxeles pesa poco comprimida
    // pero ocuparía gigas de memoria al abrirla (y podría tirar el servidor).
    private static BufferedImage readSafely(byte[] data) throws UploadException, IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new UploadException("El archivo no es una imagen válida.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w * h > MAX_PIXELS) {
                    throw new UploadException("La foto es demasiado grande (más de 50 megapíxeles).");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new UploadException("El archivo no es una imagen válida.");
                return image;
            } catch (IOException | RuntimeException e) {
                throw new UploadException("El archivo está dañado o no es una imagen válida.");
            } finally {
                reader.dispose();
            }
        }
    }

    /** Achica a "max" píxeles de ancho manteniendo la proporción (si ya es más chica, no la toca). */
    static BufferedImage resizeToMaxWidth(BufferedImage src, int max) {
        if (src.getWidth() <= max) return src;
        int w = max;
        int h = Math.max(1, Math.round((float) src.getHeight() * max / src.getWidth()));
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage current = src;
        // Se achica de a mitades hasta acercarse: con un solo salto grande la foto queda "pixelada"
        while (current.getWidth() / 2 >= w) {
            current = scale(current, current.getWidth() / 2, Math.max(1, current.getHeight() / 2), alpha);
        }
        return scale(current, w, h, alpha);
    }

    private static BufferedImage scale(BufferedImage src, int w, int h, boolean alpha) {
        BufferedImage out = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // JPG con calidad 85 %: casi no se nota la diferencia y pesa mucho menos. Sin transparencia (fondo blanco).
    private static byte[] writeJpeg(BufferedImage img) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        var bytes = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    // PNG (conserva la transparencia, por ejemplo un logo)
    private static byte[] writePng(BufferedImage img) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        return bytes.toByteArray();
    }

    private static String extension(Kind kind) {
        return switch (kind) {
            case JPG -> "jpg";
            case PNG -> "png";
            case WEBP -> "webp";
        };
    }

    // ===================== BORRAR =====================

    /**
     * Ruta del archivo de una dirección /uploads/…, solo si queda DENTRO de uploads/ (normalize() resuelve
     * los "../"). null si la dirección no es de uploads o intenta salir de la carpeta.
     */
    public static Path resolve(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return null;
        String name = URLDecoder.decode(url.substring(URL_PREFIX.length()), StandardCharsets.UTF_8);
        if (name.isBlank() || name.contains("/") || name.contains("\\")) return null;
        Path dir = uploadDir();
        Path file = dir.resolve(name).normalize();
        return file.startsWith(dir) && !file.equals(dir) ? file : null;
    }

    /** ¿Alguien usa todavía esta imagen? (una foto de la galería, un producto o una campaña) */
    public boolean isInUse(String url) {
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        return galleryRepository.findAll().stream().anyMatch(g -> same(g.getUrl(), url, decoded))
                || productRepository.findAll().stream().anyMatch(p -> same(p.getImageUrl(), url, decoded))
                || campaignRepository.findAll().stream().anyMatch(c -> same(c.getImageUrl(), url, decoded));
    }

    /**
     * Borra el archivo del disco si ya nadie lo usa. Llamar DESPUÉS de borrar la fila (foto o producto).
     * @return true si se borró el archivo
     */
    public boolean deleteIfUnused(String url) {
        Path file = resolve(url);
        if (file == null) return false;   // no es de uploads/ (ej: una imagen fija de /img): nunca se borra
        if (isInUse(url)) return false;
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) log.info("Archivo borrado: {}", file.getFileName());
            return deleted;
        } catch (IOException e) {
            // Si no se pudo borrar, la imagen igual deja de aparecer en el sitio
            log.warn("No se pudo borrar {}: {}", file.getFileName(), e.getMessage());
            return false;
        }
    }

    // Compara direcciones aunque una tenga espacios como "%20" y la otra no
    private static boolean same(String candidate, String url, String decoded) {
        if (candidate == null) return false;
        return candidate.equals(url) || URLDecoder.decode(candidate, StandardCharsets.UTF_8).equals(decoded);
    }
}
