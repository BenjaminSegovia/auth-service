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
        setField("expirationMs", 86400000L);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtService, value);
    }

    @Test
    void generarYLeerToken() {
        String token = jwtService.generateToken("benja", "ADMIN");

        assertTrue(token.split("\\.").length == 3, "debe tener header.payload.signature");
        assertEquals("benja", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, "benja"));
        assertFalse(jwtService.isTokenValid(token, "otro-usuario"));
    }

    @Test
    void tokenFalsificadoEsInvalido() {
        String token = jwtService.generateToken("benja", "USER");
        String manipulado = token.substring(0, token.length() - 2) + "xx";

        assertFalse(jwtService.isTokenValid(manipulado, "benja"));
    }
}
