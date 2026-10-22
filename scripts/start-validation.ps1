[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $project '.local-runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
foreach ($port in @(18085, 8085)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already occupied; inspect the process rather than replacing it."
    }
}
$config = Join-Path $runtime 'validation.properties'
$tokenFile = Join-Path $runtime 'registration-token.txt'
if (!(Test-Path -LiteralPath $tokenFile)) {
    [Guid]::NewGuid().ToString('N') | Set-Content -LiteralPath $tokenFile -NoNewline
}
$token = Get-Content -LiteralPath $tokenFile -Raw
@"
server.address=127.0.0.1
server.port=8085
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/agentforge_validation
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
spring.data.redis.database=14
ai.openai.api-key=local-fixture-only
ai.openai.base-url=http://127.0.0.1:18085/v1
ai.auth.registration-token=$token
management.tracing.enabled=false
logging.level.com.aiagent=INFO
"@ | Set-Content -LiteralPath $config -Encoding utf8
$stub = Start-Process -FilePath 'E:/pp/python.exe' -ArgumentList @((Join-Path $PSScriptRoot 'validation_model_stub.py')) -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'stub-out.log') -RedirectStandardError (Join-Path $runtime 'stub-error.log')
$runtimeJar = Join-Path $runtime 'app.jar'
Copy-Item -LiteralPath (Join-Path $project 'target/ai-agent-template-1.0.0.jar') -Destination $runtimeJar
$app = Start-Process -FilePath 'E:/jdk-21.0.7+6/bin/java.exe' -ArgumentList @('-jar', $runtimeJar, "--spring.config.additional-location=file:$($config.Replace('\','/'))") -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime 'app-out.log') -RedirectStandardError (Join-Path $runtime 'app-error.log')
@{stubPid=$stub.Id;appPid=$app.Id;startedUtc=[DateTime]::UtcNow.ToString('o');database='agentforge_validation';redisDatabase=14;model='local protocol fixture only'} | ConvertTo-Json | Set-Content (Join-Path $runtime 'processes.json')
Write-Output 'Validation processes started on 127.0.0.1:8085 and 18085; no remote model configured.'
