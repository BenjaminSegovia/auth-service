package cl.duoc.authservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usuario del sistema: una fila de la tabla <b>usuarios</b>.
 *
 * El mapeo JPA garantiza la integridad de los datos:
 * <ul>
 *     <li>{@code @Table(name = "usuarios")} → nombra la tabla en PostgreSQL.</li>
 *     <li>{@code @Column(unique = true)} → nadie puede repetir username (lo explota el 409 del registro).</li>
 *     <li>{@code @Column(nullable = false)} → ninguna columna admite null.</li>
 *     <li>{@code @Enumerated(EnumType.STRING)} → el rol se guarda como texto
 *     ("ADMIN", "USER"...), no como número, así es legible en la tabla
 *     y no se rompe si se reordenan las constantes del enum.</li>
 * </ul>
 *
 * La contraseña se almacena <b>hasheada con BCrypt</b> (60 caracteres), nunca en claro.
 *
 * Esta entidad no tiene relaciones JPA: la relación {@code @OneToMany}
 * (receta → detalles) vive en <b>receta-service</b>.
 */
@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    /** Clave primaria autonumérica (BIGSERIAL en PostgreSQL). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre de usuario: obligatorio y único en todo el sistema. */
    @Column(nullable = false, unique = true)
    private String username;

    /** Contraseña cifrada con BCrypt; jamás se guarda ni se devuelve en claro. */
    @Column(nullable = false)
    private String password;

    /** Nombre completo que entregó el usuario al registrarse. */
    @Column(nullable = false)
    private String nombreCompleto;

    /** Rol vigente, guardado como texto: USER, MEDICO, FARMACEUTICO o ADMIN. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
}
