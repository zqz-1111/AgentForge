[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $project '.local-runtime'
$statePath = Join-Path $runtime 'processes.json'
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
$jar = Join-Path $project 'target/ai-agent-template-1.0.0.jar'
$runtimeJar = Join-Path $runtime 'app.jar'
$config = Join-Path $runtime 'validation.properties'
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $($state.appPid)"
if ($process) {
    if ((!$process.CommandLine.Contains($jar) -and !$process.CommandLine.Contains($runtimeJar)) -or !$process.CommandLine.Contains($config.Replace('\','/'))) {
        throw 'Saved PID does not match this validation application; refusing to stop it.'
    }
    Stop-Process -Id $state.appPid
    Wait-Process -Id $state.appPid -Timeout 15 -ErrorAction SilentlyContinue
}
if (Get-NetTCPConnection -State Listen -LocalPort 8085 -ErrorAction SilentlyContinue) {
    throw 'Port 8085 remains occupied; inspect it manually.'
}
Copy-Item -LiteralPath $jar -Destination $runtimeJar -Force
$app = Start-Process -FilePath 'E:/jdk-21.0.7+6/bin/java.exe' -ArgumentList @('-jar', $runtimeJar, "--spring.config.additional-location=file:$($config.Replace('\','/'))") -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'app-out.log') -RedirectStandardError (Join-Path $runtime 'app-error.log')
$state.appPid = $app.Id
$state.startedUtc = [DateTime]::UtcNow.ToString('o')
$state | ConvertTo-Json | Set-Content -LiteralPath $statePath
Write-Output 'Validation application restarted; local protocol fixture and stored data retained.'
