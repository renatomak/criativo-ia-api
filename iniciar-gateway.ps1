param(
    [string]$Modelo = 'claude-haiku-5-5',
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'target\criativo-ia-api-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Compile o projeto com mvn verify antes de iniciar.' }
$nomes = @('ANTHROPIC_API_KEY', 'ANTHROPIC_AUTH_TOKEN', 'ANTHROPIC_BASE_URL', 'CLAUDE_MODEL')
$anteriores = @{}
foreach ($nome in $nomes) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }
try {
    $env:ANTHROPIC_API_KEY = $null
    $configLocal = Join-Path $PSScriptRoot 'gateway.local.json'
    if (Test-Path -LiteralPath $configLocal) {
        $config = Get-Content -LiteralPath $configLocal -Raw | ConvertFrom-Json
        $env:ANTHROPIC_AUTH_TOKEN = $config.token
    } else {
        $token = Read-Host 'Token do gateway (entrada oculta)' -AsSecureString
        $env:ANTHROPIC_AUTH_TOKEN = [Net.NetworkCredential]::new('', $token).Password
    }
    if ([string]::IsNullOrWhiteSpace($env:ANTHROPIC_AUTH_TOKEN)) { throw 'Token obrigatório.' }
    $env:ANTHROPIC_BASE_URL = 'https://api.mwapi.dev'
    $env:CLAUDE_MODEL = $Modelo
    Push-Location $PSScriptRoot
    try { & $Java -jar $jar } finally { Pop-Location }
} finally {
    foreach ($nome in $nomes) { [Environment]::SetEnvironmentVariable($nome, $anteriores[$nome], 'Process') }
}
