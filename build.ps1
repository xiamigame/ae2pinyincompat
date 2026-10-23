#requires -Version 5.1
<#
    Offline build script: ae2pinyincompat (AE2 Pinyin Search Compat)

    No Gradle / no internet: compiles with the local JDK 17 against the
    Forge 1.18.2 libraries of the target instance plus the target mod jars,
    then packages the jar with the JDK jar tool.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\build.ps1
        powershell -ExecutionPolicy Bypass -File .\build.ps1 -ModVersion 1.0.1

    This file is intentionally ASCII-only so it works with every console
    code page (PowerShell 5.1 may read non-BOM files as ANSI).
#>
[CmdletBinding()]
param(
    [string]$JdkHome    = "D:\Tool\Java\jdk-17.0.4+8",
    [string]$ForgeLibs  = "E:\Game\Minecraft\WV\Wold's-Vaults-0.34.1\.minecraft\libraries",
    [string]$ModsDir    = "E:\Game\Minecraft\WV\Wold's-Vaults-0.34.1\.minecraft\versions\Wold's Vaults - Vault Hunters Expansion\mods",
    [string]$ModVersion = "1.0.0"
)

$ErrorActionPreference = 'Stop'

$root       = Split-Path -Parent $MyInvocation.MyCommand.Path
$javac      = Join-Path $JdkHome 'bin\javac.exe'
$jarTool    = Join-Path $JdkHome 'bin\jar.exe'
$buildDir   = Join-Path $root 'build'
$classesDir = Join-Path $buildDir 'classes'
$distDir    = Join-Path $root 'dist'
$outJar     = Join-Path $distDir ("ae2pinyincompat-{0}.jar" -f $ModVersion)

foreach ($tool in @($javac, $jarTool)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "JDK tool not found: $tool" }
}
if (-not (Test-Path -LiteralPath $ForgeLibs)) { throw "Forge libraries not found: $ForgeLibs" }
if (-not (Test-Path -LiteralPath $ModsDir))   { throw "Mods directory not found: $ModsDir" }

# Resolve dependency jars by name pattern, so no non-ASCII literal is needed.
$modJars = @(Get-ChildItem -LiteralPath $ModsDir -File | Where-Object { $_.Name -like '*.jar' })

function Resolve-ModJar([string]$Pattern) {
    $hit = @($modJars | Where-Object { $_.Name -like $Pattern })
    if ($hit.Count -eq 0) { throw "Dependency jar not found in ${ModsDir}: $Pattern" }
    if ($hit.Count -gt 1)  { throw "More than one jar matches $Pattern : $($hit.Name -join ', ')" }
    return $hit[0].FullName
}

$ae2Jar = Resolve-ModJar 'appliedenergistics2-forge-*.jar'
$jecJar = Resolve-ModJar '*jecharacters*.jar'

$javaSrcDir = Join-Path $root 'src\main\java'
$resDir     = Join-Path $root 'src\main\resources'

if (Test-Path -LiteralPath $buildDir) { Remove-Item -LiteralPath $buildDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classesDir, $distDir | Out-Null

# Stage the dependency jars under ASCII names: javac 17 decodes @argfiles with
# the platform charset, so a non-ASCII path inside the arg file would break
# there (the JEC jar name contains Chinese).
$depsDir   = Join-Path $buildDir 'deps'
New-Item -ItemType Directory -Force -Path $depsDir | Out-Null
$ae2Staged = Join-Path $depsDir 'ae2.jar'
$jecStaged = Join-Path $depsDir 'jecharacters.jar'
Copy-Item -LiteralPath $ae2Jar -Destination $ae2Staged -Force
Copy-Item -LiteralPath $jecJar -Destination $jecStaged -Force

# ---- classpath: all Forge/MC/Mixin libraries + AE2 + JEC ----
$cpParts = New-Object System.Collections.Generic.List[string]
Get-ChildItem -LiteralPath $ForgeLibs -Recurse -Filter *.jar | ForEach-Object { $cpParts.Add($_.FullName) }
$cpParts.Add($ae2Staged)
$cpParts.Add($jecStaged)
$classpath = $cpParts -join ';'

$sources = @(Get-ChildItem -LiteralPath $javaSrcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "No Java sources found: $javaSrcDir" }

# ---- use @argfile to stay below the Windows command line limit ----
$argsFile = Join-Path $buildDir 'javac.args'
$argLines = New-Object System.Collections.Generic.List[string]
# javac's @argfile treats backslash as an escape character; forward slashes work too.
$argLines.Add('-encoding'); $argLines.Add('UTF-8')
$argLines.Add('-nowarn')
$argLines.Add('-d');        $argLines.Add(('"{0}"' -f $classesDir.Replace('\', '/')))
$argLines.Add('-cp');       $argLines.Add(('"{0}"' -f $classpath.Replace('\', '/')))
foreach ($src in $sources) { $argLines.Add(('"{0}"' -f $src.Replace('\', '/'))) }
[System.IO.File]::WriteAllLines($argsFile, $argLines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("[ae2pinyincompat] classpath: {0} jars" -f $cpParts.Count)
Write-Host ("[ae2pinyincompat] AE2: {0}" -f (Split-Path -Leaf $ae2Jar))
Write-Host ("[ae2pinyincompat] JEC: {0}" -f (Split-Path -Leaf $jecJar))
Write-Host ("[ae2pinyincompat] compiling {0} source files ..." -f $sources.Count)
& $javac ("@" + $argsFile)
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# ---- resources ----
Copy-Item -Path (Join-Path $resDir '*') -Destination $classesDir -Recurse -Force

# ---- MANIFEST (MixinConfigs is how Forge 1.18.2 discovers the mixin config) ----
$manifestPath = Join-Path $buildDir 'MANIFEST.MF'
$manifestText = @"
Manifest-Version: 1.0
Implementation-Title: ae2pinyincompat
Implementation-Version: $ModVersion
MixinConfigs: ae2pinyincompat.mixins.json

"@
$manifestText = $manifestText -replace "`r?`n", "`r`n"
[System.IO.File]::WriteAllText($manifestPath, $manifestText, (New-Object System.Text.ASCIIEncoding))

# ---- package ----
if (Test-Path -LiteralPath $outJar) { Remove-Item -LiteralPath $outJar -Force }
& $jarTool --create --file $outJar --manifest $manifestPath -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed with exit code $LASTEXITCODE" }

$size = [math]::Round((Get-Item -LiteralPath $outJar).Length / 1KB, 1)
Write-Host ""
Write-Host ("[ae2pinyincompat] built: {0} ({1} KB)" -f $outJar, $size)
Write-Host "[ae2pinyincompat] jar contents:"
& $jarTool --list --file $outJar | ForEach-Object { Write-Host ("  " + $_) }
