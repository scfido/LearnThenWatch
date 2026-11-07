param([string]$Destination = (Join-Path $env:USERPROFILE '.android\learnthenwatch'))
$ErrorActionPreference = 'Stop'
$keyPath = Join-Path $Destination 'release.jks'
$configPath = Join-Path $Destination 'signing.json'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $configPath)) {
    throw 'Signing files already exist; keep the original key for future updates.'
}
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$bytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$password = [Convert]::ToBase64String($bytes)
$env:LEARNTHENWATCH_NEW_KEY_PASSWORD = $password
try {
    & keytool -genkeypair -storetype JKS -keystore $keyPath -storepass:env LEARNTHENWATCH_NEW_KEY_PASSWORD -keypass:env LEARNTHENWATCH_NEW_KEY_PASSWORD -alias learnthenwatch -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LearnThenWatch'
    if ($LASTEXITCODE) { throw 'keytool failed' }
    [ordered]@{
        ANDROID_KEYSTORE_PATH = [IO.Path]::GetFullPath($keyPath)
        ANDROID_KEYSTORE_PASSWORD = $password
        ANDROID_KEY_ALIAS = 'learnthenwatch'
        ANDROID_KEY_PASSWORD = $password
    } | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
} finally { Remove-Item Env:LEARNTHENWATCH_NEW_KEY_PASSWORD -ErrorAction SilentlyContinue }
Write-Host "Created release signing files in $Destination. Back up this private directory; never commit it."
