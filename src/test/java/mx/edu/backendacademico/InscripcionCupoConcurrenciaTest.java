
package mx.edu.backendacademico;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.AlumnoEntity;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.service.InscripcionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class InscripcionCupoConcurrenciaTest {

    @Autowired
    private InscripcionService servicio;

    @Autowired
    private AlumnoSpringDataRepository alumnos;

    @Autowired
    private InscripcionRepository inscripciones;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dosAlumnosCompitenPorUnSoloLugar() throws Exception {

        // 1. Preparar una materia con cupo de uno.
        Long materiaId = jdbc.queryForObject(
            "SELECT id FROM materias WHERE clave = 'MAT101'",
            Long.class
        );

        assertNotNull(materiaId);

        // Este laboratorio utiliza una base de datos exclusiva.
        jdbc.update(
            "DELETE FROM inscripciones WHERE materia_id = ?",
            materiaId
        );

        jdbc.update(
            "UPDATE materias SET cupo = 1 WHERE id = ?",
            materiaId
        );

        // 2. Crear dos alumnos diferentes y activos.
        String sufijo = Long.toString(System.nanoTime());

        AlumnoEntity alumnoA = alumnos.saveAndFlush(
            new AlumnoEntity(new Alumno(
                null,
                "CONCA" + sufijo,
                "Alumno Concurrencia A",
                "conca" + sufijo + "@example.com",
                EstatusAlumno.ACTIVO
            ))
        );

        AlumnoEntity alumnoB = alumnos.saveAndFlush(
            new AlumnoEntity(new Alumno(
                null,
                "CONCB" + sufijo,
                "Alumno Concurrencia B",
                "concb" + sufijo + "@example.com",
                EstatusAlumno.ACTIVO
            ))
        );

        Long idA = alumnoA.getId();
        Long idB = alumnoB.getId();

        // 3. Barreras de coordinación.
        CountDownLatch preparadas = new CountDownLatch(2);
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch finalizadas = new CountDownLatch(2);

        // 4. Crear exactamente dos tareas Java.
        try (ExecutorService executor =
                 Executors.newFixedThreadPool(2)) {

            Callable<String> tareaA = crearTarea(
                idA, materiaId, "A",
                preparadas, inicio, finalizadas
            );

            Callable<String> tareaB = crearTarea(
                idB, materiaId, "B",
                preparadas, inicio, finalizadas
            );

            Future<String> resultadoA = executor.submit(tareaA);
            Future<String> resultadoB = executor.submit(tareaB);

            // Esperar hasta que los dos hilos estén preparados.
            assertTrue(
                preparadas.await(10, TimeUnit.SECONDS),
                "Los dos hilos deben estar preparados"
            );

            // Liberar las dos tareas simultáneamente.
            inicio.countDown();

            assertTrue(
                finalizadas.await(60, TimeUnit.SECONDS),
                "Las dos tareas deben terminar"
            );

            String respuestaA =
                resultadoA.get(10, TimeUnit.SECONDS);

            String respuestaB =
                resultadoB.get(10, TimeUnit.SECONDS);

            List<String> resultados =
                List.of(respuestaA, respuestaB);

            System.out.println("Resultado A: " + respuestaA);
            System.out.println("Resultado B: " + respuestaB);

            // 5. Verificar un éxito y un rechazo por cupo.
            assertEquals(
                1,
                resultados.stream()
                    .filter(r -> r.startsWith("INSCRITO:"))
                    .count()
            );

            assertEquals(
                1,
                resultados.stream()
                    .filter("SIN_CUPO"::equals)
                    .count()
            );

            // 6. Verificar lo realmente persistido.
            assertEquals(
                1,
                inscripciones.countByMateriaId(materiaId),
                "Debe persistir exactamente una inscripción"
            );
        }
    }

    private Callable<String> crearTarea(
            Long alumnoId,
            Long materiaId,
            String nombre,
            CountDownLatch preparadas,
            CountDownLatch inicio,
            CountDownLatch finalizadas) {

        return () -> {
            preparadas.countDown();

            try {
                // Ambas tareas esperan la misma señal.
                inicio.await();

                // Invocación del bean REAL de Spring.
                Long id = servicio.inscribir(
                    alumnoId,
                    materiaId
                );

                return "INSCRITO:" + id;

            } catch (OperacionAcademicaException e) {

                return e.getCodigo();

            } finally {
                finalizadas.countDown();
            }
        };
    }
}
