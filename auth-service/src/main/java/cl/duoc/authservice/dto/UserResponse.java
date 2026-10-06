package cl.duoc.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta cuando se consulta o cambia la información de un usuario:
 * cambio de rol (PUT /auth/users/{username}/role) y consulta de perfil
 * (GET /auth/me).
 *
 * Todos los campos se rellenan en {@code AuthService}; ninguno puede quedar
 * en null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** Identificador numérico del usuario en la base de datos. */
    private Long id;

    /** Nombre de usuario (único en todo el sistema). */
    private String username;

    /** Nombre completo tal como se registró. */
    private String nombreCompleto;

    /** Rol vigente: USER, MEDICO, FARMACEUTICO o ADMIN. */
    private String role;
}
