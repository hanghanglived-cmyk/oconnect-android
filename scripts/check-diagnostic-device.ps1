param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
# Read-only device/package checks. Never dump authentication logs, accounts or preferences.
& $Adb version
& $Adb devices
if ($LASTEXITCODE -ne 0) { throw 'ADB is unavailable.' }
$deviceState = & $Adb get-state 2>$null
if ($deviceState -ne 'device') { throw 'Select exactly one authorized USB device before continuing.' }
foreach ($name in @('com.openai.chatgpt','com.google.android.apps.bard',
    'com.google.android.googlequicksearchbox','com.google.android.gms','com.android.vending',
    'com.android.chrome','com.google.android.youtube',
    'io.github.hanghanglived.oconnect.whitelist.diagnostic')) {
    $result = & $Adb shell pm path $name 2>$null
    $installed = [bool]($result | Where-Object { $_ -match '^package:' })
    Write-Output "$name installed_for_shell_user=$installed"
}
Write-Output 'This does not prove PackageManager visibility or per-app routing inside the VPN app.'
Write-Output 'Use Show system apps and export the in-app VPN diagnostics for each test scenario.'
