<#
.SYNOPSIS
    安装/卸载/检查文档门禁的提交钩子（幂等）。

.DESCRIPTION
    钩子放在版本化的 .githooks/ 目录，通过 core.hooksPath 启用。
    安装后会立即试跑一次门禁，避免「装完才发现当前工作区任何提交都被拒」。

.PARAMETER Check
    只报告当前状态，不做修改。

.PARAMETER Uninstall
    移除 core.hooksPath 配置。

.EXAMPLE
    pwsh -File tools/install-hooks.ps1
    pwsh -File tools/install-hooks.ps1 -Check
    pwsh -File tools/install-hooks.ps1 -Uninstall
#>
param(
    [switch]$Check,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$current = (& git -C $repoRoot config --get core.hooksPath) 2>$null

if ($Check) {
    if ($current -eq '.githooks') {
        Write-Host '钩子已启用：core.hooksPath = .githooks'
        exit 0
    }
    Write-Host "钩子未启用（当前 core.hooksPath = '$current'）"
    exit 1
}

if ($Uninstall) {
    if ($current) {
        & git -C $repoRoot config --unset core.hooksPath
        Write-Host '已移除 core.hooksPath，提交钩子停用'
    }
    else {
        Write-Host 'core.hooksPath 未设置，无需移除'
    }
    exit 0
}

& git -C $repoRoot config core.hooksPath .githooks
if ($LASTEXITCODE -ne 0) {
    Write-Host '设置 core.hooksPath 失败'
    exit 1
}
Write-Host '已启用提交钩子：core.hooksPath = .githooks'

Write-Host '试跑一次文档门禁……'
& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-docs.ps1')
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host '注意：当前工作区未通过文档门禁，提交会被拒绝。修好后再提交，或临时用 git commit --no-verify 跳过。'
    exit 1
}
Write-Host '门禁当前通过。'
exit 0
