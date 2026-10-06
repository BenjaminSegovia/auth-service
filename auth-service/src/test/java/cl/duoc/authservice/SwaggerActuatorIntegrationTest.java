package cl.duoc.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que la documentación OpenAPI (Swagger UI) y el health check de
 * Actuator queden accesibles SIN token, y que el resto de Actuator siga
 * protegido por el filtro JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerActuatorIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void elHealthDeActuatorEsPublico() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void laDocumentacionOpenApiEsPublica() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.paths").isNotEmpty());
    }

    @Test
    void laInterfazDeSwaggerEsPublica() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void elRestoDeActuatorSigueProtegido() throws Exception {
        // metrics no está en management.endpoints.web.exposure.include ni permitido -> 401
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void losEndpointsEstanDocumentadosConOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // @Tag del controller
                .andExpect(jsonPath("$.tags[0].name").value("Autenticación"))
                // @Operation + @ApiResponse de cada endpoint
                .andExpect(jsonPath("$.info.title").value("RecetaYa - auth-service API"))
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses.201").exists())
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/auth/me'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/auth/password'].put.responses.204").exists())
                .andExpect(jsonPath("$.paths['/auth/users'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/auth/users/{username}'].delete.responses.204").exists())
                .andExpect(jsonPath("$.paths['/auth/users/{username}/role'].put.responses.404").exists());
    }
}
