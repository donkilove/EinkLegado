<#
.SYNOPSIS
    文档门禁负向自测：验证 check-docs.ps1 在四种破坏下确实失败。

.DESCRIPTION
    在临时目录构造最小夹具，先确认全绿基线通过，再逐项注入破坏：
      1. 未归类文档
      2. 索引未登记文件
      3. 索引指向缺失文件
      4. 编号重复
    输出每种场景的退出码与命中原因。

.EXAMPLE
    pwsh -File tools/selftest-docgate.ps1
#>
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ('docgate-selftest-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$script:bad = 0

function New-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Path $fixture | Out-Null

    Copy-Item -LiteralPath (Join-Path $RepoRoot 'README.md') -Destination (Join-Path $fixture 'README.md')
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'CHANGELOG.md') -Destination (Join-Path $fixture 'CHANGELOG.md') -ErrorAction SilentlyContinue
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'api.md') -Destination (Join-Path $fixture 'api.md') -ErrorAction SilentlyContinue

    foreach ($sub in @('tools', 'docs')) {
        $src = Join-Path $RepoRoot $sub
        if (Test-Path -LiteralPath $src) {
            Copy-Item -LiteralPath $src -Destination (Join-Path $fixture $sub) -Recurse -Force
        }
    }
    # 例外样本：证明 glob 例外生效
    $ex1 = Join-Path $fixture 'app'
    $ex1 = Join-Path $ex1 'src'
    $ex1 = Join-Path $ex1 'main'
    $ex1 = Join-Path $ex1 'java'
    $ex1 = Join-Path $ex1 'io'
    $ex1 = Join-Path $ex1 'legado'
    $ex1 = Join-Path $ex1 'app'
    New-Item -ItemType Directory -Path $ex1 -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $ex1 'README.md') -Value '# sample' -Encoding utf8

    $ex2 = Join-Path $fixture 'modules'
    $ex2 = Join-Path $ex2 'web'
    New-Item -ItemType Directory -Path $ex2 -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $ex2 'README.md') -Value '# sample' -Encoding utf8

    # 证据目录里的两个被登记文件（内容无关）
    $ev = Join-Path $fixture 'docs'
    $ev = Join-Path $ev 'evidence'
    foreach ($name in @('eink-only-check.txt', 'eink-only.verify.md')) {
        if (-not (Test-Path -LiteralPath (Join-Path $ev $name))) {
            Set-Content -LiteralPath (Join-Path $ev $name) -Value 'placeholder' -Encoding utf8
        }
    }
    $todo = Join-Path $fixture 'TODO.md'
    Set-Content -LiteralPath $todo -Value "# TODO`n`n- [ ] T-1 示例事项" -Encoding utf8
}

function Invoke-Gate {
    $out = & pwsh -NoProfile -File (Join-Path $fixture 'tools/check-docs.ps1') 2>&1 | Out-String
    return @{ Code = $LASTEXITCODE; Text = $out }
}

function Assert-Case([string]$name, [scriptblock]$mutate, [string]$expectPattern) {
    New-Fixture
    & $mutate
    $r = Invoke-Gate
    $hit = $r.Text -match $expectPattern
    if ($r.Code -ne 0 -and $hit) {
        Write-Host "  [PASS] $name（exit=$($r.Code)）"
    }
    else {
        Write-Host "  [FAIL] $name（exit=$($r.Code)，命中=$hit）"
        Write-Host ($r.Text -split "`n" | Select-Object -Last 12 | Out-String)
        $script:bad++
    }
}

Write-Host "夹具目录：$fixture"

# 0) 基线必须通过
New-Fixture
$base = Invoke-Gate
if ($base.Code -eq 0) { Write-Host '  [PASS] 基线全绿' }
else {
    Write-Host "  [FAIL] 基线未通过（exit=$($base.Code)）"
    Write-Host $base.Text
    $script:bad++
}

# 1) 未归类文档
Assert-Case '未归类文档' {
    Set-Content -LiteralPath (Join-Path $fixture 'stray.md') -Value '# 未归类' -Encoding utf8
} '未归类文档'

# 2) 类内文件未登记
Assert-Case '索引未登记文件' {
    $d = Join-Path $fixture 'docs'
    $d = Join-Path $d 'evidence'
    Set-Content -LiteralPath (Join-Path $d 'orphan.txt') -Value 'orphan' -Encoding utf8
} '未在索引'

# 3) 索引指向缺失文件
Assert-Case '索引指向缺失文件' {
    $idx = Join-Path $fixture 'docs'
    $idx = Join-Path $idx 'specs'
    $idx = Join-Path $idx 'README.md'
    Add-Content -LiteralPath $idx -Value '| [不存在的规格](ghost.md) | draft | — |' -Encoding utf8
} '不存在'

# 4) 编号重复
Assert-Case '编号重复' {
    Add-Content -LiteralPath (Join-Path $fixture 'TODO.md') -Value '- [ ] T-1 重复编号' -Encoding utf8
} '重复'

if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host ''
if ($script:bad -eq 0) {
    Write-Host '负向自测全部通过：四种破坏都能被检出。'
    exit 0
}
Write-Host "负向自测失败：$($script:bad) 项"
exit 1
