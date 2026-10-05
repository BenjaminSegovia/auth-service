package cl.duoc.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    /** Token de acceso: vida corta (jwt.expiration-ms), se envía en cada petición. */
    private String token;

    /** Token de refresco: vida larga (jwt.refresh-expiration-ms), solo para pedir un nuevo token. */
    private String refreshToken;

    private String username;
    private String role;
}
