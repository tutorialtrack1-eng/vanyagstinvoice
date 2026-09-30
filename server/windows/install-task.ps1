# Makes the BlitzBook sync server run in the background on this PC: it starts now, starts again every time
# you sign in to Windows, needs no open window, and is restarted if it stops.
#   powershell -ExecutionPolicy Bypass -File server\windows\install-task.ps1            (port 8090)
#   powershell -ExecutionPolicy Bypass -File server\windows\install-task.ps1 -Port 9000
# Run it again to change the port. Remove with uninstall-task.ps1.
param([int]$Port = 8090)
$ErrorActionPreference = 'Stop'
$task = 'BlitzBookSync'
$script = Join-Path $PSScriptRoot 'start-hidden.ps1'

# Stop whatever copy is running now (an earlier task, or one started from a window)
if (Get-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue) { Stop-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName $task -Confirm:$false }
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object { $_.CommandLine -like '*server.js*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -like '*start-hidden.ps1*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`" -Port $Port"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $task -Action $action -Trigger $trigger -Settings $settings -Description 'BlitzBook sync server (app <-> web portal)' | Out-Null
Start-ScheduledTask -TaskName $task
Start-Sleep -Seconds 4

# Let phones on the same network reach it. Adding a firewall rule needs an administrator window.
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($isAdmin) {
    Get-NetFirewallRule -DisplayName 'BlitzBook sync server' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName 'BlitzBook sync server' -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow -Profile Any | Out-Null
    $fw = "firewall: port $Port opened for the app on the local network"
} else {
    $fw = "firewall: run this script once from an administrator PowerShell to open port $Port for phones on the local network (or allow it in Windows Defender Firewall by hand)"
}

$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' -and $_.PrefixOrigin -ne 'WellKnown' } | Select-Object -First 1).IPAddress
try { $ping = Invoke-RestMethod "http://localhost:$Port/api/ping" -TimeoutSec 5 } catch { $ping = $null }
if ($ping -and $ping.app -eq 'BlitzBook') {
    Write-Host "BlitzBook sync server is running in the background and will start with Windows."
    Write-Host "  web portal (this PC) : http://localhost:$Port/"
    if ($ip) { Write-Host "  app / other devices  : http://${ip}:$Port   (enter this under Sync in the app)" }
    Write-Host "  data                 : $(Join-Path (Split-Path $PSScriptRoot) 'data')"
    Write-Host "  $fw"
} else {
    Write-Host "The task was created but the server does not answer on port $Port yet. See server\data\server.log"
}
