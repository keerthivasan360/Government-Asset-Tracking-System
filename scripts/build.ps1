$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    npm --prefix frontend ci
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    npm --prefix frontend test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    npm --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    mvn clean package
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
} finally { Pop-Location }
