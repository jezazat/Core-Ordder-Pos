param([string]$JdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\java-tools.ps1')
Set-PosUtf8
$tools = Find-PosJavaTools $JdkHome
Push-Location -LiteralPath $PSScriptRoot
try {
    Reset-PosClassDirectory $PSScriptRoot 'build\test-classes'
    $sources = @(Get-ChildItem -LiteralPath 'src\pos' -Filter '*.java' | ForEach-Object { 'src\pos\' + $_.Name })
    $sources += @(Get-ChildItem -LiteralPath 'tests\pos' -Filter '*.java' | ForEach-Object { 'tests\pos\' + $_.Name })
    & $tools.Javac --release 17 -encoding UTF-8 -d 'build\test-classes' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & $tools.Java '-Dfile.encoding=UTF-8' -cp 'build\test-classes' pos.PosTests
    if ($LASTEXITCODE -ne 0) { throw 'POS tests failed.' }
} finally { Pop-Location }
