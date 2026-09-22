#Requires -Version 7
<#
.SYNOPSIS
    Builds the IDEARM release packages for the current operating system with jlink and jpackage.

.DESCRIPTION
    Windows: IDEARM-<version>-windows-x64.msi (per-user installer, needs the WiX Toolset) and
    IDEARM-<version>-windows-x64-portable.zip.
    Linux:   IDEARM-<version>-linux-x64.deb (needs dpkg-deb and fakeroot) and IDEARM-<version>-linux-x64.tar.gz.
    Each package goes to dist/release with a .sha256 file next to it.

    The editor libraries (RichTextFX, Flowless, UndoFX, ReactFX, WellBehavedFX) are automatic modules, which jlink
    refuses. The runtime therefore holds only JDK modules, and jpackage copies every jar to app/mods, where both
    launchers (IDEARM and idearm-cli) load them from the module path (spike S8, spikes/REPORT.md).

.PARAMETER Type
    all (default): the portable archive and the installer. app-image: only the portable archive.
    installer: only the installer.

.PARAMETER SkipBuild
    Use the jars already built by "mvn package" instead of building them again.
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'app-image', 'installer')]
    [string]$Type = 'all',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows -and -not $IsLinux) {
    throw 'The release packages are built on Windows or Linux.'
}

$root = Split-Path -Parent $PSScriptRoot
# What the jars require (jdeps and their module descriptors, spike S8), plus jdk.charsets for Windows-1252 sources
# on Linux and jdk.localedata for the Spanish interface.
$jdkModules = 'java.base,java.desktop,java.logging,java.xml,jdk.unsupported,jdk.charsets,jdk.localedata'
# Identifies the product across versions, so a newer MSI replaces the installed one. Never change it.
$upgradeUuid = '96a637cf-b790-4ee1-a31b-340b46de4f5e'
$appModule = 'io.github.dinamo541.idearm.app/io.github.dinamo541.idearm.app.App'
$cliModule = 'io.github.dinamo541.idearm.cli/io.github.dinamo541.idearm.cli.Main'
# JavaFX and the Windows Job Object / 8.3-name code call native functions (FFM).
$nativeAccess = '--enable-native-access=javafx.graphics,io.github.dinamo541.idearm.infrastructure'
$aboutUrl = 'https://github.com/Dinamo541/IDEARM'

# A JDK tool from JAVA_HOME when it is set (CI), otherwise from PATH.
function Get-JdkTool([string]$name) {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin' ($IsWindows ? "$name.exe" : $name)
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    return $name
}

function Invoke-Checked([string]$tool, [string[]]$arguments) {
    & $tool @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$tool failed with exit code $LASTEXITCODE."
    }
}

$version = ([xml](Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw)).project.version
# jpackage and Windows Installer accept only numbers: 1.1.0-SNAPSHOT is packaged as 1.1.0.
$appVersion = ($version -split '-')[0]
$platform = $IsWindows ? 'windows-x64' : 'linux-x64'
$prefix = "IDEARM-$version-$platform"
Write-Host "Packaging IDEARM $version for $platform ($Type)" -ForegroundColor Cyan

if ($Type -ne 'app-image') {
    if ($IsWindows -and -not (Get-Command wix -ErrorAction SilentlyContinue) `
            -and -not (Get-Command candle -ErrorAction SilentlyContinue)) {
        throw 'The MSI needs the WiX Toolset (wix.exe v4/v5, or candle.exe v3). Use -Type app-image for the portable zip only.'
    }
    if ($IsLinux) {
        foreach ($tool in 'dpkg-deb', 'fakeroot') {
            if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
                throw "The .deb package needs $tool (sudo apt install $tool)."
            }
        }
    }
}

if (-not $SkipBuild) {
    Write-Host 'Building the jars (mvn clean package -DskipTests)...' -ForegroundColor Cyan
    Invoke-Checked 'mvn' @('-B', '-q', 'clean', 'package', '-DskipTests')
}

$work = Join-Path $root 'target' 'release'
if (Test-Path -LiteralPath $work) {
    Remove-Item -LiteralPath $work -Recurse -Force
}
$mods = New-Item -ItemType Directory -Force (Join-Path $work 'mods')
$jars = @(
    Get-Item -LiteralPath (Join-Path $root 'idearm-app' 'target' "idearm-app-$version.jar")
    Get-Item -LiteralPath (Join-Path $root 'idearm-cli' 'target' "idearm-cli-$version.jar")
    Get-ChildItem -Path (Join-Path $root 'idearm-app' 'target' 'lib') -Filter '*.jar'
    Get-ChildItem -Path (Join-Path $root 'idearm-cli' 'target' 'lib') -Filter '*.jar'
)
foreach ($jar in $jars) {
    # javafx-base-25.0.2.jar and the like are empty placeholders for the platform jars (javafx-base-25.0.2-win.jar).
    $placeholder = $jar.Name -match '^javafx-[a-z]+-[0-9.]+\.jar$'
    # A lib folder not cleaned since an older version still holds that version's IDEARM jars.
    $stale = $jar.Name -like 'idearm-*' -and -not $jar.Name.EndsWith("-$version.jar")
    if (-not $placeholder -and -not $stale) {
        Copy-Item -LiteralPath $jar.FullName -Destination $mods -Force
    }
}

Write-Host 'Linking the Java runtime...' -ForegroundColor Cyan
$runtime = Join-Path $work 'runtime'
Invoke-Checked (Get-JdkTool 'jlink') @('--add-modules', $jdkModules, '--include-locales=en,es', '--strip-debug',
    '--no-header-files', '--no-man-pages', '--output', $runtime)

# The command-line launcher: a console program on Windows, and no menu entry or shortcut of its own.
$cliLauncher = Join-Path $work 'idearm-cli.properties'
Set-Content -LiteralPath $cliLauncher -Encoding ascii -Value @(
    "module=$cliModule"
    'java-options=--enable-native-access=io.github.dinamo541.idearm.infrastructure'
    'win-console=true'
    'win-shortcut=false'
    'win-menu=false'
    'linux-shortcut=false'
)

$branding = Join-Path $root 'idearm-app' 'src' 'main' 'resources' 'io' 'github' 'dinamo541' 'idearm' 'app' 'branding'
$common = @(
    '--name', 'IDEARM',
    '--app-version', $appVersion,
    '--vendor', 'IDEARM',
    '--copyright', 'Copyright (c) 2026 Dominique Mariano Castro',
    '--description', 'Desktop IDE for x86 Assembly'
)

# The installer is built from these same options rather than from the finished image: an image only remembers the
# name of its extra launcher, so idearm-cli would get a menu entry of its own.
$payload = @(
    '--runtime-image', $runtime,
    '--module-path', $mods.FullName,
    '--module', $appModule,
    '--java-options', $nativeAccess,
    '--icon', (Join-Path $branding ($IsWindows ? 'idearm.ico' : 'idearm.png')),
    '--app-content', ((Join-Path $root 'LICENSE') + ',' + (Join-Path $root 'THIRD-PARTY-NOTICES.txt')),
    '--add-launcher', "idearm-cli=$cliLauncher"
)

$dist = New-Item -ItemType Directory -Force (Join-Path $root 'dist' 'release')
$packages = @()

if ($Type -ne 'installer') {
    Write-Host 'Creating the application image...' -ForegroundColor Cyan
    $images = Join-Path $work 'image'
    Invoke-Checked (Get-JdkTool 'jpackage') (@('--type', 'app-image') + $common + @('--dest', $images) + $payload)
    $appImage = Join-Path $images 'IDEARM'
    if ($IsWindows) {
        $zip = Join-Path $dist "$prefix-portable.zip"
        Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::CreateFromDirectory($appImage, $zip, 'Optimal', $true)
        $packages += $zip
    } else {
        # tar keeps the executable bits of bin/IDEARM and bin/idearm-cli.
        $tarball = Join-Path $dist "$prefix.tar.gz"
        Invoke-Checked 'tar' @('-czf', $tarball, '-C', $images, 'IDEARM')
        $packages += $tarball
    }
}

if ($Type -ne 'app-image') {
    Write-Host 'Creating the installer...' -ForegroundColor Cyan
    $installers = Join-Path $work 'installer'
    if ($IsWindows) {
        # Per user: no administrator rights, installed like VS Code in %LOCALAPPDATA%\Programs.
        Invoke-Checked (Get-JdkTool 'jpackage') (@('--type', 'msi') + $common + $payload + @(
            '--dest', $installers,
            '--about-url', $aboutUrl,
            '--win-per-user-install',
            '--install-dir', 'Programs\IDEARM',
            '--win-menu', '--win-menu-group', 'IDEARM',
            '--win-shortcut',
            '--win-dir-chooser',
            '--win-upgrade-uuid', $upgradeUuid
        ))
        $built = Get-ChildItem -Path $installers -Filter '*.msi' | Select-Object -First 1
        $target = Join-Path $dist "$prefix.msi"
    } else {
        # apt installs the tools the IDE drives; TASM and MASM are proprietary and never bundled (ADR-004).
        Invoke-Checked (Get-JdkTool 'jpackage') (@('--type', 'deb') + $common + $payload + @(
            '--dest', $installers,
            '--about-url', $aboutUrl,
            '--linux-package-name', 'idearm',
            '--linux-deb-maintainer', 'Dinamo541@users.noreply.github.com',
            '--linux-shortcut',
            '--linux-menu-group', 'Development;Education;',
            '--linux-app-category', 'devel',
            # libgtk-3-0 is what JavaFX draws with; every desktop has it, a bare system does not.
            '--linux-package-deps', 'libgtk-3-0, dosbox, nasm, binutils, gdb'
        ))
        $built = Get-ChildItem -Path $installers -Filter '*.deb' | Select-Object -First 1
        $target = Join-Path $dist "$prefix.deb"
    }
    Move-Item -LiteralPath $built.FullName -Destination $target -Force
    $packages += $target
}

foreach ($package in $packages) {
    $name = Split-Path -Leaf $package
    $hash = (Get-FileHash -LiteralPath $package -Algorithm SHA256).Hash.ToLowerInvariant()
    # The format sha256sum --check reads.
    Set-Content -LiteralPath "$package.sha256" -Encoding ascii -Value "$hash  $name"
    Write-Host ("{0} ({1:N1} MB)" -f $name, ((Get-Item -LiteralPath $package).Length / 1MB)) -ForegroundColor Green
}
