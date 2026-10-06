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
}
