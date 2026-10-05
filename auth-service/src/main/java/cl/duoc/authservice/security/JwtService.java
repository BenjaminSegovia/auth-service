package cl.duoc.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Genera y valida tokens JWT firmados con HS256.
 *
 * Existen dos tipos de token (claim "type"):
 *  - "access":  vida corta, se usa para llamar a la API.
 *  - "refresh": vida larga, solo sirve para obtener un nuevo access token.
 */
@Service
public class JwtService {

    /** Claim que indica el tipo de token. */
    public static final String TYPE_CLAIM = "type";

    /** Token de acceso (corta vida). */
    public static final String TOKEN_TYPE_ACCESS = "access";

    /** Token de refresco (larga vida). */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /**
     * Clave secreta para firmar los tokens (debe tener al menos 32 bytes para HS256).
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Vigencia del access token en milisegundos.
     */
    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Vigencia del refresh token en milisegundos.
     */
    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un access token con el username como subject y el rol como claim.
     * @param username nombre de usuario.
     * @param role rol del usuario (ej: MEDICO, USER).
     * @return token JWT compacto.
     */
    public String generateToken(String username, String role) {
        return buildToken(username, role, expirationMs, TOKEN_TYPE_ACCESS);
    }

    /**
     * Genera un refresh token con el mismo payload, pero de vida más larga.
     */
    public String generateRefreshToken(String username, String role) {
        return buildToken(username, role, refreshExpirationMs, TOKEN_TYPE_REFRESH);
    }

    private String buildToken(String username, String role, long ttlMs, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);

        return Jwts.builder()
                .subject(username)
                .claims(Map.of("role", role, TYPE_CLAIM, type))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Extrae el username (claim "sub") del token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrae el tipo de token (claim "type"): "access" o "refresh".
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TYPE_CLAIM, String.class));
    }

    /**
     * Extrae un claim particular del token.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Valida que el token sea firmado, no esté vencido y pertenezca al usuario indicado.
     * Devuelve false (y no lanza excepción) si el token está vencido o es inválido.
     */
    public boolean isTokenValid(String token, String username) {
        try {
            final Claims claims = extractAllClaims(token);
            return claims.getSubject().equals(username)
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Igual que {@link #isTokenValid(String, String)} pero exige que el token
     * sea del tipo indicado (por ejemplo: un refresh token no sirve como access token).
     */
    public boolean isTokenValid(String token, String username, String expectedType) {
        try {
            final Claims claims = extractAllClaims(token);
            return claims.getSubject().equals(username)
                    && claims.getExpiration().after(new Date())
                    && expectedType.equals(claims.get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
