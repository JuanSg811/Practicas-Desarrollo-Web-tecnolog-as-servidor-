package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
            new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }
    @Test
    void reactivarDesdeBajaConservaElOriginal() {
        var baja = new Alumno(
            1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.BAJA
        );

        var reactivado = baja.reactivar();

        // Se construyó una instancia diferente.
        assertNotSame(baja, reactivado);

        // Los datos académicos se conservan.
        assertEquals(baja.id(), reactivado.id());   
        assertEquals(baja.matricula(), reactivado.matricula());
        assertEquals(baja.nombre(), reactivado.nombre());
        assertEquals(baja.correo(), reactivado.correo());

        // El nuevo valor queda activo.
        assertEquals(EstatusAlumno.ACTIVO, reactivado.estatus());

        // El original permanece dado de baja.
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
    }

    @Test
    void mismoIdConCorreoDistintoNoEsIgualComoRecord() {
        var primero = new Alumno(
        7L, "A007", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO
        );

        var segundo = new Alumno(
            7L, "A007", "Ada", "ada.personal@u.mx",
            EstatusAlumno.ACTIVO
        );

        // Tienen la misma identidad académica.
        assertEquals(primero.id(), segundo.id());

        // Son instancias diferentes.
        assertNotSame(primero, segundo);

        // Los records comparan todos sus componentes.
        assertNotEquals(primero, segundo);
    }
}