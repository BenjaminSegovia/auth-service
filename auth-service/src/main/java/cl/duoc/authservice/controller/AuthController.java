package cl.duoc.authservice.controller;

import cl.duoc.authservice.dto.AuthResponse;
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
import org.springframework.web.bind.annotation.*;

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
}
