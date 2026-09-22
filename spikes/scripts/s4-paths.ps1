<#
.SYNOPSIS
  Spike F0-S4: host paths with spaces and non-ASCII characters, long file names and the LFN API in the DOSBox family.
.DESCRIPTION
  For each variant (DOSBox 0.74-3, Staging, DOSBox-X with and without lfn=true) and each conf encoding
  (ANSI code page 1252 and UTF-8):
  - mounts a folder named "My Drive\naïve café ñ" and assembles a file from it;
  - assembles and runs FOPEN.EXE, which tries to open 'inv_bottom.spr' through the classic API (3Dh) and the
    LFN API (716Ch), the same way Architecture_Project_2's loadmap.asm opens its resources.
.EXAMPLE
  ./s4-paths.ps1
#>
param(
    [string]$DosBox074 = 'C:\Program Files (x86)\DOSBox-0.74-3\dosbox.exe',
    [string]$Staging = 'C:\Codigo\Asembly\Turtoria\tools\dosbox\dosbox-staging-v0.82.2\dosbox.exe',
    [string]$DosBoxX = (Join-Path $env:LOCALAPPDATA 'IDEARM\tools\dosbox-x-2026.08.31\bin\x64\Release\dosbox-x.exe'),
    [string]$ToolDir = 'C:\Codigo\Asembly\Turtoria\tools\tasm\bin',
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out'),
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')
$ansi = [Text.Encoding]::GetEncoding(1252)
$utf8 = [Text.UTF8Encoding]::new($false)

$sessions = @(
    @{ Label = 's4-dosbox074-ansi'; Dialect = 'dosbox074'; DosBox = $DosBox074; Encoding = $ansi; Extra = @() },
    @{ Label = 's4-dosbox074-utf8'; Dialect = 'dosbox074'; DosBox = $DosBox074; Encoding = $utf8; Extra = @() },
    @{ Label = 's4-staging-ansi'; Dialect = 'staging'; DosBox = $Staging; Encoding = $ansi; Extra = @() },
    @{ Label = 's4-staging-utf8'; Dialect = 'staging'; DosBox = $Staging; Encoding = $utf8; Extra = @() },
    @{ Label = 's4-dosboxx-ansi'; Dialect = 'dosboxx'; DosBox = $DosBoxX; Encoding = $ansi; Extra = @() },
    @{ Label = 's4-dosboxx-utf8'; Dialect = 'dosboxx'; DosBox = $DosBoxX; Encoding = $utf8; Extra = @() },
    @{ Label = 's4-dosboxx-lfn'; Dialect = 'dosboxx'; DosBox = $DosBoxX; Encoding = $utf8; Extra = @('[dos]', 'lfn=true', '') }
)

$report = [Text.StringBuilder]::new()
foreach ($session in $sessions) {
    $ws = New-SpikeWorkspace -OutRoot $OutRoot -Label $session.Label
    Copy-SpikeSources -AsmDir (Join-Path $PSScriptRoot '..\asm') -Destination $ws.S

    $nonAsciiDir = Join-Path $ws.Root 'My Drive\naïve café ñ'
    New-Item -ItemType Directory -Force $nonAsciiDir | Out-Null
    Copy-Item (Join-Path $ws.S 'HELLO.ASM') $nonAsciiDir
    $resources = Join-Path $ws.Root 'D'
    New-Item -ItemType Directory -Force $resources | Out-Null
    [IO.File]::WriteAllText((Join-Path $resources 'inv_bottom.spr'), 'test sprite')

    $batch = [Collections.Generic.List[string]]::new()
    $batch.AddRange([string[]]@('@echo off', 'set PATH=T:\', 'C:', 'cd \', 'dir P:\ > C:\LOG\DIR-P.LOG', 'dir D:\ > C:\LOG\DIR-D.LOG'))
    Add-DosStep $batch 'ASM_P' 'T:\TASM.EXE P:\HELLO.ASM,C:\OBJ\HELLOP.OBJ'
    Add-DosStep $batch 'FOPEN_A' 'T:\TASM.EXE S:\FOPEN.ASM,C:\OBJ\FOPEN.OBJ'
    Add-DosStep $batch 'FOPEN_L' 'T:\TLINK.EXE C:\OBJ\FOPEN.OBJ,C:\OUT\FOPEN.EXE'
    # FOPEN runs with D: (the resources folder) as the current drive, like a game opening its sprites.
    $batch.AddRange([string[]]@('D:', 'C:\OUT\FOPEN.EXE > C:\LOG\FOPEN_R.LOG', 'C:', 'echo DONE>C:\LOG\DONE.TXT', 'exit'))
    Write-DosFile -Path (Join-Path $ws.C 'BUILD.BAT') -Lines $batch

    $conf = Join-Path $ws.Root 'build.conf'
    New-DosBoxConf -Path $conf -Encoding $session.Encoding -ExtraLines $session.Extra -Autoexec @(
        "mount T `"$ToolDir`"",
        "mount S `"$($ws.S)`"",
        "mount C `"$($ws.C)`"",
        "mount P `"$nonAsciiDir`"",
        "mount D `"$resources`"",
        'C:',
        'BUILD.BAT'
    )

    $run = Invoke-DosBox -Dialect $session.Dialect -DosBox $session.DosBox -Conf $conf -WorkingDirectory $ws.HostDir -TimeoutSeconds $TimeoutSeconds
    [void]$report.AppendLine("== $($session.Label) ($($run.Seconds) s, finished: $($run.Finished), DONE: $(Test-Path (Join-Path $ws.C 'LOG\DONE.TXT')))")
    foreach ($name in 'DIR-P', 'ASM_P', 'DIR-D', 'FOPEN_A', 'FOPEN_L', 'FOPEN_R') {
        [void]$report.AppendLine("--- $name [$(Read-DosText (Join-Path $ws.C "LOG\$name.RC"))]")
        [void]$report.AppendLine((Read-DosText (Join-Path $ws.C "LOG\$name.LOG")))
    }
    [void]$report.AppendLine()
}

$text = $report.ToString()
[IO.File]::WriteAllText((Join-Path ([IO.Path]::GetFullPath($OutRoot)) 's4-summary.txt'), $text, $utf8)
$text
