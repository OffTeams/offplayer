# Локализация OFFPlayer.
#  • strings.txt  — key|ru|en|uk|de|es|fr|pt|ko|ja (строки интерфейса)
#  • plurals.txt  — key|quantity|ru|en|uk|de|es|fr|pt|ko|ja (пусто — нет такой формы в языке)
#  • replace-*.txt — замены в коде: «FILE путь», затем пары строк «- было» / «+ стало»
# Запуск: powershell -File i18n\apply.ps1 [-Replace]   (без -Replace — только генерация XML)
param([switch]$Replace)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$src = Join-Path $root 'app\src\main\java\com\offplayer\app'
$res = Join-Path $root 'app\src\main\res'
$langs = @('ru','en','uk','de','es','fr','pt','ko','ja')
$utf8 = New-Object Text.UTF8Encoding $false

if ($Replace) {
    foreach ($rf in Get-ChildItem $PSScriptRoot -Filter 'replace-*.txt') {
        $file = $null; $old = $null; $text = $null
        $flush = { if ($file -and $text -ne $null) { [IO.File]::WriteAllText($file, $text, $utf8) } }
        foreach ($line in [IO.File]::ReadAllLines($rf.FullName, [Text.Encoding]::UTF8)) {
            if ($line -match '^FILE (.+)$') {
                & $flush
                $file = Join-Path $src $Matches[1].Trim()
                $text = [IO.File]::ReadAllText($file, [Text.Encoding]::UTF8)
            } elseif ($line.StartsWith('- ')) {
                $old = $line.Substring(2)
            } elseif ($line.StartsWith('+ ')) {
                $new = $line.Substring(2)
                if (-not $text.Contains($old)) { Write-Host "MISSING [$($rf.Name)] $file :: $old" }
                $text = $text.Replace($old, $new)
                $old = $null
            }
        }
        & $flush
    }
    # Импорты tr/trn/R в файлах, где они появились
    foreach ($f in Get-ChildItem $src -Recurse -Filter *.kt) {
        $t = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
        if ($t -notmatch '(?m)^package com\.offplayer\.app\.') { continue }
        $add = @()
        if ($t -match '\btr\(R\.' -and $t -notmatch 'import com\.offplayer\.app\.tr\b') { $add += 'import com.offplayer.app.tr' }
        if ($t -match '\btrn\(R\.' -and $t -notmatch 'import com\.offplayer\.app\.trn\b') { $add += 'import com.offplayer.app.trn' }
        if ($t -match '\bR\.(string|plurals)\.' -and $t -notmatch 'import com\.offplayer\.app\.R\b') { $add += 'import com.offplayer.app.R' }
        if ($add.Count -gt 0) {
            $t = [regex]::Replace($t, '(?m)^(package [^\r\n]+\r?\n)', { param($m) $m.Value + "`n" + ($add -join "`n") + "`n" }, 1)
            [IO.File]::WriteAllText($f.FullName, $t, $utf8)
        }
    }
}

function Esc([string]$s) {
    $s = $s.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;')
    $s = $s.Replace('\', '\\').Replace("'", "\'").Replace('"', '\"')
    if ($s.StartsWith('@') -or $s.StartsWith('?')) { $s = '\' + $s }
    return $s
}

$strings = @{}; foreach ($l in $langs) { $strings[$l] = New-Object System.Collections.Generic.List[string] }
$seen = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'strings.txt'), [Text.Encoding]::UTF8)) {
    if (-not $line.Trim() -or $line.StartsWith('#')) { continue }
    $p = $line.Split('|')
    if ($p.Count -ne 10) { Write-Host "BAD LINE ($($p.Count)): $line"; continue }
    $key = $p[0].Trim()
    if ($seen.ContainsKey($key)) { Write-Host "DUPLICATE KEY: $key"; continue }
    $seen[$key] = 1
    $fmt = ''
    if ($p[1] -match '%\d\$') { $fmt = '' } elseif ($p[1] -match '%') { $fmt = ' formatted="false"' }
    for ($i = 0; $i -lt 9; $i++) {
        $v = $p[$i + 1].Trim()
        if (-not $v) { $v = $p[2].Trim() }
        $strings[$langs[$i]].Add("    <string name=`"$key`"$fmt>$(Esc $v)</string>")
    }
}
$plurals = @{}; foreach ($l in $langs) { $plurals[$l] = [ordered]@{} }
$pf = Join-Path $PSScriptRoot 'plurals.txt'
if (Test-Path $pf) {
    foreach ($line in [IO.File]::ReadAllLines($pf, [Text.Encoding]::UTF8)) {
        if (-not $line.Trim() -or $line.StartsWith('#')) { continue }
        $p = $line.Split('|')
        if ($p.Count -ne 11) { Write-Host "BAD PLURAL ($($p.Count)): $line"; continue }
        for ($i = 0; $i -lt 9; $i++) {
            $v = $p[$i + 2].Trim()
            if (-not $v) { continue }
            $l = $langs[$i]
            if (-not $plurals[$l].Contains($p[0])) { $plurals[$l][$p[0]] = New-Object System.Collections.Generic.List[string] }
            $plurals[$l][$p[0]].Add("        <item quantity=`"$($p[1])`">$(Esc $v)</item>")
        }
    }
}
# Английский — язык по умолчанию (для всех остальных языков системы)
$dirs = @{ en = 'values'; ru = 'values-ru'; uk = 'values-uk'; de = 'values-de'; es = 'values-es'; fr = 'values-fr'; pt = 'values-pt'; ko = 'values-ko'; ja = 'values-ja' }
foreach ($l in $langs) {
    $dir = Join-Path $res $dirs[$l]
    New-Item -ItemType Directory -Force $dir | Out-Null
    $sb = New-Object Text.StringBuilder
    [void]$sb.AppendLine('<?xml version="1.0" encoding="utf-8"?>')
    [void]$sb.AppendLine('<!-- Сгенерировано i18n/apply.ps1 из i18n/strings.txt — правьте там -->')
    [void]$sb.AppendLine('<resources>')
    foreach ($s in $strings[$l]) { [void]$sb.AppendLine($s) }
    foreach ($k in $plurals[$l].Keys) {
        [void]$sb.AppendLine("    <plurals name=`"$k`">")
        foreach ($it in $plurals[$l][$k]) { [void]$sb.AppendLine($it) }
        [void]$sb.AppendLine('    </plurals>')
    }
    [void]$sb.AppendLine('</resources>')
    [IO.File]::WriteAllText((Join-Path $dir 'strings_ui.xml'), $sb.ToString(), $utf8)
}
Write-Host "Generated: $($seen.Count) strings x $($langs.Count) languages"
