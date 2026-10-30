param(
    [string]$Title = "AssetGuard - Bluetooth Alert",
    [string]$Message = "Bluetooth connection lost!"
)

# Play system exclamation / alarm sound
try {
    [System.Media.SystemSounds]::Exclamation.Play()
} catch {}

# Windows Native Toast Notification
try {
    [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
    [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null

    $template = @"
<toast>
    <visual>
        <binding template="ToastGeneric">
            <text>$Title</text>
            <text>$Message</text>
        </binding>
    </visual>
</toast>
"@
    $xmlDoc = New-Object Windows.Data.Xml.Dom.XmlDocument
    $xmlDoc.LoadXml($template)
    $toast = [Windows.UI.Notifications.ToastNotification]::new($xmlDoc)
    $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe")
    $notifier.Show($toast)
} catch {
    # Fallback balloon
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $notify = New-Object System.Windows.Forms.NotifyIcon
        $notify.Icon = [System.Drawing.SystemIcons]::Warning
        $notify.BalloonTipTitle = $Title
        $notify.BalloonTipText = $Message
        $notify.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::Warning
        $notify.Visible = $True
        $notify.ShowBalloonTip(5000)
        Start-Sleep -Seconds 1
        $notify.Dispose()
    } catch {}
}
