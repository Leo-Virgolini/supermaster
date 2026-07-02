# ==============================================================================
# update-remote.ps1 — actualizar SuperMaster en el servidor remoto (vía SSH)
# ==============================================================================
# Desde tu PC Windows, se conecta por SSH al servidor Linux y ejecuta:
#   git pull  +  docker compose up -d --build  +  image prune
# usando docker-compose.prod.yml.
#
# NO hace git push: se asume que ya pusheaste tus commits a la rama remota
# (o que el server ya tiene el código que querés desplegar).
#
# NOTA: este deploy corre por FUERA de Coolify (compose manual). El proxy de
# Coolify no enruta contenedores levantados así. Ver README.prod.md.
#
# Configuración: leer de deploy/.env.deploy (copiá .env.deploy.example a
# .env.deploy y completá). Ese archivo NO va al repo (gitignore).
# ==============================================================================

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $deployDir ".env.deploy"

if (-not (Test-Path $envFile)) {
    Write-Host "Falta $envFile. Copiá .env.deploy.example a .env.deploy y completá los datos del servidor." -ForegroundColor Red
    exit 1
}

# --- Parsear .env.deploy (mismo estilo que update-super-master.ps1) ---
$cfg = @{}
Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }
    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) {
        $cfg[$parts[0].Trim()] = $parts[1].Trim()
    }
}

function Get-Cfg {
    param([string]$key, [string]$default = $null, [bool]$required = $false)
    if ($cfg.ContainsKey($key) -and $cfg[$key]) { return $cfg[$key] }
    if ($required) {
        Write-Host "Falta la variable '$key' en $envFile." -ForegroundColor Red
        exit 1
    }
    return $default
}

$remoteSsh    = Get-Cfg "REMOTE_SSH"     -required $true               # ej: leo@1.2.3.4
$remotePort   = Get-Cfg "REMOTE_PORT"    "22"
$remoteKey    = Get-Cfg "REMOTE_KEY"     $null                        # ej: C:/Users/Leo/.ssh/id_ed25519
$repoDir      = Get-Cfg "REMOTE_REPO_DIR" -required $true             # ej: /data/supermaster/repo
$gitBranch    = Get-Cfg "GIT_BRANCH"     "main"
$composeFile  = Get-Cfg "COMPOSE_FILE"   "deploy/docker-compose.prod.yml"
$remoteEnv    = Get-Cfg "REMOTE_ENV_FILE" "deploy/.env"              # env con DB_PASSWORD, etc. (en el server)

# --- Verificar que exista el cliente ssh ---
$sshExe = Get-Command ssh -ErrorAction SilentlyContinue
if (-not $sshExe) {
    Write-Host "No se encontró 'ssh'. Instalá el cliente OpenSSH de Windows (Configuración -> Aplicaciones -> Características opcionales)." -ForegroundColor Red
    exit 1
}

# --- Construir el comando remoto (corre en el shell del servidor) ---
# Encadenado con && para abortar ante el primer error.
$remoteCmd = @(
    "cd '$repoDir'"
    "echo '>> git pull origin $gitBranch...'"
    "git pull origin '$gitBranch'"
    "echo '>> docker compose up -d --build...'"
    "docker compose --env-file '$remoteEnv' -f '$composeFile' up -d --build"
    "echo '>> Limpiando imágenes sin uso...'"
    "docker image prune -f"
    "echo '>> Deploy remoto terminado.'"
) -join " && "

# --- Argumentos de ssh ---
$sshArgs = @()
if ($remotePort) { $sshArgs += @("-p", $remotePort) }
if ($remoteKey)  { $sshArgs += @("-i", $remoteKey) }
$sshArgs += $remoteSsh
$sshArgs += $remoteCmd

Write-Host "Conectando a $remoteSsh (puerto $remotePort)..." -ForegroundColor Cyan
Write-Host "Repo: $repoDir  |  Rama: $gitBranch  |  Compose: $composeFile" -ForegroundColor DarkGray

ssh @sshArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "El deploy remoto falló (exit $LASTEXITCODE)." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Deploy remoto completado correctamente." -ForegroundColor Green
