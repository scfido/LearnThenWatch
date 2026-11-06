param(
    [ValidateSet('bilistart', 'english', 'all')]
    [string]$App = 'bilistart',
    [switch]$English
)
$ErrorActionPreference = 'Stop'
if ($English) {
    if ($PSBoundParameters.ContainsKey('App') -and $App -ne 'english') {
        throw 'Use either -English or -App, not both.'
    }
    $App = 'english'
}
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$tools = Join-Path $sdk 'build-tools\35.0.0'
$android = Join-Path $sdk 'platforms\android-35\android.jar'
# Preserve the existing signing key so installed apps can receive updates.
$key = Join-Path $PSScriptRoot 'build\local-signing.jks'
New-Item -ItemType Directory -Force -Path (Split-Path $key) | Out-Null
if (!(Test-Path -LiteralPath $key)) {
    & keytool -genkeypair -keystore $key -storepass android -keypass android -alias local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Local TV Startup'
    if ($LASTEXITCODE) { throw 'keytool failed' }
}
$apps = if ($App -eq 'all') { @('bilistart', 'english') } else { @($App) }
foreach ($currentApp in $apps) {
    $project = Join-Path $PSScriptRoot "apps\$currentApp"
    $build = Join-Path $project 'build'
    New-Item -ItemType Directory -Force -Path "$build\classes", "$build\dex" | Out-Null
    $sources = @(Get-ChildItem "$project\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$build\classes" @sources
    if ($LASTEXITCODE) { throw 'javac failed' }
    $classes = @(Get-ChildItem "$build\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
    $minApi = if ($currentApp -eq 'english') { 26 } else { 24 }
    & "$tools\d8.bat" --lib $android --min-api $minApi --output "$build\dex" @classes
    if ($LASTEXITCODE) { throw 'd8 failed' }
    $resources = if (Test-Path "$project\res") { @('-S', "$project\res") } else { @() }
    & "$tools\aapt.exe" package -f -M "$project\AndroidManifest.xml" -I $android @resources -F "$build\unsigned.apk"
    if ($LASTEXITCODE) { throw 'aapt failed' }
    Push-Location "$build\dex"
    try {
        & "$tools\aapt.exe" add "$build\unsigned.apk" classes.dex
        if ($LASTEXITCODE) { throw 'aapt add failed' }
    } finally { Pop-Location }
    & "$tools\zipalign.exe" -f 4 "$build\unsigned.apk" "$build\aligned.apk"
    if ($LASTEXITCODE) { throw 'zipalign failed' }
    $name = if ($currentApp -eq 'english') { 'englishquiz.apk' } else { 'bilistart.apk' }
    & "$tools\apksigner.bat" sign --ks $key --ks-pass pass:android --out "$build\$name" "$build\aligned.apk"
    if ($LASTEXITCODE) { throw 'signing failed' }
    & "$tools\apksigner.bat" verify "$build\$name"
    if ($LASTEXITCODE) { throw 'signature verification failed' }
    Write-Host "Built: $build\$name"
}
