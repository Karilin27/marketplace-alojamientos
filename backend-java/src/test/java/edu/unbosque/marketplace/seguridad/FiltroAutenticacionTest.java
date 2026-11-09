package edu.unbosque.marketplace.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Rutas públicas y protegidas del filtro de autenticación. */
class FiltroAutenticacionTest {

    private final FiltroAutenticacion filtro =
            new FiltroAutenticacion(mock(JwtVerificador.class), new ObjectMapper(), Clock.systemUTC());

    private int estadoSinToken(String metodo, String ruta) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest(metodo, ruta), res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void registroEsPublico() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, estadoSinToken("POST", "/api/auth/register"));
    }

    @Test
    void lasDemasRutasSiguenExigiendoToken() throws Exception {
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, estadoSinToken("GET", "/api/users/me"));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, estadoSinToken("GET", "/api/auth/register"));
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, estadoSinToken("POST", "/api/auth/register/otra"));
    }
}
