# Insert Jalna Mandi records directly into MySQL Docker container
Write-Host "Auto-detecting credentials from container 'farm-mysql-test'..." -ForegroundColor Cyan

$sqlFile = Join-Path $PSScriptRoot "backend\src\main\resources\db\migration\V9__seed_jalna_market_price_records.sql"

if (-not (Test-Path $sqlFile)) {
    Write-Host "SQL file not found at: $sqlFile" -ForegroundColor Red
    exit 1
}

# Inspect container environment variables to get password automatically
$envVars = docker inspect farm-mysql-test --format '{{range .Config.Env}}{{println .}}{{end}}' 2>$null

$userPass = ""
$rootPass = ""

foreach ($line in $envVars) {
    if ($line -match "^MYSQL_PASSWORD=(.+)") {
        $userPass = $matches[1]
    }
    if ($line -match "^MYSQL_ROOT_PASSWORD=(.+)") {
        $rootPass = $matches[1]
    }
}

$success = $false

if ($userPass) {
    Write-Host "Found farm_test_user password from container environment. Applying..." -ForegroundColor Cyan
    Get-Content $sqlFile | docker exec -i farm-mysql-test mysql -u farm_test_user "-p$userPass" farm_strategy_test
    if ($LASTEXITCODE -eq 0) { $success = $true }
}

if (-not $success -and $rootPass) {
    Write-Host "Trying root password from container environment..." -ForegroundColor Cyan
    Get-Content $sqlFile | docker exec -i farm-mysql-test mysql -u root "-p$rootPass" farm_strategy_test
    if ($LASTEXITCODE -eq 0) { $success = $true }
}

if (-not $success) {
    Write-Host "Trying default root without password..." -ForegroundColor Cyan
    Get-Content $sqlFile | docker exec -i farm-mysql-test mysql -u root farm_strategy_test
    if ($LASTEXITCODE -eq 0) { $success = $true }
}

if ($success) {
    Write-Host "`nSUCCESS! All Jalna APMC records inserted into MySQL successfully!" -ForegroundColor Green
    Write-Host "Now go to your browser at http://localhost:5173/profile and click 'Refresh' on the Mandi card." -ForegroundColor Yellow
} else {
    Write-Host "`nCould not insert automatically. Please run:" -ForegroundColor Yellow
    Write-Host "docker inspect farm-mysql-test --format '{{range .Config.Env}}{{println .}}{{end}}'" -ForegroundColor White
}
