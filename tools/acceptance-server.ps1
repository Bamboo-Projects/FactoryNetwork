param(
    [string]$Java = 'java',
    [switch]$Install,
    [switch]$AcceptEula
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testServer = Join-Path $projectRoot 'build/acceptance-server'
$properties = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties')
$neoVersion = ($properties | Where-Object { $_ -match '^neo_version=' }) -replace '^neo_version=', ''
$modVersion = ($properties | Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
New-Item -ItemType Directory -Force -Path $testServer | Out-Null
$argsPath = Join-Path $testServer "libraries/net/neoforged/neoforge/$neoVersion/win_args.txt"
if (-not (Test-Path -LiteralPath $argsPath)) {
    if (-not $Install) { throw 'Run with -Install to install the pinned NeoForge server first.' }
    $installer = Join-Path $testServer "neoforge-$neoVersion-installer.jar"
    if (-not (Test-Path -LiteralPath $installer)) {
        Invoke-WebRequest -Uri "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoVersion/neoforge-$neoVersion-installer.jar" -OutFile $installer
    }
    Push-Location $testServer
    try {
        & $Java -jar $installer --installServer $testServer
        if ($LASTEXITCODE -ne 0) { throw 'NeoForge installation failed.' }
    } finally { Pop-Location }
}
$eula = Join-Path $testServer 'eula.txt'
if ($AcceptEula) { 'eula=true' | Set-Content -LiteralPath $eula -Encoding utf8 }
if (-not (Test-Path -LiteralPath $eula) -or -not (Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)) {
    throw 'The Minecraft EULA must be accepted for this local test server (-AcceptEula).'
}
$serverProperties = Join-Path $testServer 'server.properties'
if (-not (Test-Path -LiteralPath $serverProperties)) {
    @'
server-ip=127.0.0.1
server-port=25576
online-mode=false
enforce-secure-profile=false
level-name=acceptance-world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-protection=0
view-distance=4
simulation-distance=4
max-players=2
motd=FactoryNetwork isolated acceptance test
'@ | Set-Content -LiteralPath $serverProperties -Encoding utf8
}
if (-not (Select-String -LiteralPath $serverProperties -Pattern '^server-ip=127\.0\.0\.1$' -Quiet)) {
    throw 'Acceptance server must remain bound to loopback.'
}
$mods = Join-Path $testServer 'mods'
New-Item -ItemType Directory -Force -Path $mods | Out-Null
$expected = @("factorynetwork-$modVersion.jar", "factorynetwork-acceptance-$modVersion.jar")
$extraMods = Get-ChildItem -LiteralPath $mods -Filter '*.jar' | Where-Object { $_.Name -notin $expected }
if ($extraMods) { throw 'Unexpected mods in the isolated test server; use exactly the two current acceptance jars.' }
foreach ($name in $expected) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "build/libs/$name") -Destination (Join-Path $mods $name)
}
Push-Location $testServer
try { & $Java -Xmx2G "@libraries/net/neoforged/neoforge/$neoVersion/win_args.txt" nogui }
finally { Pop-Location }
