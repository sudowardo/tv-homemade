<#
.SYNOPSIS
    Build tv-homemade, install it, and make it the home screen and the screensaver.

.DESCRIPTION
    Needs JDK 17 and the Android SDK (ANDROID_HOME). A local SDK in .toolchain\android-sdk is used
    when present; that folder is git-ignored.
    Also sets window and transition animations to -AnimationScale, which makes a slow TV feel faster.

.EXAMPLE
    .\tools\install.ps1
    .\tools\install.ps1 -SkipBuild
#>
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb,
    [string]$AnimationScale = "0.5",
    [switch]$SkipBuild,
    [switch]$DryRun
)

. "$PSScriptRoot\common.ps1"

$launcherDir = Join-Path $RepoRoot "launcher"
$apk = Join-Path $launcherDir "app\build\outputs\apk\release\app-release.apk"

if (-not $SkipBuild -and -not $DryRun) {
    $toolchain = Join-Path $RepoRoot ".toolchain"
    if (Test-Path (Join-Path $toolchain "android-sdk")) {
        $env:ANDROID_HOME = Join-Path $toolchain "android-sdk"
        $env:GRADLE_USER_HOME = Join-Path $toolchain "gradle-home"
        $env:ANDROID_USER_HOME = Join-Path $toolchain "android-user"
    }
    if (-not $env:ANDROID_HOME) { throw "ANDROID_HOME is not set and there is no .toolchain\android-sdk." }
    & (Join-Path $launcherDir "gradlew.bat") -p $launcherDir assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }
}
if (-not $DryRun -and -not (Test-Path $apk)) { throw "APK not found: $apk. Run without -SkipBuild." }

Connect-Tv

Write-Host "`n== Installing"
if ($DryRun) {
    Write-Host "[dry-run] adb install -r $apk"
} else {
    $output = (& $script:adbPath -s $TvHost install -r $apk 2>&1) -join " "
    if ($LASTEXITCODE -ne 0 -or $output -notmatch "Success") { throw "Install failed: $output" }
    Write-Host "ok    $output"
}

Write-Host "`n== Home screen, screensaver and animations"
Invoke-TvShell "cmd package set-home-activity $LauncherHome" "Success"
Invoke-TvShell "settings put secure screensaver_components $LauncherDream"
Invoke-TvShell "settings put global window_animation_scale $AnimationScale"
Invoke-TvShell "settings put global transition_animation_scale $AnimationScale"

if (-not $DryRun) {
    $homeActivity = Get-HomeActivity
    if ($homeActivity -ne $LauncherHome) { $script:failures += "HOME resolves to '$homeActivity', expected $LauncherHome" }
}

Complete-Run "Installed. Press Home on the TV. Optional: tools\set-photo.ps1 -Path <photo> for a background photo."
