param([string]$Voice = 'Microsoft Zira Desktop')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$app = Split-Path $PSScriptRoot
$wavDirectory = Join-Path $app 'build\content-source\wav'
New-Item -ItemType Directory -Force -Path $wavDirectory | Out-Null
$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer
$speaker.SelectVoice($Voice)
$speaker.Rate = -1
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)
$rows = Import-Csv -LiteralPath (Join-Path $app 'assets\curriculum.tsv') -Delimiter "`t" -Encoding UTF8 |
    Sort-Object -Property audio -Unique
$count = 0
try {
    foreach ($row in $rows) {
        $filename = [IO.Path]::GetFileNameWithoutExtension($row.audio) + '.wav'
        $destination = Join-Path $wavDirectory $filename
        if (!(Test-Path -LiteralPath $destination)) {
            $speaker.SetOutputToWaveFile($destination, $format)
            $speaker.Speak($row.word)
            $speaker.SetOutputToNull()
        }
        $count++
        if ($count % 250 -eq 0) { Write-Output "Synthesized $count / $($rows.Count)" }
    }
} finally { $speaker.Dispose() }
Write-Output "Offline English speech complete: $count unique words and phrases"
