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
