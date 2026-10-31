$ErrorActionPreference = 'Stop'
function Find-PosJavaTools {
    param([string]$RequestedHome)
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($RequestedHome) { $candidates.Add($RequestedHome) }
    elseif ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($compiler) { $candidates.Add((Split-Path (Split-Path $compiler.Source -Parent) -Parent)) }
    $extensionRoot = Join-Path $env:USERPROFILE '.vscode\extensions'
    if (Test-Path -LiteralPath $extensionRoot) {
        $extensions = Get-ChildItem -LiteralPath $extensionRoot -Directory -Filter 'redhat.java-*' | Sort-Object Name -Descending
        foreach ($extension in $extensions) {
            $jre = Join-Path $extension.FullName 'jre'
            if (Test-Path -LiteralPath $jre) {
                foreach ($runtime in (Get-ChildItem -LiteralPath $jre -Directory)) { $candidates.Add($runtime.FullName) }
            }
        }
    }
    foreach ($candidate in $candidates) {
        $javacPath = Join-Path $candidate 'bin\javac.exe'
        $javaPath = Join-Path $candidate 'bin\java.exe'
        if ((Test-Path -LiteralPath $javacPath) -and (Test-Path -LiteralPath $javaPath)) {
            $versionText = (& $javacPath -version 2>&1 | Out-String).Trim()
            if ($versionText -match 'javac\s+(\d+)' -and [int]$Matches[1] -ge 17) {
                return @{ Javac = $javacPath; Java = $javaPath; Jar = (Join-Path $candidate 'bin\jar.exe'); Version = $versionText }
            }
        }
        if ($RequestedHome) { throw 'The specified -JdkHome must contain JDK 17 or newer (java and javac).' }
    }
    throw 'JDK 17+ not found. Set JAVA_HOME or pass -JdkHome pointing to an installed JDK.'
}
function Set-PosUtf8 {
    [Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $global:OutputEncoding = [Console]::OutputEncoding
}
function Reset-PosClassDirectory {
    param([string]$ProjectRoot, [string]$RelativePath)
    if ($RelativePath -notin @('build\classes', 'build\test-classes')) { throw 'Unexpected class output directory.' }
    $projectPath = [System.IO.Path]::GetFullPath($ProjectRoot)
    $targetPath = [System.IO.Path]::GetFullPath((Join-Path $projectPath $RelativePath))
    $expectedPrefix = Join-Path $projectPath 'build\'
    if (-not $targetPath.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Class output must stay inside this project build directory.' }
    if (Test-Path -LiteralPath $targetPath) {
        $resolvedPath = (Resolve-Path -LiteralPath $targetPath).Path
        if ($resolvedPath -ne $targetPath -or (Get-Item -LiteralPath $targetPath).LinkType) { throw 'Refusing to clear an unexpected class output path.' }
        Remove-Item -LiteralPath $resolvedPath -Recurse -Force
    }
    New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
}
