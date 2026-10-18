<#
.SYNOPSIS
    Back up the TV's package list and settings, then disable every package in a debloat list.

.DESCRIPTION
    Uses `pm disable-user --user 0` only: nothing is uninstalled, so tools\restore.ps1 can undo it.
    The backup goes to backup\<timestamp>\ (git-ignored: it contains the serial number and device ids).
    Refuses to run if the list contains the app currently set as home, so the TV is never left
    without a home screen; install tv-homemade first with tools\install.ps1.

.EXAMPLE
    .\tools\disable.ps1 -DryRun
    .\tools\disable.ps1 -List devices\jvc-changhong-ai-pont\debloat.txt
#>
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb,
    [string]$List,
    [switch]$DryRun
)

. "$PSScriptRoot\common.ps1"
if (-not $List) { $List = $DefaultDeviceList }

$packages = Read-PackageList $List
Connect-Tv

if (-not $DryRun) {
    $homeActivity = Get-HomeActivity
    $homePackage = ($homeActivity -split "/")[0]
    if ($packages -contains $homePackage) {
        throw "$homePackage is the current home app ($homeActivity) and is in the list. Run tools\install.ps1 first."
    }

    $backupDir = Join-Path $RepoRoot ("backup\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    New-Item -ItemType Directory -Force $backupDir | Out-Null
    $dumps = [ordered]@{
        "getprop.txt"             = "getprop"
        "packages-all.txt"        = "pm list packages -u"
        "packages-disabled.txt"   = "pm list packages -d"
        "packages-thirdparty.txt" = "pm list packages -3"
        "settings-global.txt"     = "settings list global"
        "settings-secure.txt"     = "settings list secure"
        "settings-system.txt"     = "settings list system"
    }
    foreach ($name in $dumps.Keys) {
        Get-TvShell $dumps[$name] | Set-Content (Join-Path $backupDir $name)
    }
    Write-Host "Backup written to $backupDir"
}

Write-Host "`n== Disabling $($packages.Count) packages from $List"
foreach ($pkg in $packages) {
    Invoke-TvShell "pm disable-user --user 0 $pkg" "new state: disabled-user"
}

Complete-Run "Done. Undo any time with tools\restore.ps1."
