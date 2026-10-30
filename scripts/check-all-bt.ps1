$devs = Get-PnpDevice -Class Bluetooth | Where-Object InstanceId -like '*7CF0E54B732C*'
foreach ($dev in $devs) {
    Write-Host "=== Device: $($dev.FriendlyName) ($($dev.InstanceId)) ==="
    $props = Get-PnpDeviceProperty -InstanceId $dev.InstanceId
    foreach ($p in $props) {
        if ($p.KeyName -like "*Connect*" -or $p.KeyName -like "*Status*" -or $p.KeyName -like "*Radio*" -or $p.KeyName -like "*State*") {
            Write-Host "   $($p.KeyName) = $($p.Data)"
        }
    }
}

