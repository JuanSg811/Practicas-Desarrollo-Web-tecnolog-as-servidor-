# PowerShell 5.1+ | Ejecutar DESDE la raiz del proyecto: .\scripts\verificar-u12.ps1
# No necesita IDE ni extension REST Client. Requiere curl.exe (Windows 10/11).
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$evid = Join-Path (Get-Location) 'evidencias-u12'
New-Item -ItemType Directory -Force -Path $evid | Out-Null
$cookie = Join-Path $evid 'cookies.txt'
$run = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
$matricula = 'U12' + $run
$adminAuth='admin:admin123'; $docAuth='docente:docente123'; $alumnoAuth='alumno:alumno123'
function Solicitar([string]$nombre,[string]$metodo,[string]$ruta,[int]$esperado,[string[]]$opciones=@()) {
    $respuesta = Join-Path $evid ($nombre+'.json')
    $argumentos = @('-sS','-X',$metodo,'-o',$respuesta,'-w','%{http_code}') + $opciones + @($base + $ruta)
    $codigo = & curl.exe @argumentos
    if ($LASTEXITCODE -ne 0) { throw "Error de conexion en $nombre (curl $LASTEXITCODE)." }
    $codigo = ($codigo | Select-Object -Last 1).Trim()
    Write-Host "[$nombre] HTTP $codigo (esperado $esperado)"
    if ($codigo -ne [string]$esperado) { Get-Content $respuesta; throw "Resultado inesperado en $nombre" }
    return (Get-Content -Raw $respuesta)
}
# El token CSRF se extrae con la misma cookie que usaremos en los POST.
$csrfJson = Solicitar '00-csrf' 'GET' '/api/v1/csrf' 200 @('-u',$docAuth,'-c',$cookie,'-b',$cookie)
$csrf = ($csrfJson | ConvertFrom-Json).token
if (-not $csrf) { throw 'No se recibio token CSRF' }
$headers = @('-u',$docAuth,'-b',$cookie,'-c',$cookie,'-H',('X-CSRF-TOKEN: '+$csrf),'-H','Content-Type: application/json')
$body = @{matricula=$matricula;nombre='Ada Lovelace';correo=('ada'+$run+'@example.com')} | ConvertTo-Json -Compress
$creacion = Solicitar '01-exito-201' 'POST' '/api/v1/alumnos' 201 ($headers + @('--data-raw',$body))
$id = ($creacion | ConvertFrom-Json).id
if (-not $id) { throw 'La creacion no devolvio id' }
$invalido = '{"matricula":"","nombre":"A","correo":"correo-invalido"}'
Solicitar '02-validacion-400' 'POST' '/api/v1/alumnos' 400 ($headers + @('--data-raw',$invalido)) | Out-Null
Solicitar '03-ausencia-404' 'GET' '/api/v1/alumnos/9223372036854775807' 404 @('-u',$alumnoAuth) | Out-Null
Solicitar '04-conflicto-409' 'POST' '/api/v1/alumnos' 409 ($headers + @('--data-raw',$body)) | Out-Null
Solicitar '05-no-autenticado-401' 'GET' ('/api/v1/alumnos/'+$id) 401 | Out-Null
Solicitar '06-rol-insuficiente-403' 'GET' '/actuator/metrics' 403 @('-u',$alumnoAuth) | Out-Null
Solicitar '07-metricas-admin-200' 'GET' '/actuator/metrics' 200 @('-u',$adminAuth) | Out-Null
Solicitar '08-readiness-up-200' 'GET' '/actuator/health/readiness' 200 | Out-Null
Solicitar '09-alumno-consulta-200' 'GET' ('/api/v1/alumnos/'+$id) 200 @('-u',$alumnoAuth) | Out-Null
@("Fecha: $(Get-Date -Format o)","Matricula: $matricula","Alumno ID: $id",'Verificaciones: 201 400 404 409 401 403 200 200 200 OK') | Set-Content (Join-Path $evid 'RESULTADOS.txt')
Write-Host "EVIDENCIAS GUARDADAS EN: $evid" -ForegroundColor Green
Write-Host 'Para la prueba de falla controlada consulta el README; no detener Postgres antes de estas pruebas.'
