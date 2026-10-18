<#
.SYNOPSIS
    Undo tools\disable.ps1 and tools\install.ps1: re-enable the debloat list and bring back the stock home screen.

.DESCRIPTION
    Re-enables every package in the list, makes the stock launcher the home app again, and resets
    the animation and screensaver settings to Android TV defaults.
    tv-homemade stays installed unless -RemoveLauncher is passed.

.EXAMPLE
    .\tools\restore.ps1 -DryRun
    .\tools\restore.ps1
    .\tools\restore.ps1 -RemoveLauncher
#>
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb,
    [string]$List,
    # Google TV devices ship a different launcher; check backup\<timestamp>\ or `cmd package query-activities` for its name.
    [string]$StockHome = "com.google.android.tvlauncher/.MainActivity",
    [string]$StockScreensaver = "com.google.android.backdrop/.Backdrop",
    [switch]$RemoveLauncher,
    [switch]$DryRun
)

. "$PSScriptRoot\common.ps1"
if (-not $List) { $List = $DefaultDeviceList }

$packages = Read-PackageList $List
$stockHomePackage = ($StockHome -split "/")[0]
if ($packages -notcontains $stockHomePackage) {
    Write-Host "Note: $stockHomePackage is not in $List; assuming it is already enabled."
}

Connect-Tv

Write-Host "`n== Re-enabling $($packages.Count) packages from $List"
foreach ($pkg in $packages) {
    Invoke-TvShell "pm enable $pkg" "new state: enabled"
}

Write-Host "`n== Stock launcher as home"
Invoke-TvShell "cmd package set-home-activity $StockHome" "Success"

Write-Host "`n== Settings"
Invoke-TvShell "settings put global window_animation_scale 1.0"
Invoke-TvShell "settings put global transition_animation_scale 1.0"
Invoke-TvShell "settings delete global animator_duration_scale"
Invoke-TvShell "settings put secure screensaver_components $StockScreensaver"

if ($RemoveLauncher) {
    Write-Host "`n== Removing tv-homemade"
    Invoke-TvShell "pm uninstall $LauncherPackage" "Success"
}

if (-not $DryRun) {
    Write-Host "`n== Verifying"
    $stillDisabled = (Get-TvShell "pm list packages -d") -split "`n" |
        ForEach-Object { ($_ -replace "^package:", "").Trim() } |
        Where-Object { $packages -contains $_ }
    if ($stillDisabled) { $script:failures += "Still disabled: $($stillDisabled -join ', ')" }

    $homeActivity = Get-HomeActivity
    if ($homeActivity -ne $StockHome) { $script:failures += "HOME resolves to '$homeActivity', expected $StockHome" }

    $screensaver = Get-TvShell "settings get secure screensaver_components"
    if ($screensaver -ne $StockScreensaver) { $script:failures += "Screensaver is '$screensaver', expected $StockScreensaver" }
}

Complete-Run "Restore complete. Press Home on the TV to see the stock launcher."
