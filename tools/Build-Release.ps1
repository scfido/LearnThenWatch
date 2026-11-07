param([string]$SigningConfig = (Join-Path $env:USERPROFILE '.android\learnthenwatch\signing.json'))
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot
$config = Get-Content -LiteralPath $SigningConfig -Raw | ConvertFrom-Json
$names = @('ANDROID_KEYSTORE_PATH', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    foreach ($name in $names) {
        if (!$config.$name) { throw "Missing signing configuration: $name" }
        [Environment]::SetEnvironmentVariable($name, $config.$name, 'Process')
    }
    & "$repoRoot\gradlew.bat" --project-dir $repoRoot :app:assembleRelease
    if ($LASTEXITCODE) { throw 'Release build failed' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
