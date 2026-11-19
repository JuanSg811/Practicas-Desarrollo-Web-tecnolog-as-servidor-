package mx.edu.backendacademico.controller;

// Traduce HTTP a llamadas de aplicación y construye respuestas mediante DTO.

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import mx.edu.backendacademico.dto.ActualizarAlumnoRequest;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.dto.CrearAlumnoRequest;
import mx.edu.backendacademico.service.AlumnoService;
import mx.edu.backendacademico.service.CreacionIdempotenteService;
import mx.edu.backendacademico.exception.ClaveIdempotenciaInvalidaException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alumnos")
public class AlumnoController {
    private final AlumnoService service;
    private final ObjectProvider<CreacionIdempotenteService> idempotencia;
    public AlumnoController(AlumnoService service,
            ObjectProvider<CreacionIdempotenteService> idempotencia) {
        this.service = service;
        this.idempotencia = idempotencia;
    }

    // GET consulta sin solicitar cambios de estado de negocio.
    @GetMapping
    public List<AlumnoResponse> listar() { return service.listar().stream().map(AlumnoMapper::toResponse).toList(); }

    // GET consulta sin solicitar cambios de estado de negocio.
    @GetMapping("/{id}")
    public AlumnoResponse buscar(@PathVariable Long id) { return AlumnoMapper.toResponse(service.buscar(id)); }

    // El servicio asigna identidad; ResponseEntity publica 201 y Location.
    @PostMapping
    public ResponseEntity<AlumnoResponse> crear(@RequestBody @Valid CrearAlumnoRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String clave,
            Authentication principal) {
        if (clave == null) {
            // Compatibilidad con las solicitudes anteriores de U11.
            var creado = service.crear(request.matricula(), request.nombre(), request.correo());
            return ResponseEntity.created(URI.create("/api/v1/alumnos/" + creado.id()))
                .body(AlumnoMapper.toResponse(creado));
        }
        if (!clave.matches("[A-Za-z0-9_-]{8,128}")) {
            throw new ClaveIdempotenciaInvalidaException();
        }
        var servicio = idempotencia.getIfAvailable();
        if (servicio == null) {
            throw new IllegalStateException("Idempotencia requiere el perfil jpa y PostgreSQL");
        }
        var resultado = servicio.crear(principal.getName(), clave, request);
        return ResponseEntity.created(URI.create("/api/v1/alumnos/" + resultado.alumno().id()))
            .header("Idempotency-Replayed", Boolean.toString(resultado.repeticion()))
            .body(resultado.alumno());
    }

    // PUT sustituye todos los campos de la representación editable.
    @PutMapping("/{id}")
    public AlumnoResponse actualizar(@PathVariable Long id, @RequestBody @Valid ActualizarAlumnoRequest request) {
        return AlumnoMapper.toResponse(service.actualizar(id, request.nombre(), request.correo()));
    }

    // DELETE exitoso termina con 204, que no permite cuerpo de respuesta.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}