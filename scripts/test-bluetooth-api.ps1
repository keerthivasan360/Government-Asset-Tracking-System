$base = "http://127.0.0.1:8080"
$session = Invoke-RestMethod -Uri "$base/api/session" -SessionVariable s
$login = Invoke-RestMethod -Uri "$base/api/login" -Method Post -Body "username=admin&password=admin" -ContentType "application/x-www-form-urlencoded" -WebSession $s -Headers @{ $session.csrfHeader = $session.csrfToken }

# Refresh session to get rotated token
$session = Invoke-RestMethod -Uri "$base/api/session" -WebSession $s

$devices = Invoke-RestMethod -Uri "$base/api/bluetooth/devices" -WebSession $s
Write-Host "=== LIVE DETECTED BLUETOOTH DEVICES ON PC ($($devices.Count)) ==="
foreach ($d in $devices) {
    Write-Host "Device: $($d.Name) | MAC: $($d.Mac) | Connected: $($d.Connected)"
}

Write-Host "`nTesting /api/bluetooth/sync to import all paired devices..."
$synced = Invoke-RestMethod -Uri "$base/api/bluetooth/sync" -Method Post -WebSession $s -Headers @{ $session.csrfHeader = $session.csrfToken }
Write-Host "Newly synced assets count: $($synced.Count)"

$summary = Invoke-RestMethod -Uri "$base/api/summary" -WebSession $s
Write-Host "`n=== CURRENT TRACKED ASSETS ($($summary.assets.Count)) ==="
foreach ($a in $summary.assets) {
    Write-Host "Asset: $($a.name) | MAC: $($a.bluetoothMac) | Status: $($a.status) | Type: $($a.type)"
}

