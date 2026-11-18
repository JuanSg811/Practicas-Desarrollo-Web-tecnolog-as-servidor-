package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.exception.ExpedienteAjenoException;
import mx.edu.backendacademico.exception.GlobalExceptionHandler;
import mx.edu.backendacademico.service.ExpedienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpedienteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ExpedienteSeguridadTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExpedienteService servicio;

    private final Alumno ada = new Alumno(10L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);

    @Test void sinSesionRetorna401() throws Exception {
        mvc.perform(get("/api/v1/alumnos/mi-expediente"))
           .andExpect(status().isUnauthorized());
    }

    @Test void propietarioObtieneSuExpedienteDesdePrincipal() throws Exception {
        when(servicio.miExpediente(any(Authentication.class))).thenReturn(ada);
        mvc.perform(get("/api/v1/alumnos/mi-expediente")
                .with(httpBasic("alumno", "alumno123")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
        verify(servicio).miExpediente(argThat(auth -> "alumno".equals(auth.getName())));
    }

    @Test void ajenoRecibe403AlConsultarPorId() throws Exception {
        when(servicio.consultarExpediente(eq(10L), any(Authentication.class)))
           .thenThrow(new ExpedienteAjenoException());
        mvc.perform(get("/api/v1/alumnos/10/expediente")
                .with(httpBasic("alumno2", "alumno2123")))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.codigo").value("EXPEDIENTE_AJENO"));
    }

    @Test void administradorPuedeConsultarPorId() throws Exception {
        when(servicio.consultarExpediente(eq(10L), any(Authentication.class))).thenReturn(ada);
        mvc.perform(get("/api/v1/alumnos/10/expediente")
                .with(httpBasic("admin", "admin123")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
        verify(servicio).consultarExpediente(eq(10L),
                argThat(auth -> "admin".equals(auth.getName())));
    }

    @Test void alumnoNoPuedeSaltarLaReglaConRutaAntigua() throws Exception {
        mvc.perform(get("/api/v1/alumnos/10").with(httpBasic("alumno", "alumno123")))
           .andExpect(status().isForbidden());
    }

    @Test void usernameEnQueryNoSustituyeAlPrincipal() throws Exception {
        when(servicio.miExpediente(any(Authentication.class))).thenReturn(ada);
        mvc.perform(get("/api/v1/alumnos/mi-expediente?username=admin")
                .with(httpBasic("alumno", "alumno123")))
           .andExpect(status().isOk());
        verify(servicio).miExpediente(argThat(auth -> "alumno".equals(auth.getName())));
    }
}
