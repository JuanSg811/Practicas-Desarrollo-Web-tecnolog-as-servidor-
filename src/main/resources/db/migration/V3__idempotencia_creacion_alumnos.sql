-- La restricción única PostgreSQL serializa las claves simultáneas del mismo usuario y ruta.
-- La fila se inserta y completa dentro de la misma transacción que alumnos.
CREATE TABLE creaciones_idempotentes (
    principal VARCHAR(160) NOT NULL,
    operacion VARCHAR(100) NOT NULL,
    clave VARCHAR(128) NOT NULL,
    huella CHAR(64) NOT NULL,
    alumno_id BIGINT REFERENCES alumnos(id) ON DELETE SET NULL,
    matricula VARCHAR(30),
    nombre VARCHAR(120),
    correo VARCHAR(160),
    estatus VARCHAR(20),
    codigo_http INTEGER,
    location VARCHAR(256),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotencia_principal_operacion_clave UNIQUE (principal, operacion, clave),
    CONSTRAINT ck_respuesta_completa CHECK (
        (codigo_http IS NULL AND matricula IS NULL AND nombre IS NULL AND correo IS NULL AND estatus IS NULL)
        OR (codigo_http = 201 AND matricula IS NOT NULL AND nombre IS NOT NULL
            AND correo IS NOT NULL AND estatus IS NOT NULL AND location IS NOT NULL)
    )
);
