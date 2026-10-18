param([string]$Name = 'CuriousTrainingApi34', [int]$Port = 5556)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot\..").Path
$env:ANDROID_HOME = "$root\.android-test\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_AVD_HOME = "$root\.android-test\avd"
# Emulator 会把 netsimd 的日志写到 stderr；在 Stop 策略下会被当成终止错误。
# 这里改为不中断，并把两路输出都转发出去，正常运行就不会被误判为失败。
$ErrorActionPreference = 'Continue'
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd $Name -port $Port -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 -cores 4 2>&1 | ForEach-Object { "$_" }
exit $LASTEXITCODE
