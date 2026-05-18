$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $deployDir
$envFile = Join-Path $deployDir ".env"

if (-not (Test-Path $envFile)) {
    Write-Host "Falta $envFile. Copiá .env.example a .env y completá las rutas si hace falta." -ForegroundColor Red
    exit 1
}

$envVars = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }

    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) {
        $envVars[$parts[0].Trim()] = $parts[1].Trim()
    }
}

$gitBranch = if ($envVars.ContainsKey("GIT_BRANCH") -and $envVars["GIT_BRANCH"]) { $envVars["GIT_BRANCH"] } else { "main" }
$composeFile = Join-Path $deployDir "docker-compose.yml"

if (-not (Test-Path (Join-Path $repoRoot ".git"))) {
    Write-Host "No se encontró un repositorio git en $repoRoot" -ForegroundColor Red
    exit 1
}

# --- Validación de Docker y de los directorios de host (bind mounts) ---
# Verifica que Docker Desktop esté corriendo
docker info --format "{{.ServerVersion}}" 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker no responde. Asegurate de que Docker Desktop esté corriendo." -ForegroundColor Red
    exit 1
}

function Resolve-HostDir {
    param([string]$path)
    if ([System.IO.Path]::IsPathRooted($path)) { return $path }
    return [System.IO.Path]::GetFullPath((Join-Path $deployDir $path))
}

# Defaults espejan a deploy/docker-compose.yml
$hostDirs = @(
    @{ Var = "SECRETS_DIR";   Default = "C:/ProgramData/SuperMaster/secrets"; ReadOnly = $false },
    @{ Var = "IMAGENES_DIR";  Default = "D:/Doc. Compartidos/IMAGENES";       ReadOnly = $true  },
    @{ Var = "CATALOGOS_DIR"; Default = "G:/Mi unidad";                       ReadOnly = $false },
    @{ Var = "LOGS_DIR";      Default = "../logs";                            ReadOnly = $false },
    @{ Var = "BACKUPS_DIR";   Default = "../backups";                         ReadOnly = $false }
)

foreach ($dir in $hostDirs) {
    $rawPath = if ($envVars.ContainsKey($dir.Var) -and $envVars[$dir.Var]) { $envVars[$dir.Var] } else { $dir.Default }
    $absPath = Resolve-HostDir $rawPath

    if (-not (Test-Path -LiteralPath $absPath)) {
        Write-Host "Creando directorio: $absPath" -ForegroundColor Yellow
        try {
            New-Item -ItemType Directory -Force -Path $absPath | Out-Null
        } catch {
            Write-Host "No se pudo crear $absPath ($($dir.Var)). Creá la carpeta manualmente y volvé a intentar." -ForegroundColor Red
            exit 1
        }
    }

    # Test de bind mount: confirma que Docker puede leer el directorio del host
    Write-Host "Verificando acceso de Docker a $absPath..." -ForegroundColor Cyan
    $mountSpec = "${absPath}:/test"
    if ($dir.ReadOnly) { $mountSpec = "${absPath}:/test:ro" }
    docker run --rm -v $mountSpec alpine sh -c "ls /test > /dev/null 2>&1" *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ERROR: Docker no puede acceder a $absPath (variable $($dir.Var))." -ForegroundColor Red
        Write-Host "Verificá en Docker Desktop -> Settings -> Resources -> File sharing que el drive esté habilitado." -ForegroundColor Red
        Write-Host "Si es un drive virtual de Google Drive (G:), puede no funcionar como bind mount;" -ForegroundColor Red
        Write-Host "considerá usar una carpeta local y sincronizarla aparte." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Actualizando repo supermaster ($gitBranch)..." -ForegroundColor Cyan
git -C $repoRoot pull origin $gitBranch
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fallo git pull del monorepo." -ForegroundColor Red
    exit 1
}

Write-Host "Deteniendo contenedores actuales..." -ForegroundColor Cyan
docker compose --env-file $envFile -f $composeFile down

# Write-Host "Limpiando cache de build..." -ForegroundColor Cyan
# docker builder prune -f

Write-Host "Reconstruyendo y levantando contenedores..." -ForegroundColor Cyan
docker compose --env-file $envFile -f $composeFile up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fallo docker compose up -d --build." -ForegroundColor Red
    exit 1
}

Write-Host "Limpiando imagenes Docker sin uso..." -ForegroundColor Cyan
docker image prune -f

Write-Host "Deploy terminado correctamente." -ForegroundColor Green
