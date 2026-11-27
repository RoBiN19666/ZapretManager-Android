# Запускает релизную сборку в GitHub Actions.
#
# Использование (из корня репозитория):
#   .\scripts\release.ps1                 # версия из version.txt
#   .\scripts\release.ps1 -Version 1.1.0  # конкретная версия
#
# Требуется установленный GitHub CLI (`gh auth login`).
# Сборка не запускается на каждый коммит: только этот скрипт (workflow_dispatch)
# или push тега v*.
[CmdletBinding()]
param(
    [string]$Version = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $Version) {
    $Version = (Get-Content version.txt -Raw).Trim()
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI (gh) не найден: https://cli.github.com/'
}

# Проверяем, что для версии есть раздел в CHANGELOG — он попадёт в текст релиза.
$changelog = Get-Content CHANGELOG.md -Raw -Encoding UTF8
if ($changelog -notmatch "(?m)^##\s+$([regex]::Escape($Version))\s*$") {
    Write-Warning "В CHANGELOG.md нет раздела «## $Version» — релиз получит общий текст."
}

git pull --rebase origin main

Write-Host "Запускаю сборку релиза v$Version…"
gh workflow run android.yml --ref main -f "version=$Version"
Start-Sleep -Seconds 5
gh run list --workflow android.yml --limit 1

Write-Host 'Готово. Прогресс: GitHub → Actions → Android CI.'
Write-Host "После сборки релиз появится здесь: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases"
