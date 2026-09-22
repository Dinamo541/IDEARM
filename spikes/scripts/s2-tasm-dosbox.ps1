<#
.SYNOPSIS
  Spike F0-S2/S3: assemble and link with TASM + TLINK inside DOSBox, without interaction.
.DESCRIPTION
  Copies the sources from spikes/asm, generates BUILD.BAT and a DOSBox conf, runs a single session, measures the
  time and summarizes logs, errorlevels and artifacts. It also tests long names (8.3 aliases), LF line endings,
  UTF-8 strings, read-only mounts (-ro) and redirecting the output of a DOS program.
.EXAMPLE
  ./s2-tasm-dosbox.ps1 -Dialect dosbox074 -DosBox 'C:\Program Files (x86)\DOSBox-0.74-3\dosbox.exe' `
      -ToolDir C:\Codigo\Asembly\Turtoria\tools\tasm\bin -Label tasm41-dosbox074
#>
param(
    [Parameter(Mandatory)][ValidateSet('dosbox074', 'staging', 'dosboxx')][string]$Dialect,
    [Parameter(Mandatory)][string]$DosBox,
    [Parameter(Mandatory)][string]$ToolDir,
    [Parameter(Mandatory)][string]$Label,
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out'),
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')

$ws = New-SpikeWorkspace -OutRoot $OutRoot -Label $Label
Copy-SpikeSources -AsmDir (Join-Path $PSScriptRoot '..\asm') -Destination $ws.S

$batch = [Collections.Generic.List[string]]::new()
$batch.AddRange([string[]]@('@echo off', 'set PATH=T:\', 'C:', 'cd \', 'dir S:\ > C:\LOG\DIR-S.LOG'))
foreach ($case in 'HELLO', 'ERRSYM', 'ERRSYN', 'NOEND', 'EXTERN', 'CPU186', 'DUPA', 'DUPB', 'UTF8', 'LFONLY', 'WARN', 'NOSTK') {
    # /w2 enables every TASM warning, which is what the IDE will use by default
    Add-DosStep $batch "${case}_A" "T:\TASM.EXE /zi /l /w2 S:\$case.ASM,C:\OBJ\$case.OBJ,C:\LST\$case.LST"
}
Add-DosStep $batch 'NOFILE_A' 'T:\TASM.EXE S:\NOFILE.ASM,C:\OBJ\NOFILE.OBJ'
Add-DosStep $batch 'LONG_A' 'T:\TASM.EXE S:\LONGFI~1.ASM,C:\OBJ\LONG.OBJ'
Add-DosStep $batch 'HELLO_L' 'T:\TLINK.EXE /v /m C:\OBJ\HELLO.OBJ,C:\OUT\HELLO.EXE,C:\OUT\HELLO.MAP'
Add-DosStep $batch 'EXTERN_L' 'T:\TLINK.EXE /v /m C:\OBJ\EXTERN.OBJ,C:\OUT\EXTERN.EXE,C:\OUT\EXTERN.MAP'
Add-DosStep $batch 'DUP_L' 'T:\TLINK.EXE /v /m C:\OBJ\DUPA.OBJ C:\OBJ\DUPB.OBJ,C:\OUT\DUP.EXE,C:\OUT\DUP.MAP'
Add-DosStep $batch 'NOSTK_L' 'T:\TLINK.EXE /m C:\OBJ\NOSTK.OBJ,C:\OUT\NOSTK.EXE,C:\OUT\NOSTK.MAP'
Add-DosStep $batch 'HELLO_R' 'C:\OUT\HELLO.EXE'
$batch.AddRange([string[]]@('dir R:\ > C:\LOG\DIR-R.LOG', 'echo x > R:\ROTEST.TXT', 'echo DONE>C:\LOG\DONE.TXT', 'exit'))
Write-DosFile -Path (Join-Path $ws.C 'BUILD.BAT') -Lines $batch

$conf = Join-Path $ws.Root 'build.conf'
New-DosBoxConf -Path $conf -Autoexec @(
    "mount T `"$ToolDir`"",
    "mount S `"$($ws.S)`"",
    "mount C `"$($ws.C)`"",
    "mount R `"$($ws.R)`" -ro",
    'C:',
    'BUILD.BAT'
)

$run = Invoke-DosBox -Dialect $Dialect -DosBox $DosBox -Conf $conf -WorkingDirectory $ws.HostDir -TimeoutSeconds $TimeoutSeconds
$readOnlyWrite = if (Test-Path (Join-Path $ws.R 'ROTEST.TXT')) { 'ALLOWED' } else { 'did not happen' }
$header = @(
    "== $Label ($Dialect) ==",
    "Command: $($run.CommandLine)",
    "Tools: $ToolDir",
    "Time: $($run.Seconds) s | finished: $($run.Finished) | DOSBox exit code: $($run.ExitCode)",
    "DONE.TXT: $(Test-Path (Join-Path $ws.C 'LOG\DONE.TXT'))",
    "Write to R: (mounted with -ro): $readOnlyWrite"
) -join [Environment]::NewLine
Save-SpikeSummary -Workspace $ws -Text ($header + [Environment]::NewLine + (Format-SpikeLogs $ws))
