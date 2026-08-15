param(
    [string]$Version = "",
    [string]$AppName = "Boquila",
    [string]$MainClass = "com.example.workreport.Main",
    [string]$MinGitVersion = "2.52.0",
    [string]$WiXVersion = "3.14.1",
    [string]$CacheDir = $(Join-Path $env:LOCALAPPDATA "Boquila\build-cache"),
    [string]$Types = "msi,exe"
)

$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$icon = Join-Path $root "packaging\icons\boquila.ico"

if ([string]::IsNullOrWhiteSpace($Version)) {
    Push-Location $root
    $Version = (& mvn -q help:evaluate "-Dexpression=project.version" "-DforceStdout").Trim()
    Pop-Location
    if ($LASTEXITCODE -ne 0) { throw "Could not read project version from pom.xml" }
}

$jarName = "workreport-$Version.jar"
$staging = Join-Path $root "target\packaging\staging"
$dist = Join-Path $root "target\dist"

function Invoke-Step([string]$name, [scriptblock]$body) {
    Write-Host "== $name =="
    & $body
    if (-not $?) { throw "Step failed: $name" }
}

if (-not (Test-Path $icon)) {
    & (Join-Path $PSScriptRoot "generate-icons.ps1")
}

Invoke-Step "Build jar" {
    Push-Location $root
    mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    Pop-Location
}

if (-not (Test-Path (Join-Path $root "target\$jarName"))) {
    throw "Expected jar target\$jarName not found"
}

Invoke-Step "Prepare staging dir" {
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
    New-Item -ItemType Directory -Path $staging | Out-Null
    Copy-Item (Join-Path $root "target\$jarName") $staging

    Push-Location $root
    mvn -q dependency:copy-dependencies `
        "-DincludeClassifiers=win" `
        "-DoutputDirectory=$staging"
    if ($LASTEXITCODE -ne 0) { throw "mvn copy-dependencies failed" }
    Pop-Location

    $javafxJars = Get-ChildItem $staging -Filter "javafx-*-win.jar"
    if ($javafxJars.Count -lt 3) {
        throw "Expected javafx win classifier jars in staging, found $($javafxJars.Count)"
    }
}

Invoke-Step "Bundle MinGit (portable git)" {
    $mingitDir = Join-Path $CacheDir "MinGit-$MinGitVersion"
    if (-not (Test-Path (Join-Path $mingitDir "cmd\git.exe"))) {
        $zip = Join-Path $CacheDir "MinGit-$MinGitVersion-64-bit.zip"
        if (-not (Test-Path $zip)) {
            New-Item -ItemType Directory -Path $CacheDir -Force | Out-Null
            curl.exe -L --fail --retry 3 -o $zip `
                "https://github.com/git-for-windows/git/releases/download/v$MinGitVersion.windows.1/MinGit-$MinGitVersion-64-bit.zip"
            if ($LASTEXITCODE -ne 0) { throw "MinGit download failed" }
        }
        Expand-Archive $zip $mingitDir
    }
    Copy-Item $mingitDir (Join-Path $staging "git") -Recurse
}

Invoke-Step "Ensure WiX $WiXVersion toolchain" {
    $wixTools = Join-Path $CacheDir "wix-$WiXVersion\tools"
    if (-not (Test-Path (Join-Path $wixTools "candle.exe"))) {
        $nupkg = Join-Path $CacheDir "wix.$WiXVersion.nupkg"
        if (-not (Test-Path $nupkg)) {
            curl.exe -L --fail --retry 3 -o $nupkg `
                "https://api.nuget.org/v3-flatcontainer/wix/$WiXVersion/wix.$WiXVersion.nupkg"
            if ($LASTEXITCODE -ne 0) { throw "WiX download failed" }
        }
        $extractDir = Join-Path $CacheDir "wix-$WiXVersion"
        New-Item -ItemType Directory -Path $extractDir -Force | Out-Null
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($nupkg, $extractDir)
    }
    if (-not (Test-Path (Join-Path $wixTools "candle.exe"))) {
        throw "WiX candle.exe not found after extraction"
    }
    $env:PATH = "$wixTools;$env:PATH"
}

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist | Out-Null

foreach ($type in ($Types -split ',')) {
    Invoke-Step "jpackage --type $type" {
        jpackage --type $type `
            --input $staging `
            --main-jar $jarName `
            --main-class $MainClass `
            --module-path $staging `
            --add-modules javafx.controls `
            --name $AppName `
            --app-version $Version `
            --vendor "Boquila" `
            --icon $icon `
            --win-shortcut `
            --win-menu `
            --win-menu-group $AppName `
            --dest $dist
        if ($LASTEXITCODE -ne 0) { throw "jpackage --type $type failed" }
    }
}

Write-Host ""
Write-Host "== Verification =="
Get-ChildItem $dist | ForEach-Object {
    $ok = $false
    if ($_.Extension -eq ".msi") {
        $wi = New-Object -ComObject WindowsInstaller.Installer
        $db = $wi.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $wi, @($_.FullName, 0))
        $view = $db.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $db, @("SELECT Value FROM Property WHERE Property='ProductName'"))
        $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, @())
        $record = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, @())
        $product = $record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @(1))
        $ok = ($product -eq $AppName)
        Write-Host ("{0} ({1:N1} MB) -> MSI ProductName='{2}' {3}" -f $_.Name, ($_.Length/1MB), $product, $(if ($ok) { 'OK' } else { 'MISMATCH' }))
    } elseif ($_.Extension -eq ".exe") {
        $bytes = [System.IO.File]::ReadAllBytes($_.FullName)[0..1]
        $ok = ([System.Text.Encoding]::ASCII.GetString($bytes) -eq "MZ")
        Write-Host ("{0} ({1:N1} MB) -> PE header {2}" -f $_.Name, ($_.Length/1MB), $(if ($ok) { 'OK' } else { 'INVALID' }))
    }
    if (-not $ok) { throw "Verification failed for $($_.Name)" }
}

Write-Host ""
Write-Host "Installers ready:"
Get-ChildItem $dist | ForEach-Object { Write-Host ("  {0}" -f $_.FullName) }