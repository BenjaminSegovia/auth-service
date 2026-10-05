package cl.duoc.authservice;

import cl.duoc.authservice.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el flujo completo de la API: registro, login, refresh, logout,
 * control de roles y la respuesta de error uniforme.
 *
 * Usa H2 en memoria (ver src/test/resources/application.yaml), por lo que
 * no necesita PostgreSQL corriendo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void limpiarUsuarios() {
        usuarioRepository.deleteAll();
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode registrar(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\","
                                + "\"password\":\"" + password + "\","
                                + "\"nombreCompleto\":\"Nombre " + username + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------ observación 1

    @Test
    void losErroresRespondenConEstructuraUniforme() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"benja\",\"password\":\"secret1234\",\"nombreCompleto\":\"B\"}"))
                .andExpect(status().isCreated());

        // 409 por usuario duplicado
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"benja\",\"password\":\"secret1234\",\"nombreCompleto\":\"B\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ya está en uso")))
                .andExpect(jsonPath("$.path").value("/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // 400 por validación, con el detalle campo a campo
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"otro\",\"password\":\"corto\",\"nombreCompleto\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.password").value(
                        org.hamcrest.Matchers.containsString("8 y 72")));

        // 401 con la misma estructura
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"secret1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ------------------------------------------------------------ observación 3

    @Test
    void elRolLoAsignaElServidorNoElCliente() throws Exception {
        // El registro ya no acepta "role" en el body
        JsonNode primero = registrar("admin1", "secret1234");
        assertThat(primero.get("role").asText())
                .as("el primer usuario (bootstrap) queda como ADMIN")
                .isEqualTo("ADMIN");

        JsonNode segundo = registrar("juan", "secret1234");
        assertThat(segundo.get("role").asText())
                .as("el resto se registra como USER")
                .isEqualTo("USER");
    }

    @Test
    void soloUnAdminPuedeAsignarRoles() throws Exception {
        JsonNode admin = registrar("admin1", "secret1234"); // primer usuario -> ADMIN
        JsonNode juan = registrar("juan", "secret1234");    // -> USER
        String tokenAdmin = admin.get("token").asText();
        String tokenJuan = juan.get("token").asText();

        // Sin token -> 401
        mvc.perform(put("/auth/users/juan/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEDICO\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Con token de un USER -> 403
        mvc.perform(put("/auth/users/juan/role")
                        .header("Authorization", bearer(tokenJuan))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEDICO\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Con token de ADMIN -> 200 y rol aplicado
        mvc.perform(put("/auth/users/juan/role")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEDICO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.role").value("MEDICO"));

        // Usuario inexistente -> 404 con la misma estructura
        mvc.perform(put("/auth/users/fantasma/role")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ------------------------------------------------------------ observación 2

    @Test
    void elFiltroJwtAutenticaSoloAccessTokens() throws Exception {
        JsonNode admin = registrar("admin1", "secret1234");
        String accessToken = admin.get("token").asText();
        String refreshToken = admin.get("refreshToken").asText();

        // Sin token -> 401 en JSON (entry point)
        mvc.perform(post("/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        // Con refresh token -> rechazado (no sirve para llamar a la API)
        mvc.perform(post("/auth/logout").header("Authorization", bearer(refreshToken)))
                .andExpect(status().isUnauthorized());

        // Con token corrupto -> rechazado
        mvc.perform(post("/auth/logout").header("Authorization", bearer("token.trucha.abc")))
                .andExpect(status().isUnauthorized());

        // Con access token válido -> autenticado (204)
        mvc.perform(post("/auth/logout").header("Authorization", bearer(accessToken)))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ observación 6

    @Test
    void elRefreshTokenEntregaUnNuevoParDeTokens() throws Exception {
        JsonNode registrado = registrar("admin1", "secret1234");
        String refreshToken = registrado.get("refreshToken").asText();

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin1"));

        // Un access token no se puede usar para refrescar
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + registrado.get("token").asText() + "\"}"))
                .andExpect(status().isUnauthorized());

        // Refresh token inventado -> 401
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"aaa.bbb.ccc\"}"))
                .andExpect(status().isUnauthorized());

        // Body vacío -> 400 por validación
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.refreshToken").isNotEmpty());
    }

    @Test
    void loginEntregaTokensYRechazaCredencialesInvalidas() throws Exception {
        registrar("admin1", "secret1234");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin1\",\"password\":\"secret1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin1"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin1\",\"password\":\"otraClave\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"noExiste\",\"password\":\"secret1234\"}"))
                .andExpect(status().isUnauthorized());
    }
}
