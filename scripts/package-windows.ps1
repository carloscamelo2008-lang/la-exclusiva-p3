[CmdletBinding()]
param(
    [ValidateSet('app-image', 'installer', 'all')]
    [string]$Target = 'all',
    [string]$AppVersion = '1.0.0',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Maven = 'mvn.cmd',
    [string]$WixBin,
    [string]$IconPath,
    [switch]$SkipSmokeLaunch
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-PathIsInside {
    param([string]$ChildPath, [string]$ParentPath)
    $child = [IO.Path]::GetFullPath($ChildPath).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $parent = [IO.Path]::GetFullPath($ParentPath).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $child.StartsWith($parent, [StringComparison]::OrdinalIgnoreCase)) {
        throw "La ruta generada sale del directorio esperado: $ChildPath"
    }
}

function Resolve-WixBin {
    param([string]$ProvidedPath)

    $candidate = $ProvidedPath
    if ([string]::IsNullOrWhiteSpace($candidate) -and $env:WIX) {
        $candidate = Join-Path $env:WIX 'bin'
    }
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'candle.exe')) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'light.exe'))) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    # Descarga WiX portable fuera del repositorio y verifica el hash fijado.
    $toolRoot = Join-Path $env:LOCALAPPDATA 'LaExclusiva\tooling\wix-3.14.1'
    $existingCandle = Join-Path $toolRoot 'candle.exe'
    if (Test-Path -LiteralPath $existingCandle) {
        return $toolRoot
    }

    $downloadRoot = Join-Path $env:TEMP ('LaExclusiva-WiX-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $downloadRoot | Out-Null
    $archive = Join-Path $downloadRoot 'wix314-binaries.zip'
    $url = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
    $expectedSha256 = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
    Invoke-WebRequest -Uri $url -OutFile $archive
    $actualSha256 = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw 'La verificación SHA-256 de WiX 3.14.1 falló; no se extraerá ni ejecutará el archivo.'
    }
    New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
    if (-not (Test-Path -LiteralPath (Join-Path $toolRoot 'candle.exe')) -or
            -not (Test-Path -LiteralPath (Join-Path $toolRoot 'light.exe'))) {
        throw 'El archivo de WiX no contiene candle.exe y light.exe en la ruta esperada.'
    }
    return $toolRoot
}

if ($env:OS -ne 'Windows_NT') {
    throw 'La generación de app-image e instalador Windows debe ejecutarse en Windows.'
}
if ($AppVersion -notmatch '^\d+\.\d+\.\d+$') {
    throw 'AppVersion debe tener tres componentes numéricos, por ejemplo 1.0.0.'
}
if ([string]::IsNullOrWhiteSpace($JavaHome) -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\jpackage.exe'))) {
    throw 'Configure JavaHome/JAVA_HOME con un JDK 17 que incluya jpackage.'
}

$javaExe = Join-Path $JavaHome 'bin\java.exe'
$javacExe = Join-Path $JavaHome 'bin\javac.exe'
$jarExe = Join-Path $JavaHome 'bin\jar.exe'
$jpackageExe = Join-Path $JavaHome 'bin\jpackage.exe'
foreach ($tool in @($javaExe, $javacExe, $jarExe, $jpackageExe)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "No se encontró la herramienta del JDK: $tool" }
}
$javacVersion = (& $javacExe -version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $javacVersion -notmatch '^javac 17\.') {
    throw "Se requiere Java 17; javac informado: $javacVersion"
}

if (Test-Path -LiteralPath $Maven) {
    $mavenExe = (Resolve-Path -LiteralPath $Maven).Path
} else {
    $mavenCommand = Get-Command $Maven -ErrorAction SilentlyContinue
    if (-not $mavenCommand) { throw 'No se encontró Maven. Indique -Maven con la ruta a mvn.cmd.' }
    $mavenExe = $mavenCommand.Source
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$distributionRoot = Join-Path $repoRoot 'dist\windows-package'
New-Item -ItemType Directory -Path $distributionRoot -Force | Out-Null
$buildId = [DateTime]::Now.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$buildRoot = Join-Path $distributionRoot $buildId
New-Item -ItemType Directory -Path $buildRoot | Out-Null
$inputDir = Join-Path $buildRoot 'input'
$classesDir = Join-Path $buildRoot 'classes'
New-Item -ItemType Directory -Path $inputDir, $classesDir | Out-Null

$reservedEnvironmentNames = @(
    'LAEXCLUSIVA_TEST_DB_URL', 'LAEXCLUSIVA_TEST_DB_USER', 'LAEXCLUSIVA_TEST_DB_PASSWORD',
    'LAEXCLUSIVA_DB_URL', 'LAEXCLUSIVA_DB_USER', 'LAEXCLUSIVA_DB_PASSWORD',
    'LAEXCLUSIVA_PERSISTENCE_MODE', 'LAEXCLUSIVA_SMTP_ENABLED', 'LAEXCLUSIVA_SMTP_HOST',
    'LAEXCLUSIVA_SMTP_PORT', 'LAEXCLUSIVA_SMTP_USERNAME', 'LAEXCLUSIVA_SMTP_PASSWORD',
    'LAEXCLUSIVA_SMTP_FROM', 'LAEXCLUSIVA_INITIAL_ADMIN_USERNAME',
    'LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD', 'LAEXCLUSIVA_DEMO_ADMIN_PASSWORD',
    'LAEXCLUSIVA_DEMO_WORKER_PASSWORD', 'LAEXCLUSIVA_PAYMENT_KEY',
    'LAEXCLUSIVA_PAYMENT_KEY_OWNER_CONFIRMED', 'LAEXCLUSIVA_PAYMENT_KEY_VERIFIED_BY_ADMIN'
)
$savedEnvironment = @{}
foreach ($name in $reservedEnvironmentNames) {
    $entry = Get-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
    $savedEnvironment[$name] = if ($entry) { $entry.Value } else { $null }
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"
$env:LAEXCLUSIVA_PERSISTENCE_MODE = 'memory'
$env:LAEXCLUSIVA_SMTP_ENABLED = 'false'

try {
    Push-Location $repoRoot
    try {
        Write-Host 'Ejecutando mvn clean verify con variables de bases de datos/SMTP retiradas del proceso.'
        & $mavenExe --batch-mode clean verify
        if ($LASTEXITCODE -ne 0) { throw "mvn clean verify terminó con código $LASTEXITCODE." }

        Write-Host 'Copiando solo dependencias del alcance runtime, incluidos los binarios JavaFX de Windows.'
        & $mavenExe --batch-mode 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies' `
            "-DincludeScope=runtime" "-DoutputDirectory=$inputDir"
        if ($LASTEXITCODE -ne 0) { throw "copy-dependencies terminó con código $LASTEXITCODE." }

        $requiredDependencyPatterns = @(
            'javafx-base-*-win.jar', 'javafx-graphics-*-win.jar', 'javafx-controls-*-win.jar',
            'javafx-fxml-*-win.jar', 'postgresql-*.jar', 'pdfbox-*.jar', 'fontbox-*.jar',
            'angus-mail-*.jar'
        )
        $dependencyNames = @(Get-ChildItem -LiteralPath $inputDir -Filter '*.jar' | Select-Object -ExpandProperty Name)
        foreach ($pattern in $requiredDependencyPatterns) {
            if (-not ($dependencyNames | Where-Object { $_ -like $pattern })) {
                throw "Falta una dependencia de ejecución necesaria para Windows: $pattern"
            }
        }

        $sourceClasses = Join-Path $repoRoot 'target\classes'
        foreach ($file in Get-ChildItem -LiteralPath $sourceClasses -Recurse -File) {
            $relativePath = $file.FullName.Substring($sourceClasses.Length).TrimStart([IO.Path]::DirectorySeparatorChar)
            $destinationFile = Join-Path $classesDir $relativePath
            $destinationDirectory = Split-Path -Parent $destinationFile
            New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
            Copy-Item -LiteralPath $file.FullName -Destination $destinationFile -Force
        }
        $demoDirectory = Join-Path $classesDir 'com\laexclusiva\demo'
        Assert-PathIsInside -ChildPath $demoDirectory -ParentPath $classesDir
        if (Test-Path -LiteralPath $demoDirectory) {
            Remove-Item -LiteralPath $demoDirectory -Recurse -Force
        }
        Get-ChildItem -LiteralPath (Join-Path $classesDir 'com\laexclusiva') -Filter 'Main*.class' -File |
            Remove-Item -Force

        $compileClassPath = @($classesDir) + @($dependencyNames | ForEach-Object { Join-Path $inputDir $_ })
        & $javacExe --release 17 -encoding UTF-8 --class-path ($compileClassPath -join ';') `
            -d $classesDir (Join-Path $repoRoot 'packaging\windows\WindowsDesktopLauncher.java')
        if ($LASTEXITCODE -ne 0) { throw "javac para el launcher terminó con código $LASTEXITCODE." }

        $mainJar = Join-Path $inputDir 'la-exclusiva-desktop.jar'
        & $jarExe --create --file $mainJar --main-class com.laexclusiva.packaging.WindowsDesktopLauncher -C $classesDir .
        if ($LASTEXITCODE -ne 0) { throw "jar terminó con código $LASTEXITCODE." }
        $jarListing = @(& $jarExe --list --file $mainJar)
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar el contenido del JAR de escritorio.' }
        if ($jarListing -match '^com/laexclusiva/Main(\$.*)?\.class$|^com/laexclusiva/demo/') {
            throw 'La entrada de consola o el cargador de datos demo entró en el JAR de escritorio.'
        }
        if (-not ($jarListing -contains 'com/laexclusiva/packaging/WindowsDesktopLauncher.class')) {
            throw 'El launcher de Windows no está en el JAR.'
        }
    } finally {
        Pop-Location
    }

    $icon = $IconPath
    if ([string]::IsNullOrWhiteSpace($icon)) {
        $reservedIcon = Join-Path $repoRoot 'src\main\resources\com\laexclusiva\ui\images\brand\icons\la-exclusiva.ico'
        if (Test-Path -LiteralPath $reservedIcon) { $icon = $reservedIcon }
    }
    if ($icon) {
        if (-not (Test-Path -LiteralPath $icon)) { throw "No existe el icono indicado: $icon" }
        if ([IO.Path]::GetExtension($icon) -ne '.ico') { throw 'El icono de Windows debe ser un ICO aprobado.' }
        $icon = (Resolve-Path -LiteralPath $icon).Path
    } else {
        Write-Warning 'Se construirá sin icono personalizado; el ICO oficial aún no está disponible.'
    }

    $appImageDest = Join-Path $buildRoot 'app-image'
    New-Item -ItemType Directory -Path $appImageDest | Out-Null
    $appImageArgs = @(
        '--type', 'app-image', '--name', 'La Exclusiva', '--app-version', $AppVersion,
        '--vendor', 'La Exclusiva', '--description', 'Administración comercial de La Exclusiva',
        '--input', $inputDir, '--main-jar', 'la-exclusiva-desktop.jar',
        '--main-class', 'com.laexclusiva.packaging.WindowsDesktopLauncher', '--dest', $appImageDest
    )
    if ($icon) { $appImageArgs += @('--icon', $icon) }
    & $jpackageExe @appImageArgs
    if ($LASTEXITCODE -ne 0) { throw "jpackage app-image terminó con código $LASTEXITCODE." }

    $appImage = Join-Path $appImageDest 'La Exclusiva'
    $launcherExe = Join-Path $appImage 'La Exclusiva.exe'
    $bundledRuntimeModules = Join-Path $appImage 'runtime\lib\modules'
    $bundledJvm = Join-Path $appImage 'runtime\bin\server\jvm.dll'
    if (-not (Test-Path -LiteralPath $launcherExe) -or -not (Test-Path -LiteralPath $bundledRuntimeModules) -or
            -not (Test-Path -LiteralPath $bundledJvm)) {
        throw 'El app-image no contiene el launcher o el Java Runtime esperado.'
    }
    if (-not $SkipSmokeLaunch) {
        $env:LAEXCLUSIVA_PERSISTENCE_MODE = 'memory'
        $env:LAEXCLUSIVA_INITIAL_ADMIN_USERNAME = 'validacion-empaquetado'
        $env:LAEXCLUSIVA_INITIAL_ADMIN_PASSWORD = 'TmpPkg!' + [Guid]::NewGuid().ToString('N') + 'Aa9'
        $appProcess = Start-Process -FilePath $launcherExe -PassThru
        Start-Sleep -Seconds 12
        $appProcess.Refresh()
        if ($appProcess.HasExited) {
            throw "El launcher app-image terminó antes de la comprobación de arranque (código $($appProcess.ExitCode))."
        }
        $null = $appProcess.CloseMainWindow()
        if (-not $appProcess.WaitForExit(8000)) {
            Stop-Process -Id $appProcess.Id -Force
        }
        Write-Host 'Comprobación de arranque app-image aprobada: el proceso JavaFX permaneció activo 12 segundos.'
    }

    $installerPath = $null
    if ($Target -in @('installer', 'all')) {
        $resolvedWixBin = Resolve-WixBin -ProvidedPath $WixBin
        $env:PATH = "$resolvedWixBin;$env:PATH"
        if (-not (Test-Path -LiteralPath (Join-Path $resolvedWixBin 'candle.exe')) -or
                -not (Test-Path -LiteralPath (Join-Path $resolvedWixBin 'light.exe'))) {
            throw 'WiX debe proporcionar candle.exe y light.exe para crear el MSI.'
        }
        $installerDest = Join-Path $buildRoot 'installer'
        New-Item -ItemType Directory -Path $installerDest | Out-Null
        $installerArgs = @(
            '--type', 'msi', '--app-image', $appImage, '--name', 'La Exclusiva',
            '--app-version', $AppVersion, '--vendor', 'La Exclusiva',
            '--description', 'Administración comercial de La Exclusiva', '--dest', $installerDest,
            '--win-menu', '--win-menu-group', 'La Exclusiva', '--win-shortcut',
            '--win-per-user-install', '--win-upgrade-uuid', '97b7041c-94b5-4e1b-a8bd-f9d23f56853e'
        )
        if ($icon) { $installerArgs += @('--icon', $icon) }
        & $jpackageExe @installerArgs
        if ($LASTEXITCODE -ne 0) { throw "jpackage MSI terminó con código $LASTEXITCODE." }
        $installer = Get-ChildItem -LiteralPath $installerDest -Filter '*.msi' -File | Select-Object -First 1
        if (-not $installer) { throw 'jpackage no generó el MSI esperado.' }
        $installerPath = $installer.FullName
    }

    $reportFiles = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'target\surefire-reports') -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    $testTotals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0; approved = 0 }
    foreach ($reportFile in $reportFiles) {
        [xml]$report = Get-Content -LiteralPath $reportFile.FullName -Raw
        $suite = $report.testsuite
        $testTotals.tests += [int]$suite.tests
        $testTotals.failures += [int]$suite.failures
        $testTotals.errors += [int]$suite.errors
        $testTotals.skipped += [int]$suite.skipped
    }
    $testTotals.approved = $testTotals.tests - $testTotals.failures - $testTotals.errors - $testTotals.skipped
    $evidence = @(
        'La Exclusiva — evidencia de empaquetado Windows',
        "Fecha: $([DateTime]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz'))",
        "JDK: $javacVersion",
        "Maven: $mavenExe",
        "Maven clean verify: $($testTotals.tests) pruebas, $($testTotals.approved) aprobadas, $($testTotals.failures) fallidas, $($testTotals.errors) errores, $($testTotals.skipped) omitidas.",
        "App-image: $appImage",
        "Launcher: $launcherExe",
        "Runtime Java incluido: $bundledRuntimeModules",
        "Smoke launch: $(if ($SkipSmokeLaunch) { 'omitido bajo solicitud' } else { 'proceso JavaFX activo durante 12 segundos' })",
        "MSI: $(if ($installerPath) { $installerPath } else { 'no solicitado' })",
        "Icono personalizado: $(if ($icon) { $icon } else { 'pendiente; se usó icono genérico de jpackage' })",
        'Variables de base de datos y SMTP retiradas durante build y smoke launch; no se conectó a PostgreSQL.'
    )
    Set-Content -LiteralPath (Join-Path $buildRoot 'validation-summary.txt') -Value $evidence -Encoding UTF8
    Write-Host "Paquete generado en: $buildRoot"
    Write-Host "App-image: $appImage"
    if ($installerPath) { Write-Host "Instalador MSI: $installerPath" }
    Write-Host "Maven: $($testTotals.approved) aprobadas, $($testTotals.skipped) omitidas, $($testTotals.failures) fallidas, $($testTotals.errors) errores."
} finally {
    foreach ($name in $reservedEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
