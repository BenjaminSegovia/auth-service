package cl.duoc.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Solicitud para cambiar la contraseña del usuario autenticado.
 *
 * La contraseña actual se valida contra la base de datos y la nueva
 * debe cumplir las mismas reglas del registro (8 a 72 caracteres).
 */
@Data
public class ChangePasswordRequest {

    /** Contraseña vigente, necesaria para comprobar que es el dueño de la cuenta. */
    @NotBlank(message = "la contraseña actual es obligatoria")
    private String currentPassword;

    /** Nueva contraseña: no puede repetir la actual. */
    @NotBlank(message = "la nueva contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "la nueva contraseña debe tener entre 8 y 72 caracteres")
    private String newPassword;
}
