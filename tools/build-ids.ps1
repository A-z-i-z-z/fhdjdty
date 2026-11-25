# The one place the tools take package and activity names from (Wave 0 contract, section 3.1).
# Dot-source it:   . (Join-Path $PSScriptRoot 'build-ids.ps1')
# It only defines values and two functions; it has no side effects. Works on Windows PowerShell 5.1 and pwsh 7.
#
#   dev.pocketagent.app          the owner's app (release and owner-debug builds)
#   dev.pocketagent.app.debug    the test twin
#
# The Kotlin namespace is dev.pocketagent.app for every variant, so a class is always dev.pocketagent.app.<Class>,
# whatever the installed package id is. Never write "adb ... -n <pkg>/.Class" for the twin: the leading-dot shorthand
# resolves against the package id and points at a class that does not exist. Use Get-PaComponent.

$PaNamespace = 'dev.pocketagent.app'
$PaOwnerPackage = 'dev.pocketagent.app'
$PaTwinPackage = 'dev.pocketagent.app.debug'
$PaMainActivity = 'dev.pocketagent.app.MainActivity'

# "<Package>/<fully qualified class>". A ClassName that starts with '.' gets $PaNamespace (NOT the package) in front.
function Get-PaComponent([string]$Package, [string]$ClassName) {
    $cls = $ClassName
    if ($cls.StartsWith('.')) { $cls = $PaNamespace + $cls }
    return ($Package + '/' + $cls)
}

# $true only for the owner's package.
function Test-PaOwnerPackage([string]$Package) {
    return ($Package -eq $PaOwnerPackage)
}
