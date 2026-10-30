$dev = Get-PnpDevice -Class Bluetooth | Where-Object InstanceId -like '*7CF0E54B732C*' | Select-Object -First 1
Write-Host "Device: $($dev.FriendlyName) ($($dev.InstanceId))"
$props = Get-PnpDeviceProperty -InstanceId $dev.InstanceId
Write-Host "Total properties: $($props.Count)"
foreach ($p in $props) {
    if ($p.KeyName -like "*Connect*" -or $p.KeyName -like "*Status*" -or $p.KeyName -like "*State*" -or $p.KeyName -like "*Present*") {
        Write-Host "$($p.KeyName) = $($p.Data)"
    }
}

