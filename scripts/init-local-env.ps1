$ErrorActionPreference = 'Stop'
$workspacePath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envPath = Join-Path $workspacePath '.env'
if (Test-Path -LiteralPath $envPath) {
    Write-Output '.env exists; kept unchanged.'
    exit 0
}
$examplePath = Join-Path $workspacePath '.env.example'
$envContent = [System.IO.File]::ReadAllText($examplePath)
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtBytes = New-Object byte[] 32
    $passwordBytes = New-Object byte[] 24
    $random.GetBytes($jwtBytes)
    $random.GetBytes($passwordBytes)
} finally { $random.Dispose() }
$jwtSecret = [Convert]::ToBase64String($jwtBytes)
$databasePassword = [Convert]::ToBase64String($passwordBytes)
$envContent = [regex]::Replace($envContent, '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$jwtSecret")
$envContent = [regex]::Replace($envContent, '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$databasePassword")
# CreateNew prevents overwriting a file created concurrently.
$stream = [System.IO.File]::Open($envPath, [System.IO.FileMode]::CreateNew)
try {
    $envBytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($envContent)
    $stream.Write($envBytes, 0, $envBytes.Length)
} finally { $stream.Dispose() }
Write-Output 'Created ignored .env with fresh local database/JWT secrets. No secret printed.'
