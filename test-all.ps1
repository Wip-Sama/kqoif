#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Running full kqoif test suite" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$gradlew = if ($IsWindows -or $env:OS -like "*Windows*") { ".\gradlew.bat" } else { "./gradlew" }

& $gradlew check

if ($LASTEXITCODE -eq 0) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host " All tests passed successfully! " -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host " Tests failed with exit code $LASTEXITCODE" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit $LASTEXITCODE
}
