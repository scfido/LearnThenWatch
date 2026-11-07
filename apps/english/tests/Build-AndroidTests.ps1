param([string]$Device = '192.168.1.132:5555', [switch]$Run, [string]$Screen = '', [switch]$Ui, [switch]$Remote)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$toolPath = Join-Path $sdkPath 'build-tools\35.0.0'
$androidJar = Join-Path $sdkPath 'platforms\android-35\android.jar'
$outputPath = Join-Path $repoRoot 'apps\english\build\android-tests'
New-Item -ItemType Directory -Force -Path "$outputPath\classes", "$outputPath\dex" | Out-Null
$appClasses = Join-Path $repoRoot 'apps\english\build\classes'
$testSources = @(Get-ChildItem "$PSScriptRoot\android" -Filter '*.java' | ForEach-Object FullName)
& javac -encoding UTF-8 -source 8 -target 8 -cp "$androidJar;$appClasses" -d "$outputPath\classes" @testSources
if ($LASTEXITCODE) { throw 'Test compilation failed' }
$classFiles = @(Get-ChildItem "$outputPath\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$toolPath\d8.bat" --lib $androidJar --classpath $appClasses --min-api 26 --output "$outputPath\dex" @classFiles
if ($LASTEXITCODE) { throw 'Test dex failed' }
& "$toolPath\aapt.exe" package -f -M "$PSScriptRoot\android\AndroidManifest.xml" -I $androidJar -F "$outputPath\unsigned.apk"
if ($LASTEXITCODE) { throw 'Test packaging failed' }
Push-Location "$outputPath\dex"
try { & "$toolPath\aapt.exe" add "$outputPath\unsigned.apk" classes.dex; if ($LASTEXITCODE) { throw 'Test dex packaging failed' } }
finally { Pop-Location }
& "$toolPath\zipalign.exe" -f 4 "$outputPath\unsigned.apk" "$outputPath\aligned.apk"
if ($LASTEXITCODE) { throw 'Test alignment failed' }
& "$toolPath\apksigner.bat" sign --ks "$repoRoot\build\local-signing.jks" --ks-pass pass:android --out "$outputPath\english-tests.apk" "$outputPath\aligned.apk"
if ($LASTEXITCODE) { throw 'Test signing failed' }
if ($Run) {
    & adb -s $Device install -r "$outputPath\english-tests.apk"
    if ($LASTEXITCODE) { throw 'Test install failed' }
    $testArgs = @('-s', $Device, 'shell', 'am', 'instrument', '-w')
    if ($Screen) { $testArgs += @('-e', 'screen', $Screen) }
    if ($Ui) { $testArgs += @('-e', 'ui', 'true') }
    if ($Remote) { $testArgs += @('-e', 'remote', 'true') }
    $testArgs += 'local.mytv.english.tests/local.mytv.english.RepositoryInstrumentation'
    $result = [System.Collections.Generic.List[string]]::new()
    & adb @testArgs | ForEach-Object { $line = [string]$_; $result.Add($line); Write-Output $line }
    if ($LASTEXITCODE -or ($result -join "`n") -notmatch 'PASS:') { throw 'Android repository tests failed' }
}
