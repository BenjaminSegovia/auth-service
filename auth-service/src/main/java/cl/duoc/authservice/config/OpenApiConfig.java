package cl.duoc.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Datos generales que Swagger UI muestra en la cabecera de la documentación
 * (título, versión y descripción del microservicio).
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "RecetaYa - auth-service API",
        version = "1.0.0",
        description = "Microservicio de autenticación y autorización con JWT: registro, inicio de sesión, "
                + "renovación de tokens, cambio de contraseña y gestión de usuarios y roles."))
public class OpenApiConfig {
}
