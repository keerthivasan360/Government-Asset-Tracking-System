$base = "http://127.0.0.1:8080"
$session = Invoke-RestMethod -Uri "$base/api/session" -SessionVariable webSession
$token = $session.csrfToken
$header = $session.csrfHeader

Write-Host "CSRF Token obtained."

$loginBody = "username=admin&password=admin"
$loginResp = Invoke-RestMethod -Uri "$base/api/login" -Method Post -Body $loginBody -ContentType "application/x-www-form-urlencoded" -WebSession $webSession -Headers @{ $header = $token }

Write-Host "Admin signed in successfully."

# Refresh session to get new CSRF token after login
$session = Invoke-RestMethod -Uri "$base/api/session" -WebSession $webSession
$token = $session.csrfToken
$header = $session.csrfHeader

$summary = Invoke-RestMethod -Uri "$base/api/summary" -WebSession $webSession
Write-Host "Current mode: $($summary.mode)"
Write-Host "Total assets: $($summary.assets.Count)"

$phone = $summary.assets | Where-Object { $_.bluetoothMac -eq "7C:F0:E5:4B:73:2C" }

if (-not $phone) {
    Write-Host "Registering phone 'keerthi''s oneplus'..."
    $assetPayload = @{
        name = "keerthi's oneplus"
        type = "Smartphone"
        serialNo = "ONEPLUS-7CF0E5"
        bluetoothMac = "7C:F0:E5:4B:73:2C"
        department = "Executive / Security"
        assignedTo = "Keerthi"
        currentLocation = "Laptop Desk"
        allowedLocations = "Laptop Desk, Office, Meeting Room"
    } | ConvertTo-Json

    $created = Invoke-RestMethod -Uri "$base/api/assets" -Method Post -Body $assetPayload -ContentType "application/json" -WebSession $webSession -Headers @{ $header = $token }
    Write-Host "Phone registered with ID: $($created.id), Status: $($created.status)"
} else {
    Write-Host "Phone already registered with ID: $($phone.id), Status: $($phone.status)"
}

Start-Sleep -Seconds 6

$summaryAfter = Invoke-RestMethod -Uri "$base/api/summary" -WebSession $webSession
Write-Host "=== POST-POLL SUMMARY ==="
Write-Host "Open Alerts: $($summaryAfter.openAlerts)"
Write-Host "Total Blockchain Blocks: $($summaryAfter.totalBlocks)"
foreach ($a in $summaryAfter.assets) {
    Write-Host "Asset: $($a.name) | Status: $($a.status) | Location: $($a.currentLocation)"
}
foreach ($al in $summaryAfter.alerts) {
    Write-Host "Alert: $($al.assetName) - $($al.message) (Resolved: $($al.resolved))"
}

