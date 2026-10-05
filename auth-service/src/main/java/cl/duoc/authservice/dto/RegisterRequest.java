package cl.duoc.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Solicitud de registro. El rol NO viene en el body: lo asigna el servidor
 * para evitar que cualquiera se registre como MÉDICO o FARMACÉUTICO.
 */
@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @NotBlank
    @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @NotBlank
    private String nombreCompleto;
}
