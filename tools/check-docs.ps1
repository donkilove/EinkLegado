#Requires -Version 5.1
<#
.SYNOPSIS
    文档门禁：校验仓库文档策略的三条不变式。

.DESCRIPTION
    策略外置于仓库根 README.md 的「Docs-文档约定」一节，本脚本不硬编码任何类名或落点。
    校验：
      I1 归类完备      每个 .md 必须命中某个文档类落点或某条例外
      I2 索引 ↔ 实体   索引里的链接必须存在；类范围内每个文件必须被索引登记
      I3 编号唯一      声明来源中的编号不得重复

.PARAMETER RepoRoot
    仓库根目录，默认取本脚本所在目录的上一级。

.EXAMPLE
    pwsh -File tools/check-docs.ps1
#>
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$script:skipDirs = @('.git', 'build', 'node_modules', '.gradle', '.idea', 'out')
$script:fail = 0
$script:reasons = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$msg) {
    $script:fail++
    $script:reasons.Add($msg)
    Write-Host "  [FAIL] $msg"
}

function Normalize-Rel([string]$rel) {
    $stack = New-Object System.Collections.Generic.List[string]
    foreach ($seg in ($rel -split '/')) {
        if ($seg -eq '' -or $seg -eq '.') { continue }
        if ($seg -eq '..') {
            if ($stack.Count -gt 0) { $stack.RemoveAt($stack.Count - 1) }
            continue
        }
        $stack.Add($seg)
    }
    return ($stack -join '/')
}

function Join-RepoPath([string]$root, [string]$rel) {
    $p = $root
    foreach ($seg in ($rel -split '/')) {
        if ($seg -ne '') { $p = Join-Path $p $seg }
    }
    return $p
}

function ConvertTo-GlobRegex([string]$glob) {
    $g = ($glob -replace '\\', '/')
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append('^')
    $i = 0
    while ($i -lt $g.Length) {
        $c = $g[$i]
        if ($c -eq '*' -and ($i + 1) -lt $g.Length -and $g[$i + 1] -eq '*') {
            [void]$sb.Append('.*')
            $i += 2
            if ($i -lt $g.Length -and $g[$i] -eq '/') { $i++ }
        }
        elseif ($c -eq '*') {
            [void]$sb.Append('[^/]*')
            $i++
        }
        elseif ($c -eq '?') {
            [void]$sb.Append('[^/]')
            $i++
        }
        else {
            [void]$sb.Append([regex]::Escape([string]$c))
            $i++
        }
    }
    [void]$sb.Append('$')
    return [regex]::new($sb.ToString(), [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
}

function Get-RepoFiles([string]$dir, [string]$prefix) {
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($f in (Get-ChildItem -LiteralPath $dir -File -Force -ErrorAction SilentlyContinue)) {
        $out.Add($prefix + $f.Name)
    }
    foreach ($d in (Get-ChildItem -LiteralPath $dir -Directory -Force -ErrorAction SilentlyContinue)) {
        if ($script:skipDirs -contains $d.Name) { continue }
        foreach ($p in (Get-RepoFiles $d.FullName ($prefix + $d.Name + '/'))) { $out.Add($p) }
    }
    return $out
}

# ---------------------------------------------------------------- 解析声明
$declPath = Join-Path $RepoRoot 'README.md'
if (-not (Test-Path -LiteralPath $declPath)) {
    Write-Host "门禁失败：缺少策略声明文件 $declPath"
    exit 1
}
$decl = @(Get-Content -LiteralPath $declPath)

$classes = New-Object System.Collections.Generic.List[object]
$inTable = $false
foreach ($line in $decl) {
    $t = $line.Trim()
    if (-not $inTable) {
        if ($t -match '^\|\s*类型\s*\|') { $inTable = $true }
        continue
    }
    if ($t -notmatch '^\|') { break }
    if ($t -match '^\|[\s:\-\|]+\|$') { continue }
    $cells = @($t.Trim('|').Split('|') | ForEach-Object { $_.Trim() })
    if ($cells.Count -lt 3) { continue }
    if ($cells[1] -notmatch '`([^`]+)`') {
        Write-Host "门禁失败：类型「$($cells[0])」的落点列缺少反引号路径"
        exit 1
    }
    $loc = ($Matches[1] -replace '\\', '/')
    $idx = $null
    if ($cells[2] -match '\]\(([^)]+)\)') { $idx = ($Matches[1]).Trim() }
    $classes.Add([pscustomobject]@{
            Type  = $cells[0]
            Loc   = $loc
            IsDir = $loc.EndsWith('/')
            Index = $idx
        })
}
if ($classes.Count -eq 0) {
    Write-Host '门禁失败：声明中解析不出文档类表'
    exit 1
}

$exceptions = New-Object System.Collections.Generic.List[string]
$inExc = $false
foreach ($line in $decl) {
    $t = $line.Trim()
    if (-not $inExc) {
        if ($t -match '^\*\*例外\*\*') { $inExc = $true }
        continue
    }
    if ($t -match '^\*\*') { break }
    if ($t -match '^#') { break }
    if ($t -match '^-\s+`([^`]+)`') {
        $exceptions.Add(($Matches[1] -replace '\\', '/'))
    }
}

$numSources = New-Object System.Collections.Generic.List[string]
$inNum = $false
foreach ($line in $decl) {
    $t = $line.Trim()
    if (-not $inNum) {
        if ($t -match '^\*\*编号\*\*') { $inNum = $true }
        continue
    }
    if ($t -match '^\*\*' -or $t -match '^#') { break }
    foreach ($m in [regex]::Matches($t, '`([^`]+\.md)`')) {
        $numSources.Add(($m.Groups[1].Value -replace '\\', '/'))
    }
}

Write-Host "声明解析：类 $($classes.Count) 个，例外 $($exceptions.Count) 条，编号来源 $($numSources.Count) 个"

$allFiles = @(Get-RepoFiles $RepoRoot '')
$allMd = @($allFiles | Where-Object { $_ -match '\.md$' })

function Get-ClassOf([string]$rel) {
    $best = $null
    foreach ($c in $classes) {
        if ($c.IsDir) {
            if ($rel.StartsWith($c.Loc, [System.StringComparison]::OrdinalIgnoreCase)) {
                if ($null -eq $best -or $c.Loc.Length -gt $best.Loc.Length) { $best = $c }
            }
        }
        elseif ($rel -eq $c.Loc) { $best = $c }
    }
    return $best
}

# ---------------------------------------------------------------- I1 归类完备
Write-Host 'I1 归类完备'
foreach ($rel in $allMd) {
    $exempt = $false
    foreach ($e in $exceptions) {
        if ((ConvertTo-GlobRegex $e).IsMatch($rel)) { $exempt = $true; break }
    }
    if ($exempt) { continue }
    if ($null -eq (Get-ClassOf $rel)) { Add-Failure "未归类文档：$rel" }
}

# ---------------------------------------------------------------- I2 索引 ↔ 实体
Write-Host 'I2 索引与实体双向一致'
$registeredCache = @{}
foreach ($c in $classes) {
    if (-not $c.Index) { continue }
    $idxRel = Normalize-Rel $c.Index
    $idxFull = Join-RepoPath $RepoRoot $idxRel
    if (-not (Test-Path -LiteralPath $idxFull)) {
        Add-Failure "类型「$($c.Type)」的索引不存在：$idxRel"
        continue
    }
    if (-not $registeredCache.ContainsKey($idxRel)) {
        $content = Get-Content -LiteralPath $idxFull -Raw
        $idxDir = ''
        $slash = $idxRel.LastIndexOf('/')
        if ($slash -ge 0) { $idxDir = $idxRel.Substring(0, $slash) }
        $set = New-Object System.Collections.Generic.HashSet[string]
        foreach ($m in [regex]::Matches($content, '\]\(([^)]+)\)')) {
            $target = $m.Groups[1].Value.Trim()
            if ($target -match '^(https?:|mailto:|#)') { continue }
            $target = ($target -split '#')[0]
            if (-not $target) { continue }
            if ($target.StartsWith('/')) { $regRel = Normalize-Rel $target }
            elseif ($idxDir) { $regRel = Normalize-Rel ($idxDir + '/' + $target) }
            else { $regRel = Normalize-Rel $target }
            [void]$set.Add($regRel)
            if (-not (Test-Path -LiteralPath (Join-RepoPath $RepoRoot $regRel))) {
                Add-Failure "索引 $idxRel 指向不存在的文件：$regRel"
            }
        }
        $registeredCache[$idxRel] = $set
    }
}

foreach ($rel in $allFiles) {
    $cls = Get-ClassOf $rel
    if ($null -eq $cls -or -not $cls.Index) { continue }
    $idxRel = Normalize-Rel $cls.Index
    if ($rel -eq $idxRel) { continue }
    if (-not $registeredCache.ContainsKey($idxRel)) { continue }
    if (-not $registeredCache[$idxRel].Contains($rel)) {
        Add-Failure "未在索引 $idxRel 中登记：$rel"
    }
}

# ---------------------------------------------------------------- I3 编号唯一
Write-Host 'I3 编号唯一'
foreach ($src in $numSources) {
    $full = Join-RepoPath $RepoRoot (Normalize-Rel $src)
    if (-not (Test-Path -LiteralPath $full)) { continue }
    $text = Get-Content -LiteralPath $full -Raw
    $seen = New-Object System.Collections.Generic.HashSet[string]
    foreach ($m in [regex]::Matches($text, 'T-(\d+)')) {
        $num = $m.Groups[1].Value
        if (-not $seen.Add($num)) { Add-Failure "$src 中编号重复：T-$num" }
    }
    foreach ($m in [regex]::Matches($text, '(?m)^\|\s*(\d+)\s*\|')) {
        $num = $m.Groups[1].Value
        if (-not $seen.Add("row$num")) { Add-Failure "$src 中归档编号重复：$num" }
    }
}

# ---------------------------------------------------------------- 结果
Write-Host ''
if ($script:fail -eq 0) {
    Write-Host "文档门禁通过：检查 .md $($allMd.Count) 个，文件 $($allFiles.Count) 个"
    exit 0
}
Write-Host "文档门禁失败：$($script:fail) 项问题"
foreach ($r in $script:reasons) { Write-Host "  - $r" }
exit 1
