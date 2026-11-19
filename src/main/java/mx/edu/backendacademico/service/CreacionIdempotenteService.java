package mx.edu.backendacademico.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.dto.CrearAlumnoRequest;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.ClaveIdempotenciaConflictoException;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia y arbitraje de la clave en PostgreSQL, nunca en campos singleton. */
@Service
@Profile("jpa")
public class CreacionIdempotenteService {
    private static final String RUTA = "POST /api/v1/alumnos";
    private final JdbcTemplate jdbc;
    private final AlumnoService alumnos;

    public CreacionIdempotenteService(JdbcTemplate jdbc, AlumnoService alumnos) {
        this.jdbc = jdbc;
        this.alumnos = alumnos;
    }

    public record Resultado(AlumnoResponse alumno, boolean repeticion) {}

    /** Una sola transacción incluye la reserva única, la creación y la respuesta almacenada. */
    @Transactional
    public Resultado crear(String principal, String clave, CrearAlumnoRequest request) {
        String huella = huella(request);
        // ON CONFLICT en PostgreSQL espera la resolución de otra transacción no confirmada
        // con la misma clave única. Así, la petición concurrente nunca crea un segundo alumno.
        int reservado = jdbc.update("""
            INSERT INTO creaciones_idempotentes
                (principal, operacion, clave, huella)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (principal, operacion, clave) DO NOTHING
            """, principal, RUTA, clave, huella);
        if (reservado == 1) {
            var alumno = AlumnoMapper.toResponse(
                alumnos.crear(request.matricula(), request.nombre(), request.correo()));
            int actualizados = jdbc.update("""
                UPDATE creaciones_idempotentes SET alumno_id=?, matricula=?, nombre=?,
                    correo=?, estatus=?, codigo_http=201, location=?
                WHERE principal=? AND operacion=? AND clave=?
                """, alumno.id(), alumno.matricula(), alumno.nombre(), alumno.correo(),
                alumno.estatus().name(), "/api/v1/alumnos/" + alumno.id(),
                principal, RUTA, clave);
            if (actualizados != 1) throw new IllegalStateException("No se pudo guardar la respuesta idempotente");
            return new Resultado(alumno, false);
        }

        // El registro sólo se hace visible después del COMMIT de la transacción ganadora.
        return jdbc.queryForObject("""
            SELECT huella, alumno_id, matricula, nombre, correo, estatus, codigo_http
            FROM creaciones_idempotentes
            WHERE principal=? AND operacion=? AND clave=?
            """, (rs, index) -> {
                if (!huella.equals(rs.getString("huella"))) {
                    throw new ClaveIdempotenciaConflictoException();
                }
                if (rs.getInt("codigo_http") != 201 || rs.getObject("alumno_id") == null) {
                    throw new IllegalStateException("Respuesta idempotente incompleta");
                }
                var alumno = new AlumnoResponse(rs.getLong("alumno_id"),
                    rs.getString("matricula"), rs.getString("nombre"),
                    rs.getString("correo"), EstatusAlumno.valueOf(rs.getString("estatus")));
                return new Resultado(alumno, true);
            }, principal, RUTA, clave);
    }

    /** Canonización estructural con longitudes, evitando ambigüedades de concatenación. */
    static String huella(CrearAlumnoRequest request) {
        try {
            MessageDigest dig = MessageDigest.getInstance("SHA-256");
            for (String campo : new String[] {request.matricula(), request.nombre(), request.correo()}) {
                byte[] bytes = campo.getBytes(StandardCharsets.UTF_8);
                dig.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                dig.update(bytes);
            }
            return HexFormat.of().formatHex(dig.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
