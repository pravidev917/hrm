# Build a deployable release from Windows PowerShell.
#
#   deploy\scripts\build-release.ps1
#
# Produces build\release\<stamp>\ with backend.jar, dist\ and SHA256SUMS.
# Nothing is uploaded; use scp (see docs/PRODUCTION-DEPLOYMENT.md §6-7).
$ErrorActionPreference = 'Stop'

$root  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out   = Join-Path $root "build\release\$stamp"

Write-Host "== HRM release build: $stamp =="

foreach ($tool in 'java', 'node', 'npm') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool not found on PATH"
    }
}

# The Spring context-load test needs a database and a strong JWT secret. On a
# build workstation these come from E:\hrm\.env (values are never printed and
# never reach the release output).
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    $loaded = 0
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $name, $value = $line.Split('=', 2)
        $name = $name.Trim()
        if (-not $name) { continue }
        $value = $value.Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        $loaded++
    }
    Write-Host "-- loaded $loaded build variables from .env (values not shown)"
} else {
    Write-Host '-- no .env: DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET must already be set,'
    Write-Host '   or the Spring context test cannot start (docs/PRODUCTION-DEPLOYMENT.md section 4).'
}

Write-Host '-- backend: mvnw verify'
Push-Location (Join-Path $root 'backend')
try {
    & .\mvnw.cmd -B verify
    if ($LASTEXITCODE -ne 0) { throw "backend build failed ($LASTEXITCODE)" }
} finally { Pop-Location }

$jar = Get-ChildItem (Join-Path $root 'backend\target') -Filter 'backend-*.jar' |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw 'backend jar not found in backend\target' }

Write-Host '-- frontend: install, lint, build (same-origin API base)'
Push-Location (Join-Path $root 'frontend')
try {
    npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed ($LASTEXITCODE)" }

    npm run lint
    if ($LASTEXITCODE -ne 0) { throw "lint failed ($LASTEXITCODE)" }

    $env:VITE_API_BASE_URL = ''
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "frontend build failed ($LASTEXITCODE)" }

    $leak = Get-ChildItem dist -Recurse -File |
            Select-String -Pattern 'localhost:8080' -List -ErrorAction SilentlyContinue
    if ($leak) {
        throw "the bundle still references localhost:8080: $($leak.Path -join ', ')"
    }
} finally { Pop-Location }

New-Item -ItemType Directory -Force -Path $out | Out-Null
Copy-Item $jar.FullName (Join-Path $out 'backend.jar')
Copy-Item (Join-Path $root 'frontend\dist') (Join-Path $out 'dist') -Recurse

# lowercase hash, LF line ending so `sha256sum -c` works unchanged on Ubuntu
$hash = (Get-FileHash (Join-Path $out 'backend.jar') -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $out 'SHA256SUMS'), "$hash  backend.jar`n",
                               [System.Text.Encoding]::ASCII)

Write-Host ''
Write-Host "Release ready: $out"
Write-Host 'Next: scp the files to the server and run deploy-backend.sh / deploy-frontend.sh there'
Write-Host '(steps in docs/PRODUCTION-DEPLOYMENT.md, sections 6, 7 and 12).'
