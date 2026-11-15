package edu.unbosque.marketplace.administracion;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.unbosque.marketplace.administracion.AdminDtos.ReporteSolicitud;
import edu.unbosque.marketplace.administracion.AdminDtos.Tabla;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.logs.EventLogger;
import edu.unbosque.marketplace.seguridad.Sesion;
import edu.unbosque.marketplace.seguridad.Sesion.Rol;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** HU-021 — Exporta reportes de gestión a PDF (OpenPDF) o Excel (Apache POI) con los datos vigentes (RN-21). */
@Service
public class ReporteService {

    /** Archivo generado: contenido, tipo MIME y nombre sugerido. */
    public record Archivo(byte[] contenido, String tipoMime, String nombre) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Archivo a && java.util.Arrays.equals(contenido, a.contenido)
                    && java.util.Objects.equals(tipoMime, a.tipoMime) && java.util.Objects.equals(nombre, a.nombre);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Objects.hash(tipoMime, nombre) + java.util.Arrays.hashCode(contenido);
        }

        @Override
        public String toString() {
            return "Archivo[" + nombre + ", " + tipoMime + ", " + (contenido == null ? 0 : contenido.length) + " bytes]";
        }
    }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AdminRepositorio repo;
    private final EventLogger log;
    private final Clock reloj;

    public ReporteService(AdminRepositorio repo, EventLogger log, Clock reloj) {
        this.repo = repo;
        this.log = log;
        this.reloj = reloj;
    }

    public Archivo exportar(Sesion sesion, ReporteSolicitud s) {
        sesion.exigirRol(Rol.ADMINISTRADOR);                                         // CA-05
        String tipo = s == null ? null : ReglasAdministracion.mayusculas(s.tipo());
        String formato = s == null ? null : ReglasAdministracion.mayusculas(s.formato());
        List<String> errores = ReglasAdministracion.erroresReporte(tipo, formato);   // CA-01, CA-02
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("REPORTE_INVALIDO", "Revise el reporte solicitado.",
                    errores.toArray(String[]::new));
        }
        Tabla t = repo.tabla(tipo);                                                  // CA-03: datos vigentes
        String generado = "Generado el " + LocalDateTime.now(reloj).format(FECHA);
        String base = "reporte-" + tipo.toLowerCase(Locale.ROOT) + "-" + LocalDateTime.now(reloj).toLocalDate();
        Archivo a = "PDF".equals(formato)
                ? new Archivo(pdf(t, generado), "application/pdf", base + ".pdf")
                : new Archivo(excel(t, generado),
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", base + ".xlsx");
        repo.registrarReporte(sesion.id(), tipo, formato);                            // CA-04
        log.auditado("REPORTE_EXPORTADO", sesion.id().toString(), "reporte", tipo, "EXITO",
                Map.of("formato", formato, "filas", t.filas().size()));
        return a;
    }

    /** CA-06: sin datos, el archivo se genera con el mensaje correspondiente. */
    static byte[] pdf(Tabla t, String generado) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(t.columnas().size() > 5 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(31, 56, 100));
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font encabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            doc.add(new Paragraph("Marketplace de Alojamientos – Inversiones LR", normal));
            doc.add(new Paragraph(t.titulo(), titulo));
            doc.add(new Paragraph(generado + " · " + t.filas().size() + " registros", normal));
            doc.add(new Paragraph(" "));
            if (t.filas().isEmpty()) {
                doc.add(new Paragraph("No hay datos para este reporte.", normal));
            } else {
                PdfPTable tabla = new PdfPTable(t.columnas().size());
                tabla.setWidthPercentage(100);
                tabla.setHeaderRows(1);
                for (String c : t.columnas()) {
                    PdfPCell celda = new PdfPCell(new Phrase(c, encabezado));
                    celda.setBackgroundColor(new Color(31, 56, 100));
                    celda.setHorizontalAlignment(Element.ALIGN_LEFT);
                    tabla.addCell(celda);
                }
                for (List<String> fila : t.filas()) {
                    for (String v : fila) {
                        tabla.addCell(new Phrase(v, normal));
                    }
                }
                doc.add(tabla);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    static byte[] excel(Tabla t, String generado) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Reporte");
            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle estiloEncabezado = libro.createCellStyle();
            estiloEncabezado.setFont(negrita);
            hoja.createRow(0).createCell(0).setCellValue(t.titulo());
            hoja.createRow(1).createCell(0).setCellValue(generado + " · " + t.filas().size() + " registros");
            Row enc = hoja.createRow(3);
            for (int i = 0; i < t.columnas().size(); i++) {
                enc.createCell(i).setCellValue(t.columnas().get(i));
                enc.getCell(i).setCellStyle(estiloEncabezado);
            }
            if (t.filas().isEmpty()) {
                hoja.createRow(4).createCell(0).setCellValue("No hay datos para este reporte.");
            }
            for (int f = 0; f < t.filas().size(); f++) {
                Row fila = hoja.createRow(4 + f);
                List<String> valores = t.filas().get(f);
                for (int i = 0; i < valores.size(); i++) {
                    String v = valores.get(i);
                    if (v.matches("-?\\d+(\\.\\d+)?")) {
                        fila.createCell(i).setCellValue(Double.parseDouble(v));      // números como números
                    } else {
                        fila.createCell(i).setCellValue(v);
                    }
                }
            }
            for (int i = 0; i < t.columnas().size(); i++) {
                hoja.setColumnWidth(i, 22 * 256);
            }
            libro.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel", e);
        }
    }
}
