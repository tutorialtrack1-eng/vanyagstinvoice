# Runs the BlitzBook sync server without a window and starts it again if it ever stops.
# Used by the scheduled task that install-task.ps1 creates; can also be run by hand:
#   powershell -ExecutionPolicy Bypass -File server\windows\start-hidden.ps1 -Port 8090
param([int]$Port = 8090)
$ErrorActionPreference = 'Continue'
$serverDir = Split-Path -Parent $PSScriptRoot
$node = (Get-Command node -ErrorAction SilentlyContinue).Source
if (-not $node) { $node = 'C:\Program Files\nodejs\node.exe' }
$env:PORT = "$Port"
$log = Join-Path $serverDir 'data\server.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
while ($true) {
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') starting on port $Port" | Add-Content $log
    $p = Start-Process -FilePath $node -ArgumentList 'server.js' -WorkingDirectory $serverDir -WindowStyle Hidden -PassThru -RedirectStandardOutput "$log.out" -RedirectStandardError "$log.err"
    $p.WaitForExit()
    Get-Content "$log.out", "$log.err" -ErrorAction SilentlyContinue | Add-Content $log
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') stopped (exit $($p.ExitCode)); restarting in 5 s" | Add-Content $log
    Start-Sleep -Seconds 5
}
