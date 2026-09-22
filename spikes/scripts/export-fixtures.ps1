<#
.SYNOPSIS
  Copies curated diagnostic, listing and map fixtures from spike results into fixtures/ for the F1 parser tests.
.DESCRIPTION
  Reads spikes/out (produced by s2-tasm-dosbox.ps1 for tasm41-dosboxx and tasm32-dosbox074, and by s3-masm611.ps1)
  and writes, per tool and version, the raw output of each case plus an index with exit codes. Host paths from the
  spike workspace are replaced by a fixed placeholder root so the fixtures do not depend on this machine.
.EXAMPLE
  ./export-fixtures.ps1
#>
param(
    [string]$OutRoot = (Join-Path $PSScriptRoot '..\out'),
    [string]$FixturesRoot = (Join-Path $PSScriptRoot '..\..\fixtures')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'SpikeCommon.ps1')

$out = [IO.Path]::GetFullPath($OutRoot)
$fixtures = [IO.Path]::GetFullPath($FixturesRoot)
$placeholder = 'C:\IDEARM-FIXTURE'
$index = [Collections.Generic.List[string]]::new()
$index.Add('tool,version,case,exitCode,file')

function Save-Fixture {
    param([string]$Tool, [string]$Version, [string]$Case, [string]$ExitCode, [string]$Text, [string]$WorkRoot)
    $relative = "diagnostics/$Tool-$Version/$Case.txt"
    $target = Join-Path $fixtures $relative
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    [IO.File]::WriteAllText($target, ($Text.Replace($WorkRoot, $placeholder).TrimEnd() + "`r`n"), $script:Utf8NoBom)
    $index.Add("$Tool,$Version,$Case,$ExitCode,$relative")
}

# Borland: TASM (*_A) and TLINK (*_L) logs captured inside DOSBox
foreach ($set in @(
        @{ Label = 'tasm41-dosboxx'; Tasm = '4.1'; Tlink = '7.1' },
        @{ Label = 'tasm32-dosbox074'; Tasm = '3.2'; Tlink = '3.01' })) {
    $root = Join-Path $out $set.Label
    foreach ($log in Get-ChildItem "$root\C\LOG" -Filter *.LOG) {
        $rc = (Read-DosText ([IO.Path]::ChangeExtension($log.FullName, '.RC'))) -replace '^RC=', ''
        if ($log.BaseName -match '^(?<case>.+)_A$') {
            Save-Fixture -Tool 'tasm' -Version $set.Tasm -Case $Matches.case -ExitCode $rc -Text (Read-DosText $log.FullName) -WorkRoot $root
        } elseif ($log.BaseName -match '^(?<case>.+)_L$') {
            Save-Fixture -Tool 'tlink' -Version $set.Tlink -Case $Matches.case -ExitCode $rc -Text (Read-DosText $log.FullName) -WorkRoot $root
        }
    }
}

# Microsoft: native ML output (from the summary) and 16-bit LINK logs captured inside DOSBox
$masmRoot = Join-Path $out 'masm611-dosbox074'
$summary = [IO.File]::ReadAllText((Join-Path $masmRoot 'summary.txt'))
$mlBlocks = [regex]::Matches($summary, '(?ms)^--- ML (?<case>\S+) \[exit=(?<exit>-?\d+)[^\]]*\]\r?\n(?<body>.*?)(?=^--- |^LINK inside|\z)')
foreach ($block in $mlBlocks) {
    Save-Fixture -Tool 'ml' -Version '6.11' -Case $block.Groups['case'].Value -ExitCode $block.Groups['exit'].Value `
        -Text $block.Groups['body'].Value -WorkRoot $masmRoot
}
foreach ($log in Get-ChildItem "$masmRoot\C\LOG" -Filter '*_L.LOG') {
    $rc = (Read-DosText ([IO.Path]::ChangeExtension($log.FullName, '.RC'))) -replace '^RC=', ''
    Save-Fixture -Tool 'link' -Version '5.31' -Case ($log.BaseName -replace '_L$', '') -ExitCode $rc -Text (Read-DosText $log.FullName) -WorkRoot $masmRoot
}

# Listing and map samples (source ↔ address mapping, machine-code panel)
$samples = @(
    @{ From = 'tasm41-dosboxx\C\LST\HELLO.LST'; To = 'listings\tasm-4.1\HELLO.LST' },
    @{ From = 'tasm41-dosboxx\C\LST\CPU186.LST'; To = 'listings\tasm-4.1\CPU186.LST' },
    @{ From = 'tasm32-dosbox074\C\LST\HELLO.LST'; To = 'listings\tasm-3.2\HELLO.LST' },
    @{ From = 'masm611-dosbox074\C\LST\HELLO.LST'; To = 'listings\ml-6.11\HELLO.LST' },
    @{ From = 'masm611-dosbox074\C\LST\CPU186.LST'; To = 'listings\ml-6.11\CPU186.LST' },
    @{ From = 'tasm41-dosboxx\C\OUT\HELLO.MAP'; To = 'maps\tlink-7.1\HELLO.MAP' },
    @{ From = 'tasm41-dosboxx\C\OUT\DUP.MAP'; To = 'maps\tlink-7.1\DUP.MAP' },
    @{ From = 'tasm32-dosbox074\C\OUT\HELLO.MAP'; To = 'maps\tlink-3.01\HELLO.MAP' },
    @{ From = 'masm611-dosbox074\C\OUT\HELLO.MAP'; To = 'maps\link-5.31\HELLO.MAP' }
)
foreach ($sample in $samples) {
    $source = Join-Path $out $sample.From
    if (-not (Test-Path $source)) {
        Write-Warning "Missing sample: $source"
        continue
    }
    $target = Join-Path $fixtures $sample.To
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    $text = $script:Cp437.GetString([IO.File]::ReadAllBytes($source)).Replace($masmRoot, $placeholder)
    [IO.File]::WriteAllText($target, $text, $script:Utf8NoBom)
}

[IO.File]::WriteAllLines((Join-Path $fixtures 'diagnostics\index.csv'), $index, $script:Utf8NoBom)
"Exported $($index.Count - 1) diagnostic fixtures and $($samples.Count) listing/map samples to $fixtures"
