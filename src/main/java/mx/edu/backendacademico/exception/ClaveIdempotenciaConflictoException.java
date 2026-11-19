package mx.edu.backendacademico.exception;

public class ClaveIdempotenciaConflictoException extends RuntimeException {
    public ClaveIdempotenciaConflictoException() {
        super("La clave Idempotency-Key ya se usó con otro contenido para esta operación");
    }
}
