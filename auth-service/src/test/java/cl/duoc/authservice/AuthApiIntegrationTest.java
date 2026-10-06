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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        // Con token de ADMIN -> 200 y rol aplicado (respuesta completa, sin campos en null)
        mvc.perform(put("/auth/users/juan/role")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MEDICO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.nombreCompleto").value("Nombre juan"))
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

    // ------------------------------------------------------------------- CRUD

    @Test
    void meDevuelveElPerfilDelUsuarioAutenticado() throws Exception {
        JsonNode registrado = registrar("perfil1", "secret1234");
        String token = registrado.get("token").asText();

        // 200 con los cuatro campos del perfil
        mvc.perform(get("/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("perfil1"))
                .andExpect(jsonPath("$.nombreCompleto").value("Nombre perfil1"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 401 sin token
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soloElAdminPuedeListarYConsultarUsuarios() throws Exception {
        JsonNode admin = registrar("admin1", "secret1234");
        JsonNode juan = registrar("juan", "secret1234");
        String tokenAdmin = admin.get("token").asText();
        String tokenJuan = juan.get("token").asText();

        // 401 sin token
        mvc.perform(get("/auth/users"))
                .andExpect(status().isUnauthorized());

        // 403 con token de USER
        mvc.perform(get("/auth/users").header("Authorization", bearer(tokenJuan)))
                .andExpect(status().isForbidden());

        // 200 con ADMIN: lista los dos usuarios
        mvc.perform(get("/auth/users").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 200 consulta individual, con el perfil completo
        mvc.perform(get("/auth/users/juan").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.nombreCompleto").value("Nombre juan"))
                .andExpect(jsonPath("$.role").value("USER"));

        // 404 usuario inexistente
        mvc.perform(get("/auth/users/fantasma").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void elAdminEliminaUsuariosPeroNoSiMismo() throws Exception {
        JsonNode admin = registrar("admin1", "secret1234");
        JsonNode juan = registrar("juan", "secret1234");
        String tokenAdmin = admin.get("token").asText();
        String tokenJuan = juan.get("token").asText();

        // 403: un USER no puede eliminar
        mvc.perform(delete("/auth/users/juan").header("Authorization", bearer(tokenJuan)))
                .andExpect(status().isForbidden());

        // 204: el ADMIN elimina a juan
        mvc.perform(delete("/auth/users/juan").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isNoContent());

        // 404: ya no existe
        mvc.perform(delete("/auth/users/juan").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isNotFound());

        // 400: no puede eliminarse a sí mismo
        mvc.perform(delete("/auth/users/admin1").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No puedes eliminar la cuenta con la que estás operando"));
    }

    @Test
    void sePuedeCambiarLaPasswordYLaAntiguaDejaDeServir() throws Exception {
        JsonNode registrado = registrar("cambiador", "secret1234");
        String token = registrado.get("token").asText();
        String cambio = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}";

        // 401 sin token
        mvc.perform(put("/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(cambio, "secret1234", "nuevaClave123")))
                .andExpect(status().isUnauthorized());

        // 401 si la contraseña actual no coincide
        mvc.perform(put("/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(cambio, "claveIncorrecta", "nuevaClave123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("La contraseña actual es incorrecta"));

        // 400 si la nueva es igual a la actual
        mvc.perform(put("/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(cambio, "secret1234", "secret1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La nueva contraseña debe ser distinta de la actual"));

        // 400 con detalle campo a campo si la nueva no cumple las reglas
        mvc.perform(put("/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(cambio, "secret1234", "corta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").isNotEmpty());

        // 204 si todo está bien
        mvc.perform(put("/auth/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(cambio, "secret1234", "nuevaClave123")))
                .andExpect(status().isNoContent());

        // la contraseña vieja ya no sirve (401) y la nueva sí (200)
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cambiador\",\"password\":\"secret1234\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cambiador\",\"password\":\"nuevaClave123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
