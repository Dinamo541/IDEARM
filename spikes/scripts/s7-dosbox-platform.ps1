# S7 - DOSBox secure mode, invisible builds, memory limits and MASM inside DOSBox (Windows).
# Not product code. Results go to spikes/out/s7-* and are summarized in spikes/REPORT.md.

param(
    [string]$DosBox074 = 'C:\Program Files (x86)\DOSBox-0.74-3\DOSBox.exe',
    [string]$DosBoxX = "$env:LOCALAPPDATA\IDEARM\tools\dosbox-x-2026.08.31\bin\x64\Release\dosbox-x.exe",
    [string]$Staging = 'C:\Codigo\Asembly\Turtoria\tools\dosbox\dosbox-staging-v0.82.2\dosbox.exe',
    [string]$TasmBin = 'C:\Codigo\Asembly\Turtoria\tools\tasm\bin',
    [string]$MasmBin = 'C:\masm\MASM611\BIN',
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out')
)

. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')
$asm = Join-Path $PSScriptRoot '..\asm'

$dialects = @(
    @{ Name = 'dosbox074'; Exe = $DosBox074 },
    @{ Name = 'dosboxx'; Exe = $DosBoxX },
    @{ Name = 'staging'; Exe = $Staging }
)

function Start-Observed {
    # Runs DOSBox and records whether it ever showed a top-level window.
    param([string]$Dialect, [string]$Exe, [string]$Conf, [string]$WorkingDirectory, [hashtable]$Environment = @{})
    $quotedConf = '"' + $Conf + '"'
    $arguments = switch ($Dialect) {
        'dosbox074' { '-conf', $quotedConf, '-exit', '-noconsole' }
        'staging' { '-noprimaryconf', '-nolocal', '-conf', $quotedConf, '-exit' }
        'dosboxx' { '-silent', '-exit', '-fastlaunch', '-conf', $quotedConf }
    }
    $saved = @{}
    foreach ($key in $Environment.Keys) { $saved[$key] = [Environment]::GetEnvironmentVariable($key); [Environment]::SetEnvironmentVariable($key, $Environment[$key]) }
    try {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $process = Start-Process -FilePath $Exe -ArgumentList $arguments -WorkingDirectory $WorkingDirectory -PassThru
        $null = $process.Handle
        $window = $false
        while (-not $process.HasExited -and $watch.Elapsed.TotalSeconds -lt 60) {
            Start-Sleep -Milliseconds 50
            try { $process.Refresh(); if ($process.MainWindowHandle -ne [IntPtr]::Zero) { $window = $true } } catch { }
        }
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
        [pscustomobject]@{ Seconds = [math]::Round($watch.Elapsed.TotalSeconds, 2); Window = $window; ExitCode = $process.ExitCode }
    } finally {
        foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key]) }
    }
}

$report = [Text.StringBuilder]::new()

# 1. Secure mode: a batch tries to mount a host folder after the drives are set up.
foreach ($d in $dialects) {
    foreach ($secure in $false, $true) {
        $ws = New-SpikeWorkspace -OutRoot $OutRoot -Label ("s7-secure-{0}-{1}" -f $d.Name, $(if ($secure) { 'on' } else { 'off' }))
        Set-Content -Path (Join-Path $ws.HostDir 'PROBE.TXT') -Value 'host file' -Encoding ascii
        Write-DosFile -Path (Join-Path $ws.C 'TEST.BAT') -Lines @(
            '@echo off',
            "mount D `"$($ws.HostDir)`" > C:\LOG\MOUNT.LOG",
            'set R=NOTMOUNTED',
            'if exist D:\PROBE.TXT set R=MOUNTED',
            'echo %R%>C:\LOG\RESULT.TXT',
            'echo RAN>C:\LOG\RAN.TXT')
        $autoexec = @("mount C `"$($ws.C)`"", 'C:')
        if ($secure) { $autoexec += 'config -securemode' }
        $autoexec += @('call TEST.BAT', 'exit')
        $conf = Join-Path $ws.Root 'test.conf'
        New-DosBoxConf -Path $conf -Autoexec $autoexec
        $run = Start-Observed -Dialect $d.Name -Exe $d.Exe -Conf $conf -WorkingDirectory $ws.Root
        [void]$report.AppendLine(("secure {0,-9} {1,-3}: result={2} ran={3} exited={4}s | mount says: {5}" -f $d.Name, $(if ($secure) { 'on' } else { 'off' }),
            (Read-DosText "$($ws.C)\LOG\RESULT.TXT"), (Read-DosText "$($ws.C)\LOG\RAN.TXT"), $run.Seconds,
            ((Read-DosText "$($ws.C)\LOG\MOUNT.LOG") -replace "`r?`n", ' / ')))
    }
}

# 2. Invisible builds: TASM HELLO with and without SDL_VIDEODRIVER=dummy.
foreach ($d in $dialects) {
    foreach ($dummy in $false, $true) {
        $ws = New-SpikeWorkspace -OutRoot $OutRoot -Label ("s7-build-{0}-{1}" -f $d.Name, $(if ($dummy) { 'dummy' } else { 'default' }))
        Copy-SpikeSources -AsmDir $asm -Destination $ws.S
        $batch = [Collections.Generic.List[string]]::new()
        $batch.Add('@echo off')
        Add-DosStep -Batch $batch -Name 'TASM' -Command 'T:\TASM.EXE /zi S:\HELLO.ASM,C:\OBJ\HELLO.OBJ'
        Add-DosStep -Batch $batch -Name 'TLINK' -Command 'T:\TLINK.EXE /v C:\OBJ\HELLO.OBJ,C:\OUT\HELLO.EXE'
        $batch.Add('echo DONE>C:\LOG\DONE.TXT')
        Write-DosFile -Path (Join-Path $ws.C 'BUILD.BAT') -Lines $batch
        $conf = Join-Path $ws.Root 'build.conf'
        New-DosBoxConf -Path $conf -ExtraLines @('[mixer]', 'nosound=true', '') -Autoexec @(
            "mount T `"$TasmBin`"", "mount S `"$($ws.S)`"", "mount C `"$($ws.C)`"", 'C:', 'config -securemode', 'call BUILD.BAT', 'exit')
        $env = if ($dummy) { @{ SDL_VIDEODRIVER = 'dummy'; SDL_AUDIODRIVER = 'dummy' } } else { @{} }
        $run = Start-Observed -Dialect $d.Name -Exe $d.Exe -Conf $conf -WorkingDirectory $ws.Root -Environment $env
        [void]$report.AppendLine(("build  {0,-9} {1,-7}: window={2} exe={3} done={4} tasm={5} {6}s exit={7}" -f $d.Name, $(if ($dummy) { 'dummy' } else { 'default' }),
            $run.Window, (Test-Path "$($ws.C)\OUT\HELLO.EXE"), (Read-DosText "$($ws.C)\LOG\DONE.TXT"),
            (Read-DosText "$($ws.C)\LOG\TASM.RC"), $run.Seconds, $run.ExitCode))
    }
}

# 3. Memory: what MEM reports for memsize 63 and 64.
foreach ($d in $dialects) {
    foreach ($size in 63, 64) {
        $ws = New-SpikeWorkspace -OutRoot $OutRoot -Label ("s7-mem-{0}-{1}" -f $d.Name, $size)
        $conf = Join-Path $ws.Root 'mem.conf'
        $lines = @('[sdl]', 'fullscreen=false', '', '[dosbox]', "memsize=$size", '', '[autoexec]', "mount C `"$($ws.C)`"", 'C:', 'mem > C:\LOG\MEM.LOG', 'exit')
        Write-DosFile -Path $conf -Lines $lines
        $null = Start-Observed -Dialect $d.Name -Exe $d.Exe -Conf $conf -WorkingDirectory $ws.Root -Environment @{ SDL_VIDEODRIVER = 'dummy' }
        $extended = (Read-DosText "$($ws.C)\LOG\MEM.LOG") -split "`r?`n" | Where-Object { $_ -match 'xtended' } | Select-Object -First 1
        [void]$report.AppendLine(("memsize {0,-9} {1}: {2}" -f $d.Name, $size, $extended))
    }
}

# 4. MASM 6.11 ML.EXE inside DOSBox 0.74-3 (DOSXNT extender).
$ws = New-SpikeWorkspace -OutRoot $OutRoot -Label 's7-ml-dosbox074'
Copy-SpikeSources -AsmDir $asm -Destination $ws.S
$batch = [Collections.Generic.List[string]]::new()
$batch.Add('@echo off')
$batch.Add('set PATH=T:\')
Add-DosStep -Batch $batch -Name 'ML' -Command 'T:\ML.EXE /c /Zi /FoC:\OBJ\HELLO.OBJ S:\HELLO.ASM'
Add-DosStep -Batch $batch -Name 'MLERR' -Command 'T:\ML.EXE /c /FoC:\OBJ\ERRSYM.OBJ S:\ERRSYM.ASM'
$batch.Add('echo DONE>C:\LOG\DONE.TXT')
Write-DosFile -Path (Join-Path $ws.C 'BUILD.BAT') -Lines $batch
$conf = Join-Path $ws.Root 'build.conf'
New-DosBoxConf -Path $conf -Autoexec @("mount T `"$MasmBin`"", "mount S `"$($ws.S)`"", "mount C `"$($ws.C)`"", 'C:', 'call BUILD.BAT', 'exit')
$run = Start-Observed -Dialect 'dosbox074' -Exe $DosBox074 -Conf $conf -WorkingDirectory $ws.Root -Environment @{ SDL_VIDEODRIVER = 'dummy'; SDL_AUDIODRIVER = 'dummy' }
[void]$report.AppendLine(("ml-in-dosbox074: obj={0} ml={1} mlerr={2} {3}s" -f (Test-Path "$($ws.C)\OBJ\HELLO.OBJ"),
    (Read-DosText "$($ws.C)\LOG\ML.RC"), (Read-DosText "$($ws.C)\LOG\MLERR.RC"), $run.Seconds))
[void]$report.AppendLine('--- ML.LOG'); [void]$report.AppendLine((Read-DosText "$($ws.C)\LOG\ML.LOG"))
[void]$report.AppendLine('--- MLERR.LOG'); [void]$report.AppendLine((Read-DosText "$($ws.C)\LOG\MLERR.LOG"))

$summary = $report.ToString()
[IO.File]::WriteAllText((Join-Path ([IO.Path]::GetFullPath($OutRoot)) 's7-summary.txt'), $summary, $script:Utf8NoBom)
$summary
