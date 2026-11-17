"""Ejecuta pruebas de mutación U9 y restaura SIEMPRE los archivos originales.
Requisitos: Maven o mvnw, Java 21+, Docker funcionando para PostgreSQL Testcontainers.
Ejecutar desde la raíz del proyecto: python scripts/demostrar_mutaciones.py
"""
from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = ROOT / 'src/main/java/mx/edu/backendacademico/controller/AlumnoController.java'
MIGRATION = ROOT / 'src/main/resources/db/migration/V1__crear_alumno.sql'
HTTP_ORIGINAL = 'ResponseEntity.created(URI.create("/api/v1/alumnos/" + creado.id()))'
HTTP_MUTATION = 'ResponseEntity.ok()'
SQL_ORIGINAL = 'CONSTRAINT uk_alumnos_matricula UNIQUE (matricula),'
SQL_MUTATION = '-- MUTACIÓN: se retiró temporalmente la restricción UNIQUE,'


def maven_command(test):
    if os.name == 'nt':
        launcher = 'mvnw.cmd' if (ROOT / 'mvnw.cmd').exists() else 'mvn'
    else:
        launcher = './mvnw' if (ROOT / 'mvnw').exists() else 'mvn'
    return [launcher, f'-Dtest={test}', 'test']


def run_case(title, path, original, mutation, test):
    previous = path.read_text(encoding='utf-8')
    if previous.count(original) != 1:
        raise RuntimeError(f'No se encontró una sola coincidencia de la implementación original en {path}')
    print(f'\n=== {title}: mutación temporal ===', flush=True)
    try:
        path.write_text(previous.replace(original, mutation), encoding='utf-8')
        result = subprocess.run(maven_command(test), cwd=ROOT, check=False)
        if result.returncode == 0:
            raise RuntimeError('La prueba NO detectó la mutación: revisar aserciones y entorno')
        print('El proceso de prueba devolvió código no cero. Revisar que el fallo sea la aserción esperada, no infraestructura.', flush=True)
    finally:
        path.write_text(previous, encoding='utf-8')
        print('Implementación restaurada:', path.relative_to(ROOT), flush=True)


def main():
    run_case('HTTP (esperado 201, mutado 200)', CONTROLLER,
             HTTP_ORIGINAL, HTTP_MUTATION,
             'AlumnoControllerTest#crearDevuelve201YLocationDelAlumnoCreado')
    run_case('PostgreSQL (sin UNIQUE; no hay excepción 23505)', MIGRATION,
             SQL_ORIGINAL, SQL_MUTATION,
             'PostgresIntegrationTest#postgresRechazaMatriculaDuplicadaPorRestriccionUnica')
    print('\n=== Verificación final, con las implementaciones restauradas ===', flush=True)
    rc = subprocess.run(maven_command('AlumnoControllerTest,PostgresIntegrationTest'), cwd=ROOT).returncode
    sys.exit(rc)


if __name__ == '__main__':
    main()
