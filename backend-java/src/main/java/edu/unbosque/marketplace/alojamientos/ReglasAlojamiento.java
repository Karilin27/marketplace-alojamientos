package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.ServicioAdicionalSolicitud;
import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.TarifasSolicitud;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reglas puras de S2 (sin base de datos), para poder probarlas con JUnit.
 * RN-02 (solo el anfitrión dueño), RN-15 (estados de publicación), RN-16 (información mínima y valores válidos),
 * RN-17 (tarifas y descuentos no negativos).
 */
public final class ReglasAlojamiento {

    public static final String BORRADOR = "BORRADOR";
    public static final String PENDIENTE_REVISION = "PENDIENTE_REVISION";
    public static final String ACTIVA = "ACTIVA";
    public static final String PAUSADA = "PAUSADA";
    public static final String BLOQUEADA = "BLOQUEADA";

    public static final Set<String> TIPOS = Set.of("APARTAMENTO", "CASA", "HABITACION", "CABANA", "OTRO");
    public static final int MAX_IMAGENES = 10;
    public static final int MAX_TITULO = 120;

    private ReglasAlojamiento() {
    }

    /** Datos ya normalizados (sin espacios sobrantes, tipo en mayúsculas, listas sin repetidos). */
    public record Datos(String titulo, String descripcion, String tipo, String ciudad, String direccion,
                        Integer capacidad, BigDecimal precio, List<String> servicios, String reglas,
                        List<String> imagenes, UUID idPolitica) {
    }

    public record Tarifas(BigDecimal limpieza, BigDecimal descuentoPct, int descuentoMinNoches,
                          List<Adicional> adicionales) {
    }

    public record Adicional(String nombre, BigDecimal costo, boolean activo) {
    }

    // ------------------------------------------------------------------------- HU-005 / HU-006

    public static Datos normalizar(AlojamientoSolicitud s) {
        Objects.requireNonNull(s, "solicitud");
        return new Datos(texto(s.titulo()), texto(s.descripcion()),
                sinTildes(texto(s.tipo())),
                texto(s.ciudad()), texto(s.direccion()), s.capacidad(), s.precioPorNoche(),
                sinRepetidos(s.servicios()), texto(s.reglas()), sinRepetidos(s.imagenes()), s.idPoliticaCancelacion());
    }

    /**
     * HU-005 CA-01, CA-02 y CA-03: datos obligatorios para guardar la publicación en BORRADOR.
     * Las imágenes y la política pueden completarse después, pero se exigen para activar (RN-16, HU-023 CA-03).
     */
    public static List<String> errores(Datos d) {
        List<String> e = new ArrayList<>();
        if (d.titulo() == null) {
            e.add("titulo: es obligatorio");
        } else if (d.titulo().length() > MAX_TITULO) {
            e.add("titulo: máximo " + MAX_TITULO + " caracteres");
        }
        if (d.descripcion() == null) e.add("descripcion: es obligatoria");
        if (d.ciudad() == null) e.add("ciudad: es obligatoria");
        if (d.direccion() == null) e.add("direccion: es obligatoria");
        if (d.tipo() == null) {
            e.add("tipo: es obligatorio");
        } else if (!TIPOS.contains(d.tipo())) {
            e.add("tipo: debe ser uno de " + String.join(", ", TIPOS.stream().sorted().toList()));
        }
        if (d.capacidad() == null || d.capacidad() <= 0) {
            e.add("capacidad: debe ser mayor que cero");
        }
        if (d.precio() == null || d.precio().signum() <= 0) {
            e.add("precioPorNoche: debe ser mayor que cero");
        }
        if (d.imagenes().size() > MAX_IMAGENES) {
            e.add("imagenes: máximo " + MAX_IMAGENES);
        }
        for (String url : d.imagenes()) {
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                e.add("imagenes: «" + url + "» no es una URL válida");
            }
        }
        return e;
    }

    /**
     * RN-16 y HU-007 CA-02 / HU-023 CA-03: datos faltantes para poder ACTIVAR la publicación.
     * Lista vacía = puede activarse.
     */
    public static List<String> faltantesParaActivar(Datos d) {
        List<String> f = new ArrayList<>();
        if (d.titulo() == null) f.add("titulo");
        if (d.descripcion() == null) f.add("descripcion");
        if (d.tipo() == null) f.add("tipo");
        if (d.ciudad() == null) f.add("ciudad");
        if (d.direccion() == null) f.add("direccion");
        if (d.capacidad() == null || d.capacidad() <= 0) f.add("capacidad");
        if (d.precio() == null || d.precio().signum() <= 0) f.add("precioPorNoche");
        if (d.imagenes().isEmpty()) f.add("imagenes (al menos una)");
        if (d.idPolitica() == null) f.add("politicaCancelacion");
        return f;
    }

    /**
     * HU-006 CA-06: cambiar título, descripción, ubicación o tipo de una publicación ACTIVA
     * la devuelve a PENDIENTE_REVISION para que el administrador la apruebe de nuevo.
     */
    public static boolean cambiaCamposCriticos(Datos antes, Datos despues) {
        return !Objects.equals(antes.titulo(), despues.titulo())
                || !Objects.equals(antes.descripcion(), despues.descripcion())
                || !Objects.equals(antes.ciudad(), despues.ciudad())
                || !Objects.equals(antes.direccion(), despues.direccion())
                || !Objects.equals(antes.tipo(), despues.tipo());
    }

    // ------------------------------------------------------------------------- HU-007

    /** Resultado de validar una transición pedida por el anfitrión. */
    public enum Transicion { PERMITIDA, SIN_CAMBIO, BLOQUEADA_SOLO_ADMIN, EN_REVISION, NO_PERMITIDA }

    /**
     * RN-15 para el anfitrión: BORRADOR → ACTIVA, PAUSADA → ACTIVA y ACTIVA → PAUSADA.
     * PENDIENTE_REVISION y BLOQUEADA solo las cambia el administrador (HU-018).
     */
    public static Transicion transicionAnfitrion(String actual, String pedido) {
        if (Objects.equals(actual, pedido)) return Transicion.SIN_CAMBIO;
        if (BLOQUEADA.equals(actual)) return Transicion.BLOQUEADA_SOLO_ADMIN;
        if (PENDIENTE_REVISION.equals(actual)) return Transicion.EN_REVISION;
        boolean ok = (ACTIVA.equals(pedido) && (BORRADOR.equals(actual) || PAUSADA.equals(actual)))
                || (PAUSADA.equals(pedido) && ACTIVA.equals(actual));
        return ok ? Transicion.PERMITIDA : Transicion.NO_PERMITIDA;
    }

    // ------------------------------------------------------------------------- HU-024

    public static Tarifas normalizarTarifas(TarifasSolicitud s) {
        List<Adicional> lista = new ArrayList<>();
        if (s.serviciosAdicionales() != null) {
            for (ServicioAdicionalSolicitud a : s.serviciosAdicionales()) {
                if (a == null) continue;
                lista.add(new Adicional(texto(a.nombre()), a.costo(), a.activo() == null || a.activo()));
            }
        }
        return new Tarifas(s.tarifaLimpieza() == null ? BigDecimal.ZERO : s.tarifaLimpieza(),
                s.descuentoPct() == null ? BigDecimal.ZERO : s.descuentoPct(),
                s.descuentoMinNoches() == null ? 0 : s.descuentoMinNoches(), lista);
    }

    /** HU-024 CA-05: ningún valor negativo, descuento entre 0 y 100 y servicios con nombre único. */
    public static List<String> erroresTarifas(Tarifas t) {
        List<String> e = new ArrayList<>();
        if (t.limpieza().signum() < 0) e.add("tarifaLimpieza: no puede ser negativa");
        if (t.descuentoPct().signum() < 0 || t.descuentoPct().compareTo(BigDecimal.valueOf(100)) > 0) {
            e.add("descuentoPct: debe estar entre 0 y 100");
        }
        if (t.descuentoMinNoches() < 0) e.add("descuentoMinNoches: no puede ser negativo");
        if (t.descuentoPct().signum() > 0 && t.descuentoMinNoches() < 1) {
            e.add("descuentoMinNoches: indique desde cuántas noches aplica el descuento");
        }
        Set<String> nombres = new LinkedHashSet<>();
        for (int i = 0; i < t.adicionales().size(); i++) {
            Adicional a = t.adicionales().get(i);
            String p = "serviciosAdicionales[" + i + "]";
            if (a.nombre() == null) {
                e.add(p + ".nombre: es obligatorio");
            } else if (!nombres.add(a.nombre().toLowerCase(Locale.ROOT))) {
                e.add(p + ".nombre: «" + a.nombre() + "» está repetido");
            }
            if (a.costo() == null || a.costo().signum() < 0) e.add(p + ".costo: no puede ser negativo");
        }
        return e;
    }

    /** HU-024 CA-04: la tarifa de servicio es un porcentaje entre 0 y 100. */
    public static boolean tarifaServicioValida(BigDecimal pct) {
        return pct != null && pct.signum() >= 0 && pct.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    // ------------------------------------------------------------------------- utilidades

    static String texto(String s) {
        if (s == null) return null;
        String t = s.strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /** «Cabaña» → «CABANA», «habitación» → «HABITACION». */
    static String sinTildes(String s) {
        if (s == null) return null;
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    static List<String> sinRepetidos(List<String> valores) {
        if (valores == null) return List.of();
        Set<String> r = new LinkedHashSet<>();
        for (String v : valores) {
            String t = texto(v);
            if (t != null) r.add(t);
        }
        return List.copyOf(r);
    }
}
