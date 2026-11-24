param([string]$Configuration)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $project
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot'
if (-not (Test-Path "$jdk\bin\java.exe")) { throw 'Configure una instalación Temurin Java 17 antes de iniciar.' }
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$maven = if ($mavenCommand) { $mavenCommand.Source } else {
    'C:\Users\andre\OneDrive\Documentos\OneDrive\Desktop\Nueva carpeta\netbeans\java\maven\bin\mvn.cmd'
}
if (-not (Test-Path -LiteralPath $maven)) { throw 'No se encontró Maven. Configure PATH solo en esta terminal.' }
if ($Configuration -and -not (Test-Path -LiteralPath $Configuration)) { throw 'No existe la configuración solicitada.' }
if ($Configuration) { $Configuration = (Resolve-Path -LiteralPath $Configuration).Path }

# Se rechaza un segundo arranque, sin cerrar automáticamente ni perder trabajo del usuario.
$running = Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'La Exclusiva*' }
if ($running) { throw 'Ya hay una ventana de La Exclusiva abierta. Ciérrela para iniciar una demo nueva.' }
$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$env:PATH"
$env:LAEXCLUSIVA_PERSISTENCE_MODE = 'memory'
Remove-Item Env:LAEXCLUSIVA_DB_URL,Env:LAEXCLUSIVA_TEST_DB_URL -ErrorAction SilentlyContinue

# Solo compilación; no ejecuta SQL, migraciones ni pruebas PostgreSQL.
& $maven -o '-DskipTests' test-compile
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar la herramienta de demostración.' }
$repository = Join-Path $env:USERPROFILE '.m2\repository'
$classpath = @((Join-Path $project 'target\classes'), (Join-Path $project 'target\test-classes'))
foreach ($module in @('javafx-base','javafx-graphics','javafx-controls','javafx-fxml')) {
    $jar = Join-Path $repository "org\openjfx\$module\17.0.2\$module-17.0.2-win.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw "Falta la dependencia local $module 17.0.2 para Windows." }
    $classpath += $jar
}
$jdbc = Join-Path $repository 'org\postgresql\postgresql\42.7.14\postgresql-42.7.14.jar'
if (-not (Test-Path -LiteralPath $jdbc)) { throw 'Falta el JAR JDBC requerido por las clases compartidas; no se abrirá una conexión.' }
$classpath += $jdbc

# No hay contraseña predeterminada ni secretos en archivos, argumentos Java o registros.
$env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD = 'Demo-' + [Guid]::NewGuid().ToString('N')
$env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD = 'Demo-' + [Guid]::NewGuid().ToString('N')
$credentialDialog = @'
Add-Type -AssemblyName System.Windows.Forms
$form = New-Object System.Windows.Forms.Form
$form.Text = 'La Exclusiva - credenciales de demostración completa'
$form.Width = 800; $form.Height = 390; $form.StartPosition = 'CenterScreen'
$text = New-Object System.Windows.Forms.TextBox
$text.Multiline = $true; $text.ReadOnly = $true; $text.Dock = 'Fill'
$text.Font = New-Object System.Drawing.Font('Consolas', 12)
$text.Text = "DATOS FICTICIOS EN MEMORIA. Minimiza este cuadro, no lo cierres.`r`n`r`nAdministrador: admin`r`nContraseña: $env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD`r`n`r`nTrabajadores: elena.torres, andres.molina, operador`r`nContraseña: $env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD`r`n`r`nCuenta inactiva: paula.serrano (misma contraseña de trabajador).`r`n`r`nelena.torres y andres.molina tienen vendedor exclusivo asignado.`r`nCopia la contraseña con Ctrl+C. No se guarda en archivos."
$form.Controls.Add($text)
[void]$form.ShowDialog()
'@
$encodedDialog = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($credentialDialog))
$dialogProcess = Start-Process powershell.exe -WindowStyle Hidden -ArgumentList '-NoProfile','-EncodedCommand',$encodedDialog -PassThru
try {
    $javaArguments = @('-cp', ($classpath -join ';'), 'com.laexclusiva.demo.InteractiveDemoReview')
    if ($Configuration) { $javaArguments += $Configuration }
    & "$jdk\bin\java.exe" @javaArguments
    if ($LASTEXITCODE -ne 0) { throw 'La demostración no pudo iniciarse; revise los mensajes de validación sin compartir credenciales.' }
} finally {
    Remove-Item Env:LAEXCLUSIVA_DEMO_ADMIN_PASSWORD,Env:LAEXCLUSIVA_DEMO_WORKER_PASSWORD -ErrorAction SilentlyContinue
    # El cuadro no debe dejar credenciales visibles después de cerrar la demo.
    if (-not $dialogProcess.HasExited) { Stop-Process -Id $dialogProcess.Id -ErrorAction SilentlyContinue }
}
