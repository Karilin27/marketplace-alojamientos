package edu.unbosque.marketplace.alojamientos;

import edu.unbosque.marketplace.alojamientos.AlojamientoDtos.AlojamientoRespuesta;
import edu.unbosque.marketplace.alojamientos.AlojamientoRepositorio.Registro;
import edu.unbosque.marketplace.alojamientos.ReglasAlojamiento.Datos;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.seguridad.Sesion;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Piezas comunes de S2: armar la respuesta completa y verificar que quien opera sea el dueño (RN-02). */
@Component
public class AlojamientoConsultas {

    private final AlojamientoRepositorio repo;

    public AlojamientoConsultas(AlojamientoRepositorio repo) {
        this.repo = repo;
    }

    public Registro existente(UUID id, boolean bloquear) {
        return (bloquear ? repo.buscarBloqueando(id) : repo.buscar(id))
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La publicación no existe."));
    }

    /** RN-02: solo el anfitrión dueño (o un administrador, HU-006 CA-05) puede modificar la publicación. */
    public void exigirDuenoOAdmin(Sesion sesion, Registro r) {
        if (!sesion.esAdmin() && !r.idAnfitrion().equals(sesion.id())) {
            throw ReglaNegocioException.prohibido("Solo el anfitrión dueño puede modificar esta publicación.");
        }
    }

    /** Datos de la fila en el mismo formato que la solicitud, para comparar y validar. */
    public Datos datos(Registro r) {
        return new Datos(r.titulo(), r.descripcion(), r.tipo(), r.ciudad(), r.direccion(), r.capacidad(), r.precio(),
                repo.servicios(r.id()), r.reglas(), repo.fotos(r.id()), r.idPolitica());
    }

    public AlojamientoRespuesta respuesta(UUID id, boolean soloAdicionalesActivos) {
        return respuesta(existente(id, false), soloAdicionalesActivos);
    }

    public AlojamientoRespuesta respuesta(Registro r, boolean soloAdicionalesActivos) {
        BigDecimal[] cal = repo.calificacion(r.id());
        return new AlojamientoRespuesta(r.id(), r.idAnfitrion(), r.titulo(), r.descripcion(), r.tipo(), r.ciudad(),
                r.direccion(), r.capacidad() == null ? 0 : r.capacidad(), r.precio(), repo.servicios(r.id()),
                r.reglas(), repo.fotos(r.id()), r.estado(), repo.politica(r.idPolitica()).orElse(null),
                r.tarifaLimpieza(), r.descuentoPct(), r.descuentoMinNoches(),
                repo.adicionales(r.id(), soloAdicionalesActivos), cal[0], cal[1].intValue(), r.fechaActualizacion());
    }
}
