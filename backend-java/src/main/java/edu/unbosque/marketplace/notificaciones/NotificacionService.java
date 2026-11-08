package edu.unbosque.marketplace.notificaciones;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.reservas.web.Dtos.Pagina;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** HU-016: notificaciones dentro de la plataforma (RF-017). */
@Service
public class NotificacionService {

    public static final String RESERVA_CREADA = "RESERVA_CREADA";
    public static final String PAGO_APROBADO = "PAGO_APROBADO";
    public static final String PAGO_RECHAZADO = "PAGO_RECHAZADO";
    public static final String RESERVA_RECIBIDA = "RESERVA_RECIBIDA";
    public static final String RESERVA_CANCELADA = "RESERVA_CANCELADA";
    public static final String RESERVA_EXPIRADA = "RESERVA_EXPIRADA";
    public static final String RESERVA_COMPLETADA = "RESERVA_COMPLETADA";
    public static final String REEMBOLSO = "REEMBOLSO";

    private final NotificacionRepositorio repo;
    private final Clock reloj;

    public NotificacionService(NotificacionRepositorio repo, Clock reloj) {
        this.repo = repo;
        this.reloj = reloj;
    }

    /** Se invoca dentro de la transacción de la operación de negocio, para que ambas se confirmen juntas. */
    public void notificar(UUID idUsuario, String tipo, String mensaje, UUID idReserva) {
        if (idUsuario != null) {
            repo.insertar(idUsuario, tipo, mensaje, idReserva, reloj.instant());
        }
    }

    public Pagina<Notificacion> listar(UUID idUsuario, boolean soloNoLeidas, int pagina, int tamano) {
        int p = Math.max(pagina, 0);
        int t = Math.min(Math.max(tamano, 1), 100);
        return new Pagina<>(repo.listar(idUsuario, soloNoLeidas, p, t), p, t, repo.contar(idUsuario, soloNoLeidas));
    }

    public Notificacion marcarLeida(UUID id, UUID idUsuario) {
        return repo.marcarLeida(id, idUsuario)
                .orElseThrow(() -> ReglaNegocioException.noEncontrado("La notificación no existe."));
    }
}
