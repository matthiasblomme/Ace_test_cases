# Generates the two XML fixtures used by the harness.
#
# dirty-order.xml carries a literal 0x1A (SUB) byte inside the Description element
# content. That byte is invisible in an editor and most tooling will silently strip
# or re-encode it, so the file is generated rather than committed. Regenerate it
# any time the fixtures look wrong.
#
# .NET IO calls resolve relative paths against the process start directory, not
# $PWD, so every path here is made absolute first.

$ErrorActionPreference = 'Stop'

$outDir = Join-Path $PSScriptRoot 'input'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$outDir = (Resolve-Path $outDir).Path

$header = '<?xml version="1.0" encoding="UTF-8"?>' + "`n" +
          '<ord:OrderMessage xmlns:ord="http://example.org/repro/order">' + "`n" +
          '  <ord:Body>' + "`n" +
          '    <ord:Description>'
$footer = '</ord:Description>' + "`n" +
          '  </ord:Body>' + "`n" +
          '</ord:OrderMessage>' + "`n"

$utf8 = New-Object System.Text.UTF8Encoding($false)   # no BOM

# Clean fixture: schema-valid, no control characters. Used by /roundtrip and
# /roundtripbad, whose input nodes parse AND validate with Content and Value.
$clean = $header + 'ABCDEF' + $footer
[System.IO.File]::WriteAllBytes(
    (Join-Path $outDir 'clean-order.xml'), $utf8.GetBytes($clean))

# Dirty fixture: same document with 0x1A between ABC and DEF. Used by /parse.
# Built as a byte array so the control character survives.
$bytes = New-Object System.Collections.Generic.List[byte]
$bytes.AddRange($utf8.GetBytes($header + 'ABC'))
$bytes.Add([byte]0x1A)
$bytes.AddRange($utf8.GetBytes('DEF' + $footer))
[System.IO.File]::WriteAllBytes(
    (Join-Path $outDir 'dirty-order.xml'), $bytes.ToArray())

# Schema-violating fixture: well-formed XML carrying an element the schema does not
# declare. Used by /reparse, where the RCD performs a real BLOB to XMLNSC domain
# change and therefore actually parses a bit stream.
$bad = $header + 'ABCDEF' + '</ord:Description>' + "`n" +
       '    <ord:Bogus>UNDECLARED ELEMENT</ord:Bogus>' + "`n" +
       '  </ord:Body>' + "`n" +
       '</ord:OrderMessage>' + "`n"
[System.IO.File]::WriteAllBytes(
    (Join-Path $outDir 'bad-order.xml'), $utf8.GetBytes($bad))

foreach ($f in 'clean-order.xml', 'dirty-order.xml', 'bad-order.xml') {
    $p = Join-Path $outDir $f
    $b = [System.IO.File]::ReadAllBytes($p)
    $has1a = $b -contains [byte]0x1A
    Write-Output ("{0,-18} {1,5} bytes   contains 0x1A: {2}" -f $f, $b.Length, $has1a)
}

# Per-branch copies for manual testing: one input file per harness request, named
# after the harness label, so any branch can be driven without looking up which of
# the three base fixtures it needs. Byte-copied so the 0x1A survives.
$perPath = Join-Path $outDir 'per-path'
if (-not (Test-Path $perPath)) { New-Item -ItemType Directory -Path $perPath | Out-Null }

$map = [ordered]@{
    'A_build'        = 'clean-order.xml'
    'B_badschema'    = 'clean-order.xml'
    'C_parse'        = 'dirty-order.xml'
    'D_noval'        = 'clean-order.xml'
    'E_roundtrip'    = 'clean-order.xml'
    'F_roundtripbad' = 'clean-order.xml'
    'G_reparse'      = 'bad-order.xml'
    'H_reparse_od'   = 'bad-order.xml'
    'H2_od_dirty'    = 'dirty-order.xml'
    'I_reparse_tch'  = 'bad-order.xml'
    'I2_tch_dirty'   = 'dirty-order.xml'
    'J_read_bogus'   = 'bad-order.xml'
    'K_imm_bad'      = 'bad-order.xml'
    'K2_imm_dirty'   = 'dirty-order.xml'
    'V1_valnode_1a'  = 'clean-order.xml'
    'V2_valnode_bad' = 'clean-order.xml'
    'W_blobround_1a' = 'clean-order.xml'
    'W2_blobrnd_bad' = 'clean-order.xml'
    'X_asbits_1a'    = 'clean-order.xml'
    'X2_asbits_bad'  = 'clean-order.xml'
    'Y_jcn_1a'       = 'clean-order.xml'
    'Y2_jcn_bad'     = 'clean-order.xml'
}

foreach ($label in $map.Keys) {
    $src = Join-Path $outDir $map[$label]
    $dst = Join-Path $perPath ($label + '.xml')
    [System.IO.File]::WriteAllBytes($dst, [System.IO.File]::ReadAllBytes($src))
}
$n = (Get-ChildItem $perPath -Filter '*.xml').Count
Write-Output ("per-path: {0} files written to {1}" -f $n, $perPath)
