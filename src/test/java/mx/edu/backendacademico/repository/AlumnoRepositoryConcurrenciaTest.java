
package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;

class AlumnoRepositoryConcurrenciaTest {

    @Test
    void dosTareasConLaMismaMatriculaProducenUnGuardadoYUnConflicto()
            throws Exception {

        var repository = new AlumnoRepositoryEnMemoria();

        // Ambas tareas esperan hasta que la prueba las libere.
        var preparadas = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);

        // La prueba espera a que ambas tareas terminen.
        var finalizacion = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            var tareaA = crearTarea(
                repository, "Ana", preparadas, inicio, finalizacion
            );

            var tareaB = crearTarea(
                repository, "Luis", preparadas, inicio, finalizacion
            );

            Future<String> resultadoA = executor.submit(tareaA);
            Future<String> resultadoB = executor.submit(tareaB);

            // No liberar las tareas hasta que ambas estén preparadas.
            assertTrue(
                preparadas.await(5, TimeUnit.SECONDS),
                "Las dos tareas debieron quedar preparadas"
            );

            // Las dos tareas pueden intentar guardar desde este momento.
            inicio.countDown();

            assertTrue(
                finalizacion.await(5, TimeUnit.SECONDS),
                "Las tareas debieron finalizar"
            );

            // get() también propaga cualquier error inesperado de las tareas.
            String resultado1 = resultadoA.get();
            String resultado2 = resultadoB.get();

            long guardados = java.util.List.of(resultado1, resultado2)
                .stream()
                .filter("GUARDADO"::equals)
                .count();

            long conflictos = java.util.List.of(resultado1, resultado2)
                .stream()
                .filter("CONFLICTO"::equals)
                .count();

            // Evidencia del comportamiento esperado.
            assertEquals(1, guardados, "Debe existir un guardado exitoso");
            assertEquals(1, conflictos, "Debe existir un conflicto");
            assertEquals(
                1,
                repository.buscarTodos().size(),
                "Debe quedar exactamente un alumno"
            );

            assertEquals(
                java.util.List.of("GUARDADO", "CONFLICTO").stream()
                    .sorted().toList(),
                java.util.List.of(resultado1, resultado2).stream()
                    .sorted().toList()
            );
        }
    }

    private java.util.concurrent.Callable<String> crearTarea(
            AlumnoRepository repository,
            String nombre,
            CountDownLatch preparadas,
            CountDownLatch inicio,
            CountDownLatch finalizacion) {

        return () -> {
            preparadas.countDown();

            try {
                // Barrera: espera la señal de inicio, sin usar sleep().
                inicio.await();

                var alumno = new Alumno(
                    null,
                    "A001",
                    nombre,
                    nombre.toLowerCase() + "@u.mx",
                    EstatusAlumno.ACTIVO
                );

                repository.guardar(alumno);
                return "GUARDADO";

            } catch (MatriculaDuplicadaException e) {
                return "CONFLICTO";

            } finally {
                finalizacion.countDown();
            }
        };
    }
}
