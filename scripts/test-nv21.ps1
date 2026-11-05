$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$javaRoot = $env:JAVA_HOME
if (-not $javaRoot) { throw 'Set JAVA_HOME to JDK 17 or newer' }
$destination = Join-Path $project '.tools\tests'
New-Item -ItemType Directory -Force $destination | Out-Null
& "$javaRoot\bin\javac.exe" -d $destination "$project\app\src\main\java\studio\scenecam\Nv21.java" "$project\app\src\main\java\studio\scenecam\YuvPlanes.java" "$project\tests\Nv21Test.java" "$project\tests\YuvPlanesTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& "$javaRoot\bin\java.exe" -cp $destination Nv21Test
if ($LASTEXITCODE -ne 0) { throw 'NV21 tests failed' }
& "$javaRoot\bin\java.exe" -cp $destination YuvPlanesTest
if ($LASTEXITCODE -ne 0) { throw 'YUV plane tests failed' }
