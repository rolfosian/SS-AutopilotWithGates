$env:JAVA_HOME = "$env:USERPROFILE\Downloads\jdk-17.0.12_windows-x64_bin\jdk-17.0.12"

if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    Write-Error "Java not found at $env:JAVA_HOME\bin\java.exe"
    Write-Host "Please install Java 17 and set the correct path in the script."
    exit 1
}

Write-Host "Killing Starsector"
$cwd = Get-Location
$targetRelativePath = '..\..\jre\bin\java.exe'
$targetFullPath = Resolve-Path -Path (Join-Path $cwd $targetRelativePath)
Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -and $_.ExecutablePath -ieq $targetFullPath
} | ForEach-Object {
    Stop-Process -Id $_.ProcessId -Force
}

Write-Host "Using Java from: $env:JAVA_HOME"
$buildDir = ".\build\classes"
if (-not (Test-Path $buildDir)) {
    New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
}

$dependencies = @(
    "../../starsector-core/starfarer.api.jar",
    "../../starsector-core/starfarer_obf.jar",
    "../../starsector-core/fs.common_obf.jar",
    "../../starsector-core/fs.sound_obf.jar",
    "../../starsector-core/janino.jar",
    "../../starsector-core/commons-compiler.jar",
    
    "../../starsector-core/jinput.jar",
    "../../starsector-core/log4j-1.2.9.jar",
    "../../starsector-core/lwjgl.jar",
    "../../starsector-core/lwjgl_util.jar"
)

foreach ($dep in $dependencies) {
    if (-not (Test-Path $dep)) {
        Write-Warning "Missing dependency: $dep"
        Write-Warning "This might cause compilation issues if the dependency is required."
    }
}

$classpath = ($dependencies -join ";")

Write-Host "Compiling Java sources..."
$sourceFiles = Get-ChildItem -Path ".\src" -Filter "*.java" -Recurse
$javacArgs = @(
    "-encoding", "UTF-8"
    "-parameters"
    "-source", "17"
    "-target", "17"
    "-cp", $classpath
    "-d", $buildDir
    # "-Xlint:deprecation"
    $sourceFiles.FullName
)

& "$env:JAVA_HOME\bin\javac" $javacArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful. Creating JAR..."
    
    $jarFile = ".\jars\AutopilotWithGatesJAR.jar"
    $jarDir = Split-Path $jarFile
    if (!(Test-Path $jarDir)) {
        New-Item -ItemType Directory -Path $jarDir | Out-Null
    }

    Push-Location .
    & "$env:JAVA_HOME\bin\jar" -cf $jarFile -C $buildDir .
    Pop-Location
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Build completed successfully!" -ForegroundColor Green
        Write-Host "JAR file created at: $jarFile"
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Starting starsector.exe"
            Set-Location ../../; ./starsector.exe; Set-Location $cwd
            if (Test-Path ".\bin") { Remove-Item -Recurse -Force ".\bin" }
            if (Test-Path ".\build") { Remove-Item -Recurse -Force ".\build" }
        }
    } else {
        Write-Host "JAR creation failed!" -ForegroundColor Red
        if (Test-Path ".\bin") { Remove-Item -Recurse -Force ".\bin" }
        if (Test-Path ".\build") { Remove-Item -Recurse -Force ".\build" }
    }
} else {
    Write-Host "Compilation failed!" -ForegroundColor Red
    if (Test-Path ".\bin") { Remove-Item -Recurse -Force ".\bin" }
    if (Test-Path ".\build") { Remove-Item -Recurse -Force ".\build" }
}