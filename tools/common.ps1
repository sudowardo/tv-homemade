# Shared helpers for the tools\*.ps1 scripts. Dot-source it: . "$PSScriptRoot\common.ps1"
# The calling script must define $TvHost, $Adb and $DryRun (as parameters) before dot-sourcing.

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path $PSScriptRoot -Parent
$DefaultDeviceList = Join-Path $RepoRoot "devices\jvc-changhong-ai-pont\debloat.txt"
$LauncherPackage = "dev.tvhomemade.launcher"
$LauncherHome = "$LauncherPackage/.MainActivity"
$LauncherDream = "$LauncherPackage/.ClockDream"

# Never disabled, whatever a debloat list says: losing one of these means no picture, no remote,
# no Play apps or no way back over adb. Values say what breaks.
$ProtectedPackages = [ordered]@{
    "android"                                 = "core system"
    "com.android.systemui"                    = "system UI"
    "com.android.shell"                       = "adb shell"
    "com.android.providers.settings"          = "system settings storage"
    "com.android.providers.media"             = "media storage"
    "com.android.externalstorage"             = "shared storage"
    "com.google.android.permissioncontroller" = "permission prompts"
    "com.google.android.packageinstaller"     = "installing apps"
    "com.android.tv.settings"                 = "Settings app"
    "com.mediatek.wwtv.tvcenter"              = "Live TV and HDMI switching"
    "com.mediatek.tvinput"                    = "TV inputs (tuner, HDMI, AV)"
    "com.android.providers.tv"                = "TV channel storage"
    "com.android.bluetooth"                   = "Bluetooth remote"
    "com.google.android.tv.remote.service"    = "remote control service"
    "com.android.inputdevices"                = "input devices"
    "com.google.android.inputmethod.latin"    = "on-screen keyboard"
    "com.google.android.gms"                  = "Play services (YouTube, Netflix, ...)"
    "com.google.android.gsf"                  = "Google services framework"
    "com.android.vending"                     = "Play Store"
    "com.google.android.tungsten.setupwraith" = "setup after a factory reset"
    "com.mediatek.wwtv.setupwizard"           = "TV setup after a factory reset"
    $LauncherPackage                          = "tv-homemade launcher"
}

$script:failures = @()

function Resolve-Adb {
    if ($Adb) { return $Adb }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    # winget installs platform-tools without always refreshing PATH in already open shells.
    $winget = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages"
    $found = Get-ChildItem $winget -Directory -Filter "Google.PlatformTools*" -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "platform-tools\adb.exe" } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
    if ($found) { return $found }
    throw "adb not found. Install it with 'winget install Google.PlatformTools' or pass -Adb <path to adb.exe>."
}

function Connect-Tv {
    $script:adbPath = Resolve-Adb
    Write-Host "adb: $script:adbPath"
    if ($DryRun) { return }
    if (-not $TvHost) { throw "Pass -TvHost <ip>:5555 or set the TV_HOST environment variable." }
    & $script:adbPath connect $TvHost | Out-Host
    $state = (& $script:adbPath -s $TvHost get-state 2>&1) -join ""
    if ($state -ne "device") {
        throw "TV at $TvHost is not reachable over adb (state: $state). Is network debugging on, and was the prompt on the TV accepted?"
    }
}

# One shell command per adb call: chained pm commands can stop silently after the first.
function Invoke-TvShell([string]$Command, [string]$ExpectPattern) {
    if ($DryRun) {
        Write-Host "[dry-run] adb shell $Command"
        return
    }
    $output = (& $script:adbPath -s $TvHost shell $Command 2>&1) -join " "
    if ($LASTEXITCODE -ne 0 -or ($ExpectPattern -and $output -notmatch $ExpectPattern)) {
        $script:failures += "$Command -> $output"
        Write-Host "FAIL  $Command -> $output" -ForegroundColor Red
    } else {
        Write-Host "ok    $Command $output"
    }
}

function Get-TvShell([string]$Command) {
    $output = ((& $script:adbPath -s $TvHost shell $Command 2>&1) -join "`n").Trim()
    if ($LASTEXITCODE -ne 0) { throw "adb shell $Command failed: $output" }
    $output
}

function Get-HomeActivity {
    (Get-TvShell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") -split "\s+" |
        Select-Object -Last 1
}

function Read-PackageList([string]$Path) {
    if (-not (Test-Path $Path)) { throw "Package list not found: $Path" }
    $packages = Get-Content $Path |
        ForEach-Object { ($_ -replace "#.*$", "").Trim() } |
        Where-Object { $_ }
    foreach ($pkg in $packages) {
        if ($pkg -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$') { throw "Not a package name in ${Path}: '$pkg'" }
    }
    if (-not $packages) { throw "No packages listed in $Path" }
    @($packages)
}

function Assert-NoProtectedPackages([string[]]$Packages, [string]$Path) {
    $hits = $Packages | Where-Object { $ProtectedPackages.Contains($_) }
    if ($hits) {
        $details = ($hits | ForEach-Object { "  - $_ ($($ProtectedPackages[$_]))" }) -join "`n"
        throw "Refusing to run: $Path lists protected packages. Remove them from the list:`n$details"
    }
}

function Complete-Run([string]$SuccessMessage) {
    if ($script:failures.Count -gt 0) {
        Write-Host "`nFinished with $($script:failures.Count) problem(s):" -ForegroundColor Red
        $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
        exit 1
    }
    if ($DryRun) {
        Write-Host "`nDry run only, nothing was changed."
    } else {
        Write-Host "`n$SuccessMessage" -ForegroundColor Green
    }
    exit 0
}
