# Helpers shared by the Phase 0 spikes. Not product code.

Set-StrictMode -Version Latest
[Text.Encoding]::RegisterProvider([Text.CodePagesEncodingProvider]::Instance)
$script:Cp437 = [Text.Encoding]::GetEncoding(437)
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)

function New-SpikeWorkspace {
    # Clean working folder: C: (outputs and logs), S: (sources), R: (read-only mount test).
    param([Parameter(Mandatory)][string]$OutRoot, [Parameter(Mandatory)][string]$Label)
    $root = Join-Path ([IO.Path]::GetFullPath($OutRoot)) $Label
    if (Test-Path $root) { Remove-Item $root -Recurse -Force }
    $ws = [pscustomobject]@{
        Root    = $root
        C       = Join-Path $root 'C'
        S       = Join-Path $root 'S'
        R       = Join-Path $root 'R'
        HostDir = Join-Path $root 'host'
    }
    foreach ($dir in $ws.S, $ws.R, $ws.HostDir, "$($ws.C)\OBJ", "$($ws.C)\LST", "$($ws.C)\OUT", "$($ws.C)\LOG") {
        New-Item -ItemType Directory -Force $dir | Out-Null
    }
    $ws
}

function Copy-SpikeSources {
    # Copies spikes/asm with CRLF and UTF-8 without BOM (as the IDE would save them) and adds two variants of
    # HELLO.ASM: LFONLY.ASM (LF line endings) and LONGFILENAME.ASM (a name that is not 8.3).
    param([Parameter(Mandatory)][string]$AsmDir, [Parameter(Mandatory)][string]$Destination)
    foreach ($file in Get-ChildItem $AsmDir -Filter *.ASM) {
        $text = [IO.File]::ReadAllText($file.FullName) -replace "`r?`n", "`r`n"
        [IO.File]::WriteAllText((Join-Path $Destination $file.Name.ToUpperInvariant()), $text, $script:Utf8NoBom)
    }
    $hello = [IO.File]::ReadAllText((Join-Path $Destination 'HELLO.ASM'))
    [IO.File]::WriteAllText((Join-Path $Destination 'LFONLY.ASM'), ($hello -replace "`r`n", "`n"), $script:Utf8NoBom)
    [IO.File]::WriteAllText((Join-Path $Destination 'LONGFILENAME.ASM'), $hello, $script:Utf8NoBom)
}

function Add-DosStep {
    # Runs a command redirecting its output to C:\LOG\<Name>.LOG and stores the errorlevel in <Name>.RC.
    # The name must be 8.3: DOS does not allow two dots in a file name.
    param(
        [Parameter(Mandatory)][Collections.Generic.List[string]]$Batch,
        [Parameter(Mandatory)][ValidatePattern('^[A-Z0-9_-]{1,8}$')][string]$Name,
        [Parameter(Mandatory)][string]$Command
    )
    $Batch.Add("$Command > C:\LOG\$Name.LOG")
    $Batch.Add('set RC=0')
    foreach ($level in 1, 2, 3, 4, 255) { $Batch.Add("if errorlevel $level set RC=$level") }
    $Batch.Add("echo RC=%RC%>C:\LOG\$Name.RC")
}

function Write-DosFile {
    # Files read by DOS: CRLF and ASCII, unless another encoding is requested (e.g. non-ASCII paths in a conf).
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyString()][string[]]$Lines,
        [Text.Encoding]$Encoding = [Text.Encoding]::ASCII
    )
    [IO.File]::WriteAllText($Path, (($Lines -join "`r`n") + "`r`n"), $Encoding)
}

function New-DosBoxConf {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string[]]$Autoexec,
        [AllowEmptyString()][string[]]$ExtraLines = @(),
        [Text.Encoding]$Encoding = [Text.Encoding]::ASCII
    )
    $lines = @(
        '[sdl]', 'fullscreen=false', '',
        '[dosbox]', 'memsize=16', '',
        '[cpu]', 'core=auto', 'cycles=max', '',
        '[serial]', 'serial1=disabled', 'serial2=disabled', ''
    ) + $ExtraLines + @('[autoexec]') + $Autoexec
    Write-DosFile -Path $Path -Lines $lines -Encoding $Encoding
}

function Invoke-DosBox {
    # Launches DOSBox with the given conf, measures the time and kills it when it exceeds the timeout.
    param(
        [Parameter(Mandatory)][ValidateSet('dosbox074', 'staging', 'dosboxx')][string]$Dialect,
        [Parameter(Mandatory)][string]$DosBox,
        [Parameter(Mandatory)][string]$Conf,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [int]$TimeoutSeconds = 90
    )
    $quotedConf = '"' + $Conf + '"'
    $arguments = switch ($Dialect) {
        'dosbox074' { '-conf', $quotedConf, '-exit', '-noconsole' }
        'staging' { '-noprimaryconf', '-nolocal', '-conf', $quotedConf, '-exit' }
        'dosboxx' { '-silent', '-exit', '-fastlaunch', '-conf', $quotedConf }
    }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process -FilePath $DosBox -ArgumentList $arguments -WorkingDirectory $WorkingDirectory -PassThru
    $null = $process.Handle  # keeps the handle open so ExitCode can be read after the process exits
    $finished = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $finished) { Stop-Process -Id $process.Id -Force }
    $watch.Stop()
    [pscustomobject]@{
        Seconds     = [math]::Round($watch.Elapsed.TotalSeconds, 2)
        Finished    = $finished
        ExitCode    = if ($finished) { $process.ExitCode } else { $null }
        CommandLine = "`"$DosBox`" " + ($arguments -join ' ')
    }
}

function Read-DosText {
    param([Parameter(Mandatory)][string]$Path)
    if (Test-Path $Path) { $script:Cp437.GetString([IO.File]::ReadAllBytes($Path)).TrimEnd() } else { '(missing)' }
}

function Format-SpikeLogs {
    # Summarizes every .LOG with its .RC, the generated artifacts and DOSBox's own console logs.
    param([Parameter(Mandatory)]$Workspace)
    $sb = [Text.StringBuilder]::new()
    foreach ($log in Get-ChildItem "$($Workspace.C)\LOG" -Filter *.LOG | Sort-Object Name) {
        $rc = Read-DosText ([IO.Path]::ChangeExtension($log.FullName, '.RC'))
        [void]$sb.AppendLine("--- $($log.BaseName) [$rc]")
        [void]$sb.AppendLine((Read-DosText $log.FullName))
    }
    [void]$sb.AppendLine('--- Artifacts')
    foreach ($file in Get-ChildItem "$($Workspace.C)\OBJ", "$($Workspace.C)\LST", "$($Workspace.C)\OUT" -File) {
        [void]$sb.AppendLine(('{0,-16} {1,8} B' -f $file.Name, $file.Length))
    }
    foreach ($name in 'stdout.txt', 'stderr.txt') {
        $path = Join-Path $Workspace.HostDir $name
        if (Test-Path $path) {
            [void]$sb.AppendLine("--- host/$name")
            [void]$sb.AppendLine((Get-Content -Raw $path))
        }
    }
    $sb.ToString()
}

function Save-SpikeSummary {
    param([Parameter(Mandatory)]$Workspace, [Parameter(Mandatory)][string]$Text)
    [IO.File]::WriteAllText((Join-Path $Workspace.Root 'summary.txt'), $Text, $script:Utf8NoBom)
    $Text
}
