<#
.SYNOPSIS
  Spike F0-S2b: response files to get around the 127-character DOS command-line limit.
.DESCRIPTION
  Generates 12 tiny modules (M01..M12) so that linking them together with HELLO needs more than 126 characters, and
  checks in one headless DOSBox-X session:
  - TASM reading its arguments from a response file;
  - TLINK with one long line in a response file, and with '+' continuation lines;
  - MASM 6.11's 16-bit LINK with a response file (objects assembled natively by ML beforehand).
.EXAMPLE
  ./s2b-response-files.ps1
#>
param(
    [string]$DosBoxX = (Join-Path $env:LOCALAPPDATA 'IDEARM\tools\dosbox-x-2026.08.31\bin\x64\Release\dosbox-x.exe'),
    [string]$TasmDir = 'C:\Codigo\Asembly\Turtoria\tools\tasm\bin',
    [string]$MasmBin = 'C:\masm\MASM611\BIN',
    [string]$Label = 'response-files',
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out'),
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')

$ws = New-SpikeWorkspace -OutRoot $OutRoot -Label $Label
Copy-SpikeSources -AsmDir (Join-Path $PSScriptRoot '..\asm') -Destination $ws.S
New-Item -ItemType Directory -Force (Join-Path $ws.C 'MOBJ') | Out-Null

$modules = 1..12 | ForEach-Object { 'M{0:D2}' -f $_ }
foreach ($module in $modules) {
    $lines = @("; $module.ASM - tiny module used to build a long link command", '.MODEL small', '.CODE',
        "PUBLIC P$module", "P$module PROC", '    ret', "P$module ENDP", 'END')
    Write-DosFile -Path (Join-Path $ws.S "$module.ASM") -Lines $lines
}
$all = @('HELLO') + $modules

# MASM objects: ML is a Win32 binary, so it runs natively.
$ml = Join-Path $MasmBin 'ML.EXE'
$mlLog = [Text.StringBuilder]::new()
foreach ($module in $all) {
    $output = & $ml /nologo /c "/Fo$(Join-Path $ws.C "MOBJ\$module.OBJ")" (Join-Path $ws.S "$module.ASM") 2>&1 | Out-String
    [void]$mlLog.AppendLine("--- ML $module [exit=$LASTEXITCODE] $($output.Trim())")
}

# Response files
$tasmObjects = ($all | ForEach-Object { "C:\OBJ\$_.OBJ" }) -join ' '
$directTlink = "T:\TLINK.EXE /v /m $tasmObjects,C:\OUT\RHELLO.EXE,C:\OUT\RHELLO.MAP"
Write-DosFile -Path (Join-Path $ws.C 'ASM.RSP') -Lines @('/zi /l /w2 S:\HELLO.ASM,C:\OBJ\HELLO.OBJ,C:\LST\HELLO.LST')
Write-DosFile -Path (Join-Path $ws.C 'LONG.RSP') -Lines @("/v /m $tasmObjects,C:\OUT\RHELLO.EXE,C:\OUT\RHELLO.MAP")

$plus = [Collections.Generic.List[string]]::new()
$plus.Add("/v /m C:\OBJ\$($all[0]).OBJ +")
for ($i = 1; $i -lt $all.Count - 1; $i++) { $plus.Add("C:\OBJ\$($all[$i]).OBJ +") }
$plus.Add("C:\OBJ\$($all[-1]).OBJ,C:\OUT\PHELLO.EXE,C:\OUT\PHELLO.MAP")
Write-DosFile -Path (Join-Path $ws.C 'PLUS.RSP') -Lines $plus

$mlink = [Collections.Generic.List[string]]::new()
for ($i = 0; $i -lt $all.Count - 1; $i++) { $mlink.Add("C:\MOBJ\$($all[$i]).OBJ+") }
$mlink.AddRange([string[]]@("C:\MOBJ\$($all[-1]).OBJ", 'C:\OUT\MHELLO.EXE', 'C:\OUT\MHELLO.MAP;'))
Write-DosFile -Path (Join-Path $ws.C 'MLINK.RSP') -Lines $mlink

$batch = [Collections.Generic.List[string]]::new()
$batch.AddRange([string[]]@('@echo off', 'set PATH=T:\', 'C:', 'cd \'))
foreach ($module in $modules) { Add-DosStep $batch "${module}_A" "T:\TASM.EXE S:\$module.ASM,C:\OBJ\$module.OBJ" }
Add-DosStep $batch 'RSP_A' 'T:\TASM.EXE @C:\ASM.RSP'
Add-DosStep $batch 'LONG_L' 'T:\TLINK.EXE @C:\LONG.RSP'
Add-DosStep $batch 'LONG_R' 'C:\OUT\RHELLO.EXE'
Add-DosStep $batch 'PLUS_L' 'T:\TLINK.EXE @C:\PLUS.RSP'
Add-DosStep $batch 'PLUS_R' 'C:\OUT\PHELLO.EXE'
Add-DosStep $batch 'MLINK_L' 'E:\LINK.EXE @C:\MLINK.RSP'
Add-DosStep $batch 'MLINK_R' 'C:\OUT\MHELLO.EXE'
$batch.AddRange([string[]]@('echo DONE>C:\LOG\DONE.TXT', 'exit'))
Write-DosFile -Path (Join-Path $ws.C 'BUILD.BAT') -Lines $batch

$conf = Join-Path $ws.Root 'build.conf'
New-DosBoxConf -Path $conf -Autoexec @(
    "mount T `"$TasmDir`" -ro",
    "mount E `"$MasmBin`" -ro",
    "mount S `"$($ws.S)`" -ro",
    "mount C `"$($ws.C)`"",
    'C:',
    'BUILD.BAT'
)
$run = Invoke-DosBox -Dialect dosboxx -DosBox $DosBoxX -Conf $conf -WorkingDirectory $ws.HostDir -TimeoutSeconds $TimeoutSeconds

$header = @(
    "== $Label (dosboxx) ==",
    "A direct TLINK command for these $($all.Count) objects would be $($directTlink.Length) characters (DOS limit: 126).",
    "Time: $($run.Seconds) s | finished: $($run.Finished) | DOSBox exit code: $($run.ExitCode)",
    "DONE.TXT: $(Test-Path (Join-Path $ws.C 'LOG\DONE.TXT'))",
    'Native ML:',
    $mlLog.ToString().TrimEnd()
) -join [Environment]::NewLine
Save-SpikeSummary -Workspace $ws -Text ($header + [Environment]::NewLine + (Format-SpikeLogs $ws))
