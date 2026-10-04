$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '../.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copier .env.example vers .env avant de continuer.'
}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
        throw 'Ligne .env invalide : utiliser NOM=valeur, sans guillemets.'
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
}
