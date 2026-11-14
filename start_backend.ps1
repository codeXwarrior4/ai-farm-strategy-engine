# ============================================================
# AI Farm Strategy & Decision Engine - Local Backend Runner
# ============================================================

# Automatically load environment variables from .env if present
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*([^#=]+?)\s*=\s*(.*)\s*$' } | ForEach-Object {
        $key = $matches[1].Trim()
        $val = $matches[2].Trim()
        if (-not [Environment]::GetEnvironmentVariable($key)) {
            [Environment]::SetEnvironmentVariable($key, $val)
        }
    }
}

# Defaults for non-sensitive operational parameters
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot" }
if (-not $env:DB_URL) { $env:DB_URL = "jdbc:mysql://localhost:3306/farm_strategy_test?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = "farm_test_user" }
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = "8080" }
if (-not $env:SPRING_JPA_HIBERNATE_DDL_AUTO) { $env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate" }

$jarPath = Join-Path $PSScriptRoot "backend\target\ai-farm-strategy-backend-0.1.0-SNAPSHOT.jar"
$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"

if (-not (Test-Path $javaExe)) {
    $javaExe = "java"
}

& $javaExe -jar $jarPath

