package edu.unbosque.marketplace.identidad;

import edu.unbosque.marketplace.common.ReglaNegocioException;
import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroRespuesta;
import edu.unbosque.marketplace.identidad.RegistroDtos.RegistroSolicitud;
import edu.unbosque.marketplace.logs.EventLogger;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * HU-001: registro de usuario como HUESPED o ANFITRION.
 * La validación y el cifrado BCrypt se hacen antes de abrir la transacción, para no ocupar
 * una conexión del pool mientras tanto.
 */
@Service
public class RegistroService {

    private final UsuarioRepositorio repo;
    private final PasswordEncoder codificador;
    private final TransactionTemplate tx;
    private final EventLogger log;

    public RegistroService(UsuarioRepositorio repo, PasswordEncoder codificador, TransactionTemplate tx,
                           EventLogger log) {
        this.repo = repo;
        this.codificador = codificador;
        this.tx = tx;
        this.log = log;
    }

    public RegistroRespuesta registrar(RegistroSolicitud solicitud) {
        if (solicitud == null) {
            throw ReglaNegocioException.solicitudInvalida("REGISTRO_INVALIDO", "Debe enviar los datos del registro.");
        }
        var datos = ReglasRegistro.normalizar(solicitud);
        List<String> errores = ReglasRegistro.errores(datos);
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.solicitudInvalida("REGISTRO_INVALIDO",
                    "Hay datos del registro que debe corregir.", errores.toArray(String[]::new));
        }
        String hash = codificador.encode(datos.contrasena());

        try {
            RegistroRespuesta creado = tx.execute(estado -> {
                if (repo.existeCorreo(datos.correo())) {
                    throw correoDuplicado();
                }
                return repo.insertar(datos.nombre(), datos.correo(), hash, datos.rol());
            });
            log.auditado("USUARIO_REGISTRADO", creado.id().toString(), "usuario", creado.id().toString(), "EXITO",
                    Map.of("rol", creado.rol()));
            return creado;
        } catch (DuplicateKeyException e) {
            // Dos registros simultáneos con el mismo correo: lo detiene el índice único usuario_correo_uk.
            throw correoDuplicado();
        }
    }

    private static ReglaNegocioException correoDuplicado() {
        return ReglaNegocioException.conflicto("CORREO_YA_REGISTRADO", "El correo electrónico ya está registrado.",
                "correo");
    }
}
