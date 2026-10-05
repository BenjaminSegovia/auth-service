package cl.duoc.authservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField("secret", "RecetaYaClaveSecretaMuySeguraParaJWT2026!");
        setField("expirationMs", 900000L);
        setField("refreshExpirationMs", 604800000L);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtService, value);
    }

    @Test
    void generarYLeerTokenDeAcceso() {
        String token = jwtService.generateToken("benja", "ADMIN");

        assertTrue(token.split("\\.").length == 3, "debe tener header.payload.signature");
        assertEquals("benja", jwtService.extractUsername(token));
        assertEquals(JwtService.TOKEN_TYPE_ACCESS, jwtService.extractTokenType(token));
        assertTrue(jwtService.isTokenValid(token, "benja", JwtService.TOKEN_TYPE_ACCESS));
        assertFalse(jwtService.isTokenValid(token, "otro-usuario"));
    }

    @Test
    void elRefreshTokenNoSirveComoAccessToken() {
        String refresh = jwtService.generateRefreshToken("benja", "USER");

        assertEquals(JwtService.TOKEN_TYPE_REFRESH, jwtService.extractTokenType(refresh));
        assertTrue(jwtService.isTokenValid(refresh, "benja", JwtService.TOKEN_TYPE_REFRESH));
        // Un refresh token NO debe autenticar llamadas a la API
        assertFalse(jwtService.isTokenValid(refresh, "benja", JwtService.TOKEN_TYPE_ACCESS));
    }

    @Test
    void tokenFalsificadoEsInvalido() {
        String token = jwtService.generateToken("benja", "USER");
        String manipulado = token.substring(0, token.length() - 2) + "xx";

        assertFalse(jwtService.isTokenValid(manipulado, "benja"));
        assertFalse(jwtService.isTokenValid(manipulado, "benja", JwtService.TOKEN_TYPE_ACCESS));
    }
}
