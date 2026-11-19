package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.repository.jpa.AlumnoSpringDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Especificación primero: ambas peticiones atraviesan el endpoint y PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdempotenciaAlumnosTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AlumnoSpringDataRepository alumnos;

    private org.springframework.test.web.servlet.MvcResult crear(String clave, String json) throws Exception {
        return mvc.perform(post("/api/v1/alumnos")
                .with(httpBasic("docente", "docente123"))
                .with(csrf())
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andReturn();
    }

    private String datos(String matricula, String nombre) {
        return "{\"matricula\":\"" + matricula + "\",\"nombre\":\"" + nombre
            + "\",\"correo\":\"pruebas@u.mx\"}";
    }

    @Test
    void dosSolicitudesSimultaneasConMismaClaveCreanUnSoloAlumno() throws Exception {
        String clave = "simultanea_" + UUID.randomUUID().toString().replace("-", "");
        String matricula = "I" + UUID.randomUUID().toString().replace("-", "").substring(0, 17).toUpperCase();
        String cuerpo = datos(matricula, "Ada Lovelace");
        CountDownLatch inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var solicitud = (java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult>) () -> {
                assertTrue(inicio.await(10, TimeUnit.SECONDS));
                return crear(clave, cuerpo);
            };
            var primera = ejecutor.submit(solicitud);
            var segunda = ejecutor.submit(solicitud);
            inicio.countDown();
            var a = primera.get(30, TimeUnit.SECONDS).getResponse();
            var b = segunda.get(30, TimeUnit.SECONDS).getResponse();
            assertEquals(201, a.getStatus());
            assertEquals(201, b.getStatus());
            assertEquals(a.getHeader("Location"), b.getHeader("Location"));
            assertEquals(a.getContentAsString(), b.getContentAsString());
            assertNotEquals(a.getHeader("Idempotency-Replayed"), b.getHeader("Idempotency-Replayed"));
            assertEquals(1, alumnos.findAll().stream()
                .filter(alumno -> matricula.equals(alumno.toDomain().matricula())).count());
        }
    }

    @Test
    void igualClaveConContenidoDistintoDevuelve409SinCrearOtroAlumno() throws Exception {
        String clave = "contenido_" + UUID.randomUUID().toString().replace("-", "");
        String matricula = "I" + UUID.randomUUID().toString().replace("-", "").substring(0, 17).toUpperCase();
        var primera = crear(clave, datos(matricula, "Ada Lovelace")).getResponse();
        assertEquals(201, primera.getStatus());
        var distinta = crear(clave, datos(matricula + "B", "Grace Hopper")).getResponse();
        assertEquals(409, distinta.getStatus());
        assertTrue(distinta.getContentAsString().contains("CLAVE_IDEMPOTENCIA_REUTILIZADA"));
        assertEquals(1, alumnos.findAll().stream()
            .filter(alumno -> alumno.toDomain().matricula().startsWith(matricula)).count());
    }

    @Test
    void reintentoPosteriorConserva201LocationYCuerpo() throws Exception {
        String clave = "reintento_" + UUID.randomUUID().toString().replace("-", "");
        String matricula = "I" + UUID.randomUUID().toString().replace("-", "").substring(0, 17).toUpperCase();
        String json = datos(matricula, "Katherine Johnson");
        var a = crear(clave, json).getResponse();
        var b = crear(clave, json).getResponse();
        assertEquals(201, a.getStatus());
        assertEquals(201, b.getStatus());
        assertEquals("false", a.getHeader("Idempotency-Replayed"));
        assertEquals("true", b.getHeader("Idempotency-Replayed"));
        assertEquals(a.getHeader("Location"), b.getHeader("Location"));
        assertEquals(a.getContentAsString(), b.getContentAsString());
    }
}
