param([string]$JdkHome)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scripts\java-tools.ps1')
Set-PosUtf8
$tools = Find-PosJavaTools $JdkHome
Push-Location -LiteralPath $PSScriptRoot
try {
    Reset-PosClassDirectory $PSScriptRoot 'build\classes'
    $sources = @(Get-ChildItem -LiteralPath 'src\pos' -Filter '*.java' | ForEach-Object { 'src\pos\' + $_.Name })
    & $tools.Javac --release 17 -encoding UTF-8 -d 'build\classes' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    & $tools.Jar --create --file 'build\pos-console.jar' --main-class pos.ConsoleApp -C 'build\classes' .
    if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed.' }
    Write-Host "Built build\pos-console.jar using $($tools.Version) (Java 17 compatible)"
} finally { Pop-Location }
