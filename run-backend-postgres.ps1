$ErrorActionPreference = "Stop"

$psql = "C:\Program Files\PostgreSQL\17\bin\psql.exe"
$createdb = "C:\Program Files\PostgreSQL\17\bin\createdb.exe"
if (-not (Test-Path $psql) -or -not (Test-Path $createdb)) {
    throw "PostgreSQL 17 tools were not found under C:\Program Files\PostgreSQL\17\bin."
}

$securePassword = Read-Host "PostgreSQL password for role 'postgres'" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $databasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

$env:PGPASSWORD = $databasePassword
try {
    $databaseExists = & $psql -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'smarthome'"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not connect to PostgreSQL. Verify the password and ensure the PostgreSQL service is running."
    }
    if ($databaseExists.Trim() -ne "1") {
        & $createdb -h localhost -U postgres smarthome
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create database 'smarthome'."
        }
    }

    $connectionCheck = & $psql -h localhost -U postgres -d smarthome -tAc "SELECT current_database()"
    if ($LASTEXITCODE -ne 0 -or $connectionCheck.Trim() -ne "smarthome") {
        throw "Could not connect to database 'smarthome'."
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$env:DB_URL = "jdbc:postgresql://localhost:5432/smarthome"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = $databasePassword
$env:SERVER_PORT = "8081"
if (-not $env:JWT_SECRET) {
    $secretBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
}

try {
    Push-Location (Join-Path $PSScriptRoot "smarthome-backend")
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Spring Boot stopped with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
    $databasePassword = $null
}
