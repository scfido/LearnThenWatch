$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sourcePath = Join-Path $repoRoot 'apps\english\src\local\mytv\english'
$outputPath = Join-Path $repoRoot 'apps\english\build\tests'
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$sources = @('PlaybackClock.java', 'Questions.java', 'Curriculum.java', 'AppSettings.java', 'SessionEngine.java', 'ViewingPolicy.java', 'RemoteNavigation.java') | ForEach-Object { Join-Path $sourcePath $_ }
& javac -encoding UTF-8 -d $outputPath @sources "$PSScriptRoot\PlaybackClockTest.java" "$PSScriptRoot\StudyRulesTest.java" "$PSScriptRoot\CurriculumValidationTest.java" "$PSScriptRoot\RemoteNavigationTest.java"
if ($LASTEXITCODE) { throw 'Java test compilation failed' }
foreach ($test in @('PlaybackClockTest','StudyRulesTest','RemoteNavigationTest')) {
    & java -cp $outputPath $test
    if ($LASTEXITCODE) { throw "$test failed" }
}
& java -cp $outputPath CurriculumValidationTest "$repoRoot\apps\english\assets"
if ($LASTEXITCODE) { throw 'Shipped curriculum validation failed' }
