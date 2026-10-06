package cl.duoc.authservice.controller;

import cl.duoc.authservice.dto.AuthResponse;
import cl.duoc.authservice.dto.ChangePasswordRequest;
import cl.duoc.authservice.dto.ErrorResponse;
import cl.duoc.authservice.dto.LoginRequest;
import cl.duoc.authservice.dto.RefreshRequest;
import cl.duoc.authservice.dto.RegisterRequest;
import cl.duoc.authservice.dto.RoleUpdateRequest;
import cl.duoc.authservice.dto.UserResponse;
import cl.duoc.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de autenticación y gestión de usuarios del microservicio.
 *
 * Todas las respuestas de error usan la estructura {@link ErrorResponse}.
 * Las rutas marcadas como ADMIN exigen un JWT firmado con rol ADMIN.
 */
@Tag(name = "Autenticación", description = "Registro, inicio de sesión, tokens y gestión de usuarios y roles")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registra un nuevo usuario y lo deja logueado.
     *
     * @param request username, contraseña y nombre completo.
     * @return 201 con access token, refresh token, username y rol asignado.
     */
    @Operation(summary = "Registra un nuevo usuario",
            description = "Crea la cuenta y devuelve access y refresh token. El rol lo asigna el servidor: "
                    + "el primer usuario registrado queda como ADMIN y el resto como USER.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario creado con sus tokens"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El username ya está en uso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Inicia sesión con username y contraseña.
     *
     * @param request credenciales del usuario.
     * @return 200 con access token, refresh token, username y rol.
     */
    @Operation(summary = "Inicia sesión",
            description = "Valida username y contraseña y devuelve un nuevo par de tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Credenciales correctas: se devuelven los tokens"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Intercambia un refresh token válido por un nuevo par de tokens.
     *
     * @param request refresh token emitido en register o login.
     * @return 200 con un access token y un refresh token nuevos.
     */
    @Operation(summary = "Renueva los tokens",
            description = "Acepta un refresh token vigente y devuelve un nuevo access token y un nuevo refresh token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens renovados"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Cierra la sesión. Es stateless: el servidor no guarda nada, basta con que
     * el cliente descarte el access token y el refresh token.
     * Exige un JWT de acceso válido (si no lo hay, Security responde 401).
     *
     * @return 204 si el token de acceso era válido.
     */
    @Operation(summary = "Cierra la sesión",
            description = "Operación stateless: el cliente debe descartar sus tokens. "
                    + "Exige un access token válido en el header Authorization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Sesión cerrada"),
            @ApiResponse(responseCode = "401", description = "Sin token, token expirado o token de tipo refresh",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia el rol de un usuario. Solo un ADMIN puede hacerlo.
     *
     * @param username usuario a modificar.
     * @param request nuevo rol (USER, MEDICO, FARMACEUTICO o ADMIN).
     * @return 200 con el usuario actualizado completo.
     */
    @Operation(summary = "Cambia el rol de un usuario",
            description = "Operación exclusiva de ADMIN. Devuelve el usuario con id, username, nombreCompleto y rol.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rol actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin token de acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/users/{username}/role")
    public ResponseEntity<UserResponse> updateRole(@PathVariable String username,
                                                   @Valid @RequestBody RoleUpdateRequest request) {
        UserResponse response = authService.assignRole(username, request.getRole());
        return ResponseEntity.ok(response);
    }

    /**
     * Devuelve el perfil del usuario autenticado.
     * El username se lee del JWT enviado en el header Authorization.
     *
     * @param usuario usuario que resultó autenticado por el filtro JWT.
     * @return 200 con id, username, nombreCompleto y rol vigente.
     */
    @Operation(summary = "Devuelve el perfil del usuario autenticado",
            description = "Lee el username del token JWT enviado en el header Authorization.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil del usuario"),
            @ApiResponse(responseCode = "401", description = "Sin token de acceso o token expirado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails usuario) {
        return ResponseEntity.ok(authService.me(usuario.getUsername()));
    }

    /**
     * Lista todos los usuarios registrados. Solo un ADMIN puede verlos.
     *
     * @return 200 con la lista completa de usuarios.
     */
    @Operation(summary = "Lista todos los usuarios",
            description = "Operación exclusiva de ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de usuarios"),
            @ApiResponse(responseCode = "401", description = "Sin token de acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> listarUsuarios() {
        return ResponseEntity.ok(authService.listarUsuarios());
    }

    /**
     * Consulta un usuario por su username. Solo un ADMIN puede verlo.
     *
     * @param username usuario a consultar.
     * @return 200 con el usuario, o 404 si no existe.
     */
    @Operation(summary = "Consulta un usuario por username",
            description = "Operación exclusiva de ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
            @ApiResponse(responseCode = "401", description = "Sin token de acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/users/{username}")
    public ResponseEntity<UserResponse> obtenerUsuario(@PathVariable String username) {
        return ResponseEntity.ok(authService.obtenerUsuario(username));
    }

    /**
     * Elimina un usuario. Solo un ADMIN, y nunca la cuenta con la que
     * se está operando.
     *
     * @param username usuario a eliminar.
     * @param usuarioActual usuario autenticado (quien ejecuta la operación).
     * @return 204 si se eliminó, 400 si intenta borrarse a sí mismo, 404 si no existe.
     */
    @Operation(summary = "Elimina un usuario",
            description = "Operación exclusiva de ADMIN. No permite eliminarse a uno mismo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Usuario eliminado"),
            @ApiResponse(responseCode = "400", description = "Intentó eliminar la cuenta con la que está operando",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin token de acceso",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario autenticado no es ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario no existe",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/users/{username}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable String username,
                                                @AuthenticationPrincipal UserDetails usuarioActual) {
        authService.eliminarUsuario(username, usuarioActual.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * @param request contraseña actual y nueva contraseña.
     * @param usuario usuario autenticado (dueño de la contraseña).
     * @return 204 si se cambió, 401 si la contraseña actual es incorrecta,
     *         400 si la nueva es igual a la actual o no cumple las reglas.
     */
    @Operation(summary = "Cambia la contraseña del usuario autenticado",
            description = "Exige la contraseña actual para comprobar la identidad; "
                    + "la nueva debe ser distinta y tener entre 8 y 72 caracteres.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Contraseña actualizada"),
            @ApiResponse(responseCode = "400", description = "Nueva contraseña inválida o igual a la actual",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sin token o contraseña actual incorrecta",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                @AuthenticationPrincipal UserDetails usuario) {
        authService.cambiarPassword(usuario.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}
