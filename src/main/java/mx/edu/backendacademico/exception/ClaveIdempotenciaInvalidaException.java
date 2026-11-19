package mx.edu.backendacademico.exception;

public class ClaveIdempotenciaInvalidaException extends RuntimeException {
    public ClaveIdempotenciaInvalidaException() {
        super("Idempotency-Key debe tener de 8 a 128 caracteres [A-Za-z0-9_-]");
    }
}
