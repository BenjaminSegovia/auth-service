package cl.duoc.authservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Operación recibida con datos válidos pero que el negocio no permite,
 * por ejemplo eliminar la cuenta con la que se está sesión iniciada
 * o repetir la contraseña que ya se está usando.
 *
 * Se responde con 400 Bad Request y el mensaje que explica el motivo.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String message) {
        super(message);
    }
}
