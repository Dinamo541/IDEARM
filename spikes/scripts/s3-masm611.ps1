<#
.SYNOPSIS
  Spike F0-S3: MASM 6.11 fixtures using the hybrid strategy from the plan.
.DESCRIPTION
  1) ML.EXE (a Win32 binary) runs natively on the host: its output and exit code are captured directly.
  2) LINK.EXE (a 16-bit DOS binary) runs inside DOSBox in a single session.
  Uses the same sources as s2-tasm-dosbox.ps1.
.EXAMPLE
  ./s3-masm611.ps1
#>
param(
    [string]$MasmBin = 'C:\masm\MASM611\BIN',
    [string]$DosBox = 'C:\Program Files (x86)\DOSBox-0.74-3\dosbox.exe',
    [ValidateSet('dosbox074', 'staging', 'dosboxx')][string]$Dialect = 'dosbox074',
    [string]$Label = 'masm611-dosbox074',
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out'),
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')

$ws = New-SpikeWorkspace -OutRoot $OutRoot -Label $Label
Copy-SpikeSources -AsmDir (Join-Path $PSScriptRoot '..\asm') -Destination $ws.S

# 1) Native ML
$native = [Text.StringBuilder]::new()
$ml = Join-Path $MasmBin 'ML.EXE'
foreach ($case in 'HELLO', 'ERRSYM', 'ERRSYN', 'NOEND', 'EXTERN', 'CPU186', 'DUPA', 'DUPB', 'UTF8', 'LFONLY', 'WARN', 'NOSTK', 'LONGFILENAME') {
    $src = Join-Path $ws.S "$case.ASM"
    $obj = Join-Path $ws.C "OBJ\$case.OBJ"
    $lst = Join-Path $ws.C "LST\$case.LST"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    # /W3 enables every ML warning level
    $output = & $ml /nologo /c /Zi /W3 "/Fl$lst" "/Fo$obj" $src 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $watch.Stop()
    [void]$native.AppendLine(('--- ML {0} [exit={1}, {2:N0} ms]' -f $case, $exitCode, $watch.Elapsed.TotalMilliseconds))
    [void]$native.AppendLine($output.TrimEnd())
}

# 2) 16-bit LINK inside DOSBox (the trailing ';' stops LINK from prompting for missing fields)
$batch = [Collections.Generic.List[string]]::new()
$batch.AddRange([string[]]@('@echo off', 'set PATH=E:\', 'C:', 'cd \'))
Add-DosStep $batch 'HELLO_L' 'E:\LINK.EXE /CO /MAP C:\OBJ\HELLO.OBJ,C:\OUT\HELLO.EXE,C:\OUT\HELLO.MAP;'
Add-DosStep $batch 'EXTERN_L' 'E:\LINK.EXE /MAP C:\OBJ\EXTERN.OBJ,C:\OUT\EXTERN.EXE,C:\OUT\EXTERN.MAP;'
Add-DosStep $batch 'DUP_L' 'E:\LINK.EXE /MAP C:\OBJ\DUPA.OBJ+C:\OBJ\DUPB.OBJ,C:\OUT\DUP.EXE,C:\OUT\DUP.MAP;'
Add-DosStep $batch 'NOSTK_L' 'E:\LINK.EXE /MAP C:\OBJ\NOSTK.OBJ,C:\OUT\NOSTK.EXE,C:\OUT\NOSTK.MAP;'
Add-DosStep $batch 'HELLO_R' 'C:\OUT\HELLO.EXE'
$batch.AddRange([string[]]@('echo DONE>C:\LOG\DONE.TXT', 'exit'))
Write-DosFile -Path (Join-Path $ws.C 'LINK.BAT') -Lines $batch

$conf = Join-Path $ws.Root 'link.conf'
New-DosBoxConf -Path $conf -Autoexec @("mount E `"$MasmBin`"", "mount C `"$($ws.C)`"", 'C:', 'LINK.BAT')
$run = Invoke-DosBox -Dialect $Dialect -DosBox $DosBox -Conf $conf -WorkingDirectory $ws.HostDir -TimeoutSeconds $TimeoutSeconds

$header = @(
    "== $Label ==",
    'Native ML:',
    $native.ToString().TrimEnd(),
    '',
    "LINK inside DOSBox: $($run.CommandLine)",
    "Time: $($run.Seconds) s | finished: $($run.Finished) | DOSBox exit code: $($run.ExitCode)",
    "DONE.TXT: $(Test-Path (Join-Path $ws.C 'LOG\DONE.TXT'))"
) -join [Environment]::NewLine
Save-SpikeSummary -Workspace $ws -Text ($header + [Environment]::NewLine + (Format-SpikeLogs $ws))
