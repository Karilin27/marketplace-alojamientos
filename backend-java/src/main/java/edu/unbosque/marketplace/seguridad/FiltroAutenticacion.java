package edu.unbosque.marketplace.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.unbosque.marketplace.common.ErrorRespuesta;
import edu.unbosque.marketplace.common.ReglaNegocioException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Exige "Authorization: Bearer" en /api/**, salvo GET /api/availability/** (público, HU-008). */
@Component
public class FiltroAutenticacion extends OncePerRequestFilter {

    public static final String ATRIBUTO = "sesion";

    private final JwtVerificador verificador;
    private final ObjectMapper json;
    private final Clock reloj;

    public FiltroAutenticacion(JwtVerificador verificador, ObjectMapper json, Clock reloj) {
        this.verificador = verificador;
        this.json = json;
        this.reloj = reloj;
    }

    /** Rutas POST que no exigen token: crear cuenta (HU-001). */
    private static final Set<String> POST_PUBLICAS = Set.of("/api/auth/register");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String ruta = req.getRequestURI();
        boolean publica = ("GET".equals(req.getMethod()) && ruta.startsWith("/api/availability/"))
                || ("POST".equals(req.getMethod()) && POST_PUBLICAS.contains(ruta));
        return !ruta.startsWith("/api/") || publica || "OPTIONS".equals(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain cadena)
            throws ServletException, IOException {
        String cabecera = req.getHeader("Authorization");
        try {
            if (cabecera == null || !cabecera.startsWith("Bearer ")) {
                throw ReglaNegocioException.noAutenticado("Falta la cabecera Authorization: Bearer <token>.");
            }
            req.setAttribute(ATRIBUTO, verificador.verificar(cabecera.substring(7).trim(), reloj.instant()));
        } catch (ReglaNegocioException e) {
            res.setStatus(e.tipo().http());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getWriter(), new ErrorRespuesta(e.codigo(), e.getMessage(), e.detalles()));
            return;
        }
        cadena.doFilter(req, res);
    }
}
