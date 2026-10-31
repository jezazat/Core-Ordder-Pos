param([string]$JdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\java-tools.ps1')
Set-PosUtf8
$tools = Find-PosJavaTools $JdkHome
& (Join-Path $PSScriptRoot 'build.ps1') -JdkHome $JdkHome
Push-Location -LiteralPath $PSScriptRoot
try {
    & $tools.Java '-Dfile.encoding=UTF-8' -jar 'build\pos-console.jar'
    if ($LASTEXITCODE -ne 0) { throw 'POS exited with an error.' }
} finally { Pop-Location }
