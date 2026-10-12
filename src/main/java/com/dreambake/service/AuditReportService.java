package com.dreambake.service;

import com.dreambake.audit.AuditText;
import com.dreambake.entity.AuditAction;
import com.dreambake.entity.AuditLog;
import com.dreambake.entity.AuditModule;
import com.dreambake.repository.AuditLogRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Consultas y descargas del "Registro de actividad" (paso 3.4).
 *
 * Todo es de SOLO LECTURA: este servicio no tiene (ni puede tener) forma de modificar o borrar
 * registros. Los filtros y la paginación se resuelven en la base de datos (AuditLogRepository.search),
 * así el navegador nunca recibe más de una página.
 */
@Service
public class AuditReportService {

    public static final int PAGE_SIZE = 50;
    // Máximo de filas por descarga: si hay más, se avisa en el archivo y se pide filtrar
    public static final int MAX_CSV_ROWS = 50_000;
    public static final int MAX_PDF_ROWS = 3_000;

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuditLogRepository repository;

    public AuditReportService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Filtros de la pantalla. actor puede ser un email, "rol:PUBLICO" (Cliente web, sin sesión) o
     * "rol:SISTEMA" (tareas automáticas). Todos opcionales.
     */
    public record Filter(LocalDate from, LocalDate to, String actor, AuditModule module, AuditAction action, String text) {

        /** "Desde 01/10/2026 hasta 10/10/2026", "Hasta el 10/10/2026" o "Todas las fechas" */
        public String rangeText() {
            if (from == null && to == null) return "Todas las fechas";
            if (from == null) return "Hasta el " + to.format(DAY);
            if (to == null) return "Desde el " + from.format(DAY);
            return "Desde el " + from.format(DAY) + " hasta el " + to.format(DAY);
        }

        /** Los otros filtros en palabras, para dejar constancia en la descarga y en la auditoría. */
        public String otherFiltersText() {
            List<String> parts = new ArrayList<>();
            if (actor != null && !actor.isBlank()) parts.add("usuario: " + (actor.startsWith("rol:") ? AuditText.roleLabel(actor.substring(4)) : actor));
            if (module != null) parts.add("módulo: " + AuditText.moduleLabel(module));
            if (action != null) parts.add("acción: " + AuditText.actionLabel(action));
            if (text != null && !text.isBlank()) parts.add("texto: \"" + text.trim() + "\"");
            return parts.isEmpty() ? "sin otros filtros" : String.join(", ", parts);
        }
    }

    /** Una fila tal como la ve la pantalla (con los nombres ya en español). */
    public record Row(Long id, String timestamp, String actorName, String actorEmail, String actorRole, String roleLabel,
                      String module, String moduleLabel, String action, String actionLabel,
                      String entityType, String entityId, String description, String details,
                      String ipAddress, String userAgent, String hash) {}

    public record Result(List<Row> items, int page, int totalPages, long totalElements, int pageSize) {}

    public record Actor(String value, String label) {}

    public Result search(Filter f, int page) {
        Page<AuditLog> result = query(f, Math.max(page, 0), PAGE_SIZE);
        return new Result(result.getContent().stream().map(this::toRow).toList(),
                result.getNumber(), result.getTotalPages(), result.getTotalElements(), PAGE_SIZE);
    }

    /** Opciones del filtro "Usuario": las personas que aparecen + "Cliente web" y "Sistema". */
    public List<Actor> actors() {
        List<Actor> list = new ArrayList<>();
        for (Object[] row : repository.findActors()) {
            String email = (String) row[0];
            String name = (String) row[1];
            list.add(new Actor(email, (name == null || name.equals(email) ? email : name + " (" + email + ")")));
        }
        list.add(new Actor("rol:PUBLICO", "Cliente web / sin sesión"));
        list.add(new Actor("rol:SISTEMA", "Sistema (tareas automáticas)"));
        return list;
    }

    private Page<AuditLog> query(Filter f, int page, int size) {
        String actorEmail = null, role = null;
        if (f.actor() != null && !f.actor().isBlank()) {
            if (f.actor().startsWith("rol:")) role = f.actor().substring(4);
            else actorEmail = f.actor().trim();
        }
        String text = f.text() == null || f.text().isBlank() ? null : "%" + f.text().trim().toLowerCase(Locale.ROOT) + "%";
        return repository.search(
                f.from() == null ? null : f.from().atStartOfDay(),
                f.to() == null ? null : f.to().plusDays(1).atStartOfDay(), // "hasta" incluye todo ese día
                actorEmail, role, f.module(), f.action(), text,
                PageRequest.of(page, size));
    }

    private Row toRow(AuditLog a) {
        return new Row(a.getId(), a.getTimestamp() == null ? null : a.getTimestamp().toString(),
                a.getActorName(), a.getActorEmail(), a.getActorRole(), AuditText.roleLabel(a.getActorRole()),
                a.getModule() == null ? null : a.getModule().name(), AuditText.moduleLabel(a.getModule()),
                a.getAction() == null ? null : a.getAction().name(), AuditText.actionLabel(a.getAction()),
                a.getEntityType(), a.getEntityId(), a.getDescription(), a.getDetails(),
                a.getIpAddress(), a.getUserAgent(), a.getHash());
    }

    // ===================== CSV =====================

    /** Datos de una descarga: el archivo y cuántas filas entraron (para la auditoría). */
    public record Export(byte[] bytes, int rows, long total, String lastHash, Long lastId) {}

    /**
     * CSV para Excel en español: UTF-8 con BOM (así Excel reconoce los acentos), separador punto y
     * coma (Excel en español usa la coma para los decimales) y fin de línea de Windows.
     */
    public Export csv(Filter f) {
        Page<AuditLog> page = query(f, 0, MAX_CSV_ROWS);
        StringBuilder out = new StringBuilder("﻿");
        line(out, "N°", "Fecha y hora", "Usuario", "Email", "Rol", "Módulo", "Acción", "Sobre", "Id",
                "Descripción", "Detalles (antes y después)", "IP", "Hash");
        for (AuditLog a : page.getContent()) {
            line(out, String.valueOf(a.getId()), a.getTimestamp() == null ? "" : a.getTimestamp().format(WHEN),
                    a.getActorName(), a.getActorEmail(), AuditText.roleLabel(a.getActorRole()),
                    AuditText.moduleLabel(a.getModule()), AuditText.actionLabel(a.getAction()),
                    a.getEntityType(), a.getEntityId(), a.getDescription(), a.getDetails(), a.getIpAddress(), a.getHash());
        }
        if (page.getTotalElements() > page.getNumberOfElements()) {
            line(out, "", "", "", "", "", "", "", "", "", "Hay " + page.getTotalElements() + " registros con estos filtros: "
                    + "se incluyeron los " + page.getNumberOfElements() + " más recientes. Filtrá por fechas para descargar el resto.");
        }
        AuditLog newest = page.getContent().isEmpty() ? null : page.getContent().get(0);
        return new Export(out.toString().getBytes(StandardCharsets.UTF_8), page.getNumberOfElements(),
                page.getTotalElements(), newest == null ? null : newest.getHash(), newest == null ? null : newest.getId());
    }

    private static void line(StringBuilder out, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(';');
            out.append(csvCell(values[i]));
        }
        out.append("\r\n");
    }

    // Una celda: entre comillas (y las comillas de adentro, dobles). Si empieza con = + - @ se le
    // antepone un apóstrofo: Excel ejecutaría eso como fórmula ("inyección de CSV").
    private static String csvCell(String value) {
        if (value == null) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    // ===================== PDF =====================

    /**
     * PDF con OpenPDF: logo, título, rango de fechas, quién lo generó y cuándo, la tabla y, al pie de
     * cada página, el hash del registro más reciente incluido como "sello de integridad". Ese hash
     * depende de ese registro y de TODOS los anteriores (cadena): si alguien comparara el PDF con la
     * base y el hash no coincide, el registro se modificó después de imprimir.
     */
    public Export pdf(Filter f, String generatedBy) {
        Page<AuditLog> page = query(f, 0, MAX_PDF_ROWS);
        List<AuditLog> rows = page.getContent();
        AuditLog newest = rows.isEmpty() ? null : rows.get(0);
        String seal = newest == null ? "sin registros" : "registro N° " + newest.getId() + " — hash " + newest.getHash();
        String generatedAt = LocalDateTime.now(ZONE).format(WHEN);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 44);
        PdfWriter writer = PdfWriter.getInstance(doc, bytes);
        writer.setPageEvent(new FooterSeal(pdfSafe("Sello de integridad: " + seal)));
        doc.addTitle("Registro de actividad — Dulce Sueños Posadas");
        doc.addCreator("DreamBake");
        doc.open();

        Color ink = new Color(0x48, 0x17, 0x29);       // --ink
        Color rose = new Color(0xbe, 0x3d, 0x6c);      // rosa de los emails
        Color soft = new Color(0xfb, 0xe9, 0xf0);      // rosa muy claro
        Font title = new Font(Font.HELVETICA, 18, Font.BOLD, ink);
        Font normal = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
        Font small = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.BLACK);
        Font head = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

        // Encabezado: logo a la izquierda, título y datos a la derecha
        PdfPTable header = new PdfPTable(new float[] { 1f, 9f });
        header.setWidthPercentage(100);
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        try (InputStream in = new ClassPathResource("static/img/logo-email.png").getInputStream()) {
            Image logo = Image.getInstance(in.readAllBytes());
            logo.scaleToFit(52, 56);
            logoCell.addElement(logo);
        } catch (Exception e) {
            // sin logo, el PDF sale igual
        }
        header.addCell(logoCell);
        PdfPCell info = new PdfPCell();
        info.setBorder(Rectangle.NO_BORDER);
        info.addElement(new Paragraph(pdfSafe("Registro de actividad"), title));
        info.addElement(new Paragraph(pdfSafe("Dulce Sueños Posadas · " + f.rangeText() + " · " + f.otherFiltersText()), normal));
        info.addElement(new Paragraph(pdfSafe("Generado por " + generatedBy + " el " + generatedAt + " · "
                + rows.size() + (rows.size() == 1 ? " registro" : " registros")
                + (page.getTotalElements() > rows.size() ? " (los más recientes de " + page.getTotalElements() + ": filtrá por fechas para ver el resto)" : "")), normal));
        header.addCell(info);
        doc.add(header);
        doc.add(new Paragraph(" ", small));

        // Tabla
        PdfPTable table = new PdfPTable(new float[] { 0.6f, 1.5f, 2.2f, 1.2f, 1.3f, 1.4f, 6f });
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // el encabezado se repite en cada página
        for (String h : new String[] { "N°", "Fecha y hora", "Usuario", "Rol", "Módulo", "Acción", "Descripción" }) {
            PdfPCell c = new PdfPCell(new Phrase(pdfSafe(h), head));
            c.setBackgroundColor(rose);
            c.setPadding(4);
            c.setBorderColor(rose);
            table.addCell(c);
        }
        boolean stripe = false;
        for (AuditLog a : rows) {
            String[] values = {
                    String.valueOf(a.getId()),
                    a.getTimestamp() == null ? "" : a.getTimestamp().format(WHEN),
                    a.getActorName() + (a.getActorEmail() != null && !a.getActorEmail().equals(a.getActorName()) ? "\n" + a.getActorEmail() : ""),
                    AuditText.roleLabel(a.getActorRole()),
                    AuditText.moduleLabel(a.getModule()),
                    AuditText.actionLabel(a.getAction()),
                    a.getDescription() };
            for (String v : values) {
                PdfPCell c = new PdfPCell(new Phrase(pdfSafe(v), small));
                c.setPadding(3.5f);
                c.setBorderColor(new Color(0xe8, 0xd6, 0xdd));
                if (stripe) c.setBackgroundColor(soft);
                table.addCell(c);
            }
            stripe = !stripe;
        }
        if (rows.isEmpty()) {
            PdfPCell c = new PdfPCell(new Phrase("No hay registros con estos filtros.", normal));
            c.setColspan(7);
            c.setPadding(8);
            table.addCell(c);
        }
        doc.add(table);
        doc.close();
        return new Export(bytes.toByteArray(), rows.size(), page.getTotalElements(),
                newest == null ? null : newest.getHash(), newest == null ? null : newest.getId());
    }

    // Pie de cada página: el sello de integridad y el número de página
    private static class FooterSeal extends PdfPageEventHelper {
        private final String seal;
        private final Font font = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);

        FooterSeal(String seal) {
            this.seal = seal;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase(seal, font), document.left(), page.getBottom() + 22, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase(pdfSafe("Página " + writer.getPageNumber()), font), document.right(), page.getBottom() + 22, 0);
        }
    }

    // La letra estándar del PDF (Helvetica) solo trae los caracteres de Windows-1252 (español sí;
    // flechas no). Lo que no entra se reemplaza para que no salga un cuadradito vacío.
    private static final Charset CP1252 = Charset.forName("windows-1252");

    static String pdfSafe(String text) {
        if (text == null) return "";
        String t = text.replace("→", "->");
        CharsetEncoder encoder = CP1252.newEncoder();
        StringBuilder sb = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            sb.append(encoder.canEncode(ch) ? ch : "?");
        });
        return sb.toString();
    }
}
