package cl.duoc.authservice.controller;

import cl.duoc.authservice.dto.AuthResponse;
import cl.duoc.authservice.dto.ChangePasswordRequest;
import cl.duoc.authservice.dto.LoginRequest;
import cl.duoc.authservice.dto.RefreshRequest;
import cl.duoc.authservice.dto.RegisterRequest;
import cl.duoc.authservice.dto.RoleUpdateRequest;
import cl.duoc.authservice.dto.UserResponse;
import cl.duoc.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Cierra la sesión. Es stateless: el servidor no guarda nada, basta con que
     * el cliente descarte el access token y el refresh token.
     * Exige un JWT de acceso válido (si no lo hay, Security responde 401).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

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
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails usuario) {
        return ResponseEntity.ok(authService.me(usuario.getUsername()));
    }

    /**
     * Lista todos los usuarios registrados. Solo un ADMIN puede verlos.
     *
     * @return 200 con la lista completa de usuarios.
     */
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
    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                @AuthenticationPrincipal UserDetails usuario) {
        authService.cambiarPassword(usuario.getUsername(), request);
        return ResponseEntity.noContent().build();
    }
}
