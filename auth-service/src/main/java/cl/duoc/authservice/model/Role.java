package cl.duoc.authservice.model;

/**
 * Roles disponibles en RecetaYa.
 *
 * El registro público siempre entrega {@link #USER}; los roles de negocio
 * ({@link #MEDICO}, {@link #FARMACEUTICO}) y {@link #ADMIN} se asignan desde
 * PUT /auth/users/{username}/role, que exige estar autenticado como ADMIN.
 */
public enum Role {

    /** Rol por defecto de todo usuario que se auto-registra. */
    USER,

    /** Rol de negocio: profesional de la salud. */
    MEDICO,

    /** Rol de negocio: quien dispensa la receta. */
    FARMACEUTICO,

    /** Puede asignar roles a otros usuarios. Lo obtiene el primer usuario registrado. */
    ADMIN
}
