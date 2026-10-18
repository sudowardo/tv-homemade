<#
.SYNOPSIS
    Put a personal photo behind the tv-homemade home screen, or remove it.

.DESCRIPTION
    The photo is copied to the launcher's own storage on the TV, so it never goes into the APK or the repo.
    It is drawn about 350x400 dp right of the title: a portrait crop with faces in the upper half works best.
    Uninstalling the launcher deletes the photo; run this again after a reinstall.

.EXAMPLE
    .\tools\set-photo.ps1 -Path C:\Users\me\Pictures\family.jpg
    .\tools\set-photo.ps1 -Remove
#>
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb,
    [string]$Path,
    [switch]$Remove,
    [switch]$DryRun
)

. "$PSScriptRoot\common.ps1"

if ($Remove -eq [bool]$Path) { throw "Pass either -Path <photo> or -Remove." }
if ($Path -and -not (Test-Path $Path -PathType Leaf)) { throw "Photo not found: $Path" }

Connect-Tv

# Must match BackdropPhoto.FILE_NAME and Context.getExternalFilesDir(null) in the launcher.
$remoteDir = "/sdcard/Android/data/$LauncherPackage/files"
$remoteFile = "$remoteDir/backdrop.jpg"

if (-not $DryRun -and -not (Get-TvShell "pm list packages $LauncherPackage")) {
    throw "$LauncherPackage is not installed. Run tools\install.ps1 first."
}

if ($Remove) {
    Invoke-TvShell "rm -f $remoteFile"
} else {
    Invoke-TvShell "mkdir -p $remoteDir"
    if ($DryRun) {
        Write-Host "[dry-run] adb push $Path $remoteFile"
    } else {
        $output = (& $script:adbPath -s $TvHost push $Path $remoteFile 2>&1) -join " "
        if ($LASTEXITCODE -ne 0) { throw "Push failed: $output" }
        Write-Host "ok    push $output"
    }
}

# The launcher reads the photo once per process; if it is on screen, Android restarts it right away.
Invoke-TvShell "am force-stop $LauncherPackage"

Complete-Run "Done. The home screen shows the change the next time it opens."
