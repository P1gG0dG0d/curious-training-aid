param(
    [string]$Serial = 'emulator-5556',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$ReportDirectory = "$PSScriptRoot\..\.android-test\reports"
)
$ErrorActionPreference = 'Stop'
$package = 'com.haoqi.training'
New-Item -ItemType Directory -Force $ReportDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
& $Adb -s $Serial shell am force-stop $package
& $Adb -s $Serial logcat -c
$launch = & $Adb -s $Serial shell am start -W -n "$package/.MainActivity" 2>&1
$launch | Out-File "$ReportDirectory\$stamp-launch.txt" -Encoding utf8
# Let startup coroutines and the first rendered frame execute; this delay is part of the test.
Start-Sleep -Seconds 5
$processId = & $Adb -s $Serial shell pidof $package
$crash = & $Adb -s $Serial logcat -b crash -d -v threadtime
$crash | Out-File "$ReportDirectory\$stamp-crash.txt" -Encoding utf8
$activities = & $Adb -s $Serial shell dumpsys activity activities
$activities | Out-File "$ReportDirectory\$stamp-activities.txt" -Encoding utf8
$launch
$crash
if ([string]::IsNullOrWhiteSpace("$processId") -or "$crash" -match 'FATAL EXCEPTION' -or "$activities" -notmatch '(?:mResumedActivity|topResumedActivity|ResumedActivity).*com\.haoqi\.training/\.MainActivity') {
    Write-Output "FAIL: app failed to remain resumed after cold launch. Reports: $ReportDirectory ($stamp)"
    exit 1
}
Write-Output "PASS: MainActivity remains resumed; process=$processId; no fatal exception. Reports: $ReportDirectory ($stamp)"
