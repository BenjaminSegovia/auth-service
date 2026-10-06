package cl.duoc.authservice.service;

import cl.duoc.authservice.dto.AuthResponse;
import cl.duoc.authservice.dto.ChangePasswordRequest;
import cl.duoc.authservice.dto.LoginRequest;
import cl.duoc.authservice.dto.RegisterRequest;
import cl.duoc.authservice.dto.UserResponse;
import cl.duoc.authservice.exception.InvalidCredentialsException;
import cl.duoc.authservice.exception.OperacionInvalidaException;
import cl.duoc.authservice.exception.UsernameAlreadyExistsException;
import cl.duoc.authservice.exception.UsuarioNotFoundException;
import cl.duoc.authservice.model.Role;
import cl.duoc.authservice.model.Usuario;
import cl.duoc.authservice.repository.UsuarioRepository;
import cl.duoc.authservice.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Repositorio de usuarios para acceder a la base de datos.
     */
    private final UsuarioRepository usuarioRepository;
    /**
     * Codificador de contraseñas para almacenar contraseñas de forma segura.
     */
    private final PasswordEncoder passwordEncoder;
    /**
     * Servicio para generar y validar tokens JWT.
     */
    private final JwtService jwtService;

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * El rol lo decide el servidor: el primer usuario de la base queda como ADMIN
     * (bootstrap) y todos los demás como USER. Así nadie puede autoasignarse
     * MÉDICO, FARMACÉUTICO o ADMIN en el registro.
     *
     * @param request username, contraseña y nombre completo.
     * @return access token, refresh token, username y rol asignado.
     * @throws UsernameAlreadyExistsException si el username ya está en uso.
     */
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(
                    "El username '" + request.getUsername() + "' ya está en uso");
        }

        Role rolInicial = usuarioRepository.count() == 0 ? Role.ADMIN : Role.USER;

        Usuario usuario = Usuario.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombreCompleto(request.getNombreCompleto())
                .role(rolInicial)
                .build();

        usuarioRepository.save(usuario);

        return buildTokens(usuario);
    }

    /**
     * Inicia sesión con username y contraseña.
     *
     * @param request credenciales del usuario.
     * @return access token, refresh token, username y rol.
     * @throws InvalidCredentialsException si el usuario o la contraseña son incorrectos.
     */
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

        return buildTokens(usuario);
    }

    /**
     * Intercambia un refresh token válido por un nuevo par de tokens.
     *
     * @param refreshToken token de refresco emitido en register/login.
     * @return nuevo access token y nuevo refresh token (rotación).
     * @throws InvalidCredentialsException si el token es inválido, vencido o no es de tipo refresh.
     */
    public AuthResponse refresh(String refreshToken) {
        String username;
        try {
            username = jwtService.extractUsername(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException("Refresh token inválido o expirado");
        }

        if (!jwtService.isTokenValid(refreshToken, username, JwtService.TOKEN_TYPE_REFRESH)) {
            throw new InvalidCredentialsException("Refresh token inválido o expirado");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Refresh token inválido o expirado"));

        return buildTokens(usuario);
    }

    /**
     * Cambia el rol de un usuario. Solo lo llama un ADMIN.
     *
     * @param username usuario a modificar.
     * @param role nuevo rol.
     * @return username y rol resultantes.
     * @throws UsuarioNotFoundException si el usuario no existe.
     */
    public UserResponse assignRole(String username, Role role) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNotFoundException("El usuario '" + username + "' no existe"));

        usuario.setRole(role);
        usuarioRepository.save(usuario);

        return toUserResponse(usuario);
    }

    /**
     * Devuelve el perfil del usuario que hizo la petición (su username viene en el JWT).
     *
     * @param username usuario autenticado.
     * @return id, username, nombre completo y rol vigente.
     * @throws UsuarioNotFoundException si el usuario ya no existe en la base.
     */
    public UserResponse me(String username) {
        return toUserResponse(buscar(username));
    }

    /**
     * Lista todos los usuarios registrados, con su rol vigente.
     * Solo lo llama un ADMIN (lo garantiza SecurityConfig).
     *
     * @return lista con todos los usuarios.
     */
    public List<UserResponse> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::toUserResponse)
                .toList();
    }

    /**
     * Consulta un usuario por su username.
     *
     * @param username usuario a consultar.
     * @return id, username, nombre completo y rol vigente.
     * @throws UsuarioNotFoundException si no existe.
     */
    public UserResponse obtenerUsuario(String username) {
        return toUserResponse(buscar(username));
    }

    /**
     * Elimina un usuario de la base de datos.
     *
     * No permite eliminarse a uno mismo: dejaría al sistema sin la cuenta
     * que está operando y, si es el último ADMIN, sin administradores.
     *
     * @param username usuario a eliminar.
     * @param usuarioActual username de quien ejecuta la operación.
     * @throws OperacionInvalidaException si intenta eliminarse a sí mismo.
     * @throws UsuarioNotFoundException si el usuario a eliminar no existe.
     */
    public void eliminarUsuario(String username, String usuarioActual) {
        if (username.equals(usuarioActual)) {
            throw new OperacionInvalidaException("No puedes eliminar la cuenta con la que estás operando");
        }

        usuarioRepository.delete(buscar(username));
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     *
     * Se exige la contraseña actual para comprobar la identidad y la nueva
     * debe ser distinta de la que ya está en uso. Se guarda hasheada con BCrypt.
     *
     * @param username usuario autenticado.
     * @param request contraseña actual y nueva.
     * @throws InvalidCredentialsException si la contraseña actual no coincide.
     * @throws OperacionInvalidaException si la nueva es igual a la actual.
     */
    public void cambiarPassword(String username, ChangePasswordRequest request) {
        Usuario usuario = buscar(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), usuario.getPassword())) {
            throw new InvalidCredentialsException("La contraseña actual es incorrecta");
        }

        if (passwordEncoder.matches(request.getNewPassword(), usuario.getPassword())) {
            throw new OperacionInvalidaException("La nueva contraseña debe ser distinta de la actual");
        }

        usuario.setPassword(passwordEncoder.encode(request.getNewPassword()));
        usuarioRepository.save(usuario);
    }

    /**
     * Busca un usuario por username o lanza la excepción 404.
     */
    private Usuario buscar(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNotFoundException("El usuario '" + username + "' no existe"));
    }

    /**
     * Convierte la entidad en la respuesta estándar de usuario,
     * rellenando siempre los cuatro campos.
     */
    private UserResponse toUserResponse(Usuario usuario) {
        return UserResponse.builder()
                .id(usuario.getId())
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .role(usuario.getRole().name())
                .build();
    }

    private AuthResponse buildTokens(Usuario usuario) {
        String role = usuario.getRole().name();

        return AuthResponse.builder()
                .token(jwtService.generateToken(usuario.getUsername(), role))
                .refreshToken(jwtService.generateRefreshToken(usuario.getUsername(), role))
                .username(usuario.getUsername())
                .role(role)
                .build();
    }
}
