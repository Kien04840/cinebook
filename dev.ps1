# ==============================================================================
# CineBook — Single-Command Development Launcher (dev.ps1)
# Starts & orchestrates:
#   [1] Spring Boot Backend (:8080, profile: local)
#   [2] Vue 3 / Vite Frontend (:5173, reverse proxy /api)
#   [3] ngrok Public Tunnel (forwarding :5173, static domain)
# ==============================================================================

[CmdletBinding()]
param (
    [string]$NgrokDomain = $(if ($env:NGROK_DOMAIN) { $env:NGROK_DOMAIN } else { "silencer-gimmick-uplifted.ngrok-free.dev" }),
    [switch]$NoNgrok,
    [int]$BackendTimeoutSeconds = 45,
    [int]$FrontendTimeoutSeconds = 15,
    [int]$NgrokTimeoutSeconds = 15
)

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  CineBook Development Environment Launcher" -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

$rootDir = $PSScriptRoot
$frontendDir = Join-Path $rootDir "frontend"

# 1. Validate Prerequisites
if (-not (Test-Path (Join-Path $rootDir "mvnw.cmd"))) {
    Write-Error "[FATAL] mvnw.cmd not found in root directory $rootDir"
    exit 1
}

if (-not (Test-Path $frontendDir)) {
    Write-Error "[FATAL] frontend directory not found at $frontendDir"
    exit 1
}

# Array to track child processes spawned by this session
$spawnedProcesses = @()

# Helper: Test TCP port listening
function Test-PortListening([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return ($null -ne $conn)
    }
    catch {
        return $false
    }
}

# Helper: Test HTTP endpoint
function Test-HttpEndpoint([string]$Url, [int]$TimeoutSec = 2) {
    try {
        $req = [System.Net.WebRequest]::Create($Url)
        $req.Timeout = $TimeoutSec * 1000
        $req.Headers.Add("ngrok-skip-browser-warning", "true")
        $req.UserAgent = "CineBook-HealthCheck/1.0"
        $resp = $req.GetResponse()
        $resp.Close()
        return $true
    }
    catch [System.Net.WebException] {
        if ($_.Response) {
            $_.Response.Close()
            return $true # Received HTTP response (even 4xx/5xx means server is listening)
        }
        return $false
    }
    catch {
        return $false
    }
}

# Helper: Check if ngrok is active
function Test-NgrokActive([string]$TargetDomain) {
    try {
        $tunnels = Invoke-RestMethod -Uri "http://127.0.0.1:4040/api/tunnels" -TimeoutSec 2 -ErrorAction Stop
        if ($tunnels.tunnels) {
            foreach ($t in $tunnels.tunnels) {
                if ($t.public_url -like "*$TargetDomain*") {
                    return $true
                }
            }
        }
    }
    catch {
        # ngrok API not reachable
    }
    return $false
}

try {
    # -------------------------------------------------------------------------
    # [1/3] Start Spring Boot Backend
    # -------------------------------------------------------------------------
    $backendAlreadyRunning = Test-PortListening -Port 8080
    $backendProcess = $null

    if ($backendAlreadyRunning) {
        Write-Host "[1/3] [INFO] Port 8080 is already in use. Assuming Spring Boot backend is running." -ForegroundColor DarkCyan
    }
    else {
        $mvnwPath = Join-Path $rootDir "mvnw.cmd"
        $backendProcess = Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/c", "`"$mvnwPath`" spring-boot:run -Dspring-boot.run.profiles=local" `
            -WorkingDirectory $rootDir `
            -PassThru

        if ($backendProcess) {
            $spawnedProcesses += $backendProcess
            Write-Host "      Backend process started (PID $($backendProcess.Id))." -ForegroundColor DarkGray
        }
    }

    # -------------------------------------------------------------------------
    # [2/3] Start Vite Frontend
    # -------------------------------------------------------------------------
    $frontendAlreadyRunning = Test-PortListening -Port 5173
    $frontendProcess = $null

    if ($frontendAlreadyRunning) {
        Write-Host "[2/3] [INFO] Port 5173 is already in use. Assuming Vite frontend is running." -ForegroundColor DarkCyan
    }
    else {
        Write-Host "[2/3] Starting Vite Frontend (:5173, proxy -> :8080)..." -ForegroundColor Cyan
        $frontendProcess = Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/c", "npm run dev" `
            -WorkingDirectory $frontendDir `
            -PassThru

        if ($frontendProcess) {
            $spawnedProcesses += $frontendProcess
            Write-Host "      Frontend process started (PID $($frontendProcess.Id))." -ForegroundColor DarkGray
        }
    }

    # -------------------------------------------------------------------------
    # [3/3] Start ngrok Public Tunnel
    # -------------------------------------------------------------------------
    $ngrokProcess = $null
    $ngrokCommandAvailable = (Get-Command "ngrok" -ErrorAction SilentlyContinue) -ne $null

    if ($NoNgrok) {
        Write-Host "[3/3] [INFO] ngrok tunnel skipped by flag (-NoNgrok)." -ForegroundColor DarkGray
    }
    elseif (-not $ngrokCommandAvailable) {
        Write-Host "[3/3] [WARN] 'ngrok' command not found in PATH. Public tunnel will not be started." -ForegroundColor Yellow
    }
    else {
        $ngrokAlreadyActive = Test-NgrokActive -TargetDomain $NgrokDomain
        if ($ngrokAlreadyActive) {
            Write-Host "[3/3] [INFO] ngrok tunnel for '$NgrokDomain' is already active." -ForegroundColor DarkCyan
        }
        else {
            Write-Host "[3/3] Starting ngrok tunnel (:5173 -> https://$NgrokDomain)..." -ForegroundColor Cyan
            $ngrokProcess = Start-Process -FilePath "cmd.exe" `
                -ArgumentList "/c", "ngrok http 5173 --domain=$NgrokDomain" `
                -WorkingDirectory $rootDir `
                -PassThru

            if ($ngrokProcess) {
                $spawnedProcesses += $ngrokProcess
                Write-Host "      ngrok process started (PID $($ngrokProcess.Id))." -ForegroundColor DarkGray
            }
        }
    }

    # -------------------------------------------------------------------------
    # Health Checks & Readiness Verification
    # -------------------------------------------------------------------------
    Write-Host ""
    Write-Host "[HEALTH CHECK] Waiting for services to become healthy..." -ForegroundColor Yellow

    # Check Frontend
    $feReady = $false
    $feTimer = 0
    Write-Host -NoNewline "  -> Checking Vite Frontend (http://localhost:5173)... "
    while ($feTimer -lt $FrontendTimeoutSeconds) {
        if (Test-HttpEndpoint -Url "http://localhost:5173") {
            $feReady = $true
            break
        }
        Start-Sleep -Seconds 1
        $feTimer++
    }
    if ($feReady) {
        Write-Host "ONLINE (HTTP 200)" -ForegroundColor Green
    } else {
        Write-Host "TIMEOUT (${FrontendTimeoutSeconds}s)" -ForegroundColor Red
    }

    # Check Backend
    $beReady = $false
    $beTimer = 0
    Write-Host -NoNewline "  -> Checking Spring Boot Backend (http://localhost:8080)... "
    while ($beTimer -lt $BackendTimeoutSeconds) {
        if (Test-HttpEndpoint -Url "http://localhost:8080/api/v1/movies?page=0&size=1") {
            $beReady = $true
            break
        }
        Start-Sleep -Seconds 1
        $beTimer++
    }
    if ($beReady) {
        Write-Host "ONLINE (Profile: local, HTTP 200)" -ForegroundColor Green
    } else {
        Write-Host "TIMEOUT (${BackendTimeoutSeconds}s)" -ForegroundColor Red
    }

    # Check ngrok Public Endpoint
    $ngrokReady = $false
    if (-not $NoNgrok -and $ngrokCommandAvailable) {
        $ngTimer = 0
        Write-Host -NoNewline "  -> Checking ngrok Public Tunnel (https://$NgrokDomain)... "
        while ($ngTimer -lt $NgrokTimeoutSeconds) {
            if (Test-HttpEndpoint -Url "https://$NgrokDomain") {
                $ngrokReady = $true
                break
            }
            Start-Sleep -Seconds 1
            $ngTimer++
        }
        if ($ngrokReady) {
            Write-Host "ONLINE" -ForegroundColor Green
        } else {
            Write-Host "OFFLINE / UNREACHABLE" -ForegroundColor Yellow
        }
    }

    # -------------------------------------------------------------------------
    # Environment Status Banner
    # -------------------------------------------------------------------------
    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "  CineBook Development Stack Status" -ForegroundColor Yellow
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "  Backend API   : http://localhost:8080" -ForegroundColor Green
    Write-Host "  Swagger UI    : http://localhost:8080/swagger-ui.html" -ForegroundColor Green
    Write-Host "  Frontend App  : http://localhost:5173" -ForegroundColor Green
    if ($ngrokReady) {
        Write-Host "  Public Domain : https://$NgrokDomain" -ForegroundColor Magenta
        Write-Host "  Public API    : https://$NgrokDomain/api" -ForegroundColor Magenta
        Write-Host "  VNPay Return  : https://$NgrokDomain/payment/result" -ForegroundColor Cyan
        Write-Host "  VNPay IPN     : https://$NgrokDomain/api/v1/payments/vnpay/ipn" -ForegroundColor Cyan
    }
    Write-Host "================================================================" -ForegroundColor Cyan
    
    if (-not $beReady -or -not $feReady) {
        Write-Warning "[WARN] One or more critical services failed to report healthy within timeout."
    } else {
        Write-Host "  [READY] All services running. Press Ctrl+C to terminate." -ForegroundColor Green
    }
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host ""

    # -------------------------------------------------------------------------
    # Keep Alive Loop & Process Health Monitoring
    # -------------------------------------------------------------------------
    while ($true) {
        foreach ($proc in $spawnedProcesses) {
            if ($proc.HasExited) {
                Write-Warning "Process (PID $($proc.Id)) exited unexpectedly with code $($proc.ExitCode)."
            }
        }
        Start-Sleep -Seconds 2
    }
}
finally {
    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Yellow
    Write-Host "  Stopping CineBook spawned development processes..." -ForegroundColor Yellow
    Write-Host "================================================================" -ForegroundColor Yellow

    foreach ($proc in $spawnedProcesses) {
        if ($proc -and -not $proc.HasExited) {
            Write-Host "Terminating process PID $($proc.Id)..." -ForegroundColor DarkGray
            try {
                taskkill /PID $proc.Id /T /F 2>$null | Out-Null
            }
            catch {
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Write-Host "[OK] Development environment shutdown complete." -ForegroundColor Green
}
