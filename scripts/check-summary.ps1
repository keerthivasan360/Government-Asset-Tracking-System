Start-Sleep -Seconds 6
$base = "http://127.0.0.1:8080"
$session = Invoke-RestMethod -Uri "$base/api/session" -SessionVariable s
$login = Invoke-RestMethod -Uri "$base/api/login" -Method Post -Body "username=admin&password=admin" -ContentType "application/x-www-form-urlencoded" -WebSession $s -Headers @{ $session.csrfHeader = $session.csrfToken }
$summary = Invoke-RestMethod -Uri "$base/api/summary" -WebSession $s
Write-Host "=== POST-POLL MULTI-DEVICE STATUS ==="
foreach ($a in $summary.assets) {
    Write-Host "Asset: $($a.name) | Status: $($a.status)"
}
Write-Host "Open Alerts: $($summary.openAlerts)"
Write-Host "Total Blockchain Blocks: $($summary.totalBlocks)"

