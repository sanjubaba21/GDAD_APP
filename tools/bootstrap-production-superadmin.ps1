param(
    [string]$ProjectRef = $env:SUPABASE_PRODUCTION_PROJECT_REF,
    [string]$ProjectUrl = $env:SUPABASE_URL,
    [string]$PublishableKey = $env:SUPABASE_PUBLISHABLE_KEY
)

$ErrorActionPreference = "Stop"
$developmentProjectRef = "zniqkuwktvincjndcgpu"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$cli = Join-Path $root "node_modules\.pnpm\@supabase+cli-windows-x64@2.111.0\node_modules\@supabase\cli-windows-x64\bin\supabase.exe"
$pin = $null
$bootstrapToken = $null
$bootstrapJson = $null
$loginJson = $null
$bootstrapResponse = $null
$loginResponse = $null
$account = $null
$session = $null
$secretInstalled = $false
$completed = $false
$stage = "preflight"

function Convert-SecureValue([Security.SecureString]$Value) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function New-RandomToken {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Invoke-SupabaseQuiet([string[]]$Arguments) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $cli @Arguments 1>$null 2>$null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Get-SupabaseProjects {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $json = & $cli projects list --output json 2>$null | Out-String
        if ($LASTEXITCODE -ne 0) {
            throw "Supabase project lookup failed. Run supabase login and retry."
        }
        return @($json | ConvertFrom-Json)
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Invoke-JsonPost([string]$Url, [hashtable]$Headers, [string]$Json) {
    Add-Type -AssemblyName System.Net.Http
    $client = [Net.Http.HttpClient]::new()
    try {
        foreach ($name in $Headers.Keys) {
            $client.DefaultRequestHeaders.Add($name, [string]$Headers[$name])
        }
        $content = [Net.Http.StringContent]::new($Json, [Text.Encoding]::UTF8, "application/json")
        $response = $client.PostAsync($Url, $content).GetAwaiter().GetResult()
        try {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $correlationId = $null
            if ($response.Headers.Contains("x-gdad-correlation-id")) {
                $correlationId = @($response.Headers.GetValues("x-gdad-correlation-id"))[0]
            }
            return @{
                Status = [int]$response.StatusCode
                Body = $body
                CorrelationId = [string]$correlationId
            }
        } finally {
            $response.Dispose()
            $content.Dispose()
        }
    } finally {
        $client.Dispose()
    }
}

function Assert-CorrelationId([string]$Value) {
    $parsed = [Guid]::Empty
    if (-not [Guid]::TryParse($Value, [ref]$parsed)) {
        throw "The hosted response did not include a valid safe correlation ID."
    }
}

function ConvertFrom-JsonSafe([string]$Json, [string]$FailureMessage) {
    try {
        return $Json | ConvertFrom-Json
    } catch {
        throw $FailureMessage
    }
}

function Get-JwtSubject([string]$Token) {
    $parts = $Token.Split(".")
    if ($parts.Length -ne 3) { throw "PIN login returned a malformed session token." }
    $payloadPart = $parts[1].Replace("-", "+").Replace("_", "/")
    while (($payloadPart.Length % 4) -ne 0) { $payloadPart += "=" }
    $payloadJson = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payloadPart))
    $payload = ConvertFrom-JsonSafe $payloadJson "PIN login returned an invalid session payload."
    return [string]$payload.sub
}

try {
    if (-not (Test-Path -LiteralPath $cli -PathType Leaf)) {
        throw "Pinned Supabase CLI 2.111.0 is missing. Run pnpm install --frozen-lockfile."
    }
    if ($ProjectRef -notmatch "^[a-z0-9]{20}$") {
        throw "SUPABASE_PRODUCTION_PROJECT_REF must be a valid 20-character project ref."
    }
    if ($ProjectRef -eq $developmentProjectRef) {
        throw "Production bootstrap refuses the known development project."
    }
    if ([string]::IsNullOrWhiteSpace($ProjectUrl)) {
        throw "SUPABASE_URL is required."
    }
    $expectedUrl = "https://$ProjectRef.supabase.co"
    if ($ProjectUrl.TrimEnd("/") -ne $expectedUrl) {
        throw "SUPABASE_URL must exactly match the production project ref."
    }
    if ([string]::IsNullOrWhiteSpace($PublishableKey) -or
        -not $PublishableKey.StartsWith("sb_publishable_") -or $PublishableKey.Length -gt 256) {
        throw "SUPABASE_PUBLISHABLE_KEY is not a valid client-safe publishable key."
    }
    $projects = Get-SupabaseProjects
    if (@($projects | Where-Object { $_.ref -eq $ProjectRef -and $_.status -eq "ACTIVE_HEALTHY" }).Count -ne 1) {
        throw "The production project is not visible as ACTIVE_HEALTHY to the signed-in CLI account."
    }

    Write-Host "GDAD BAGS - controlled production Super Admin bootstrap" -ForegroundColor Cyan
    Write-Host "The PIN and one-time token are never printed or written to disk."
    Write-Host ("Target project: {0}" -f $ProjectRef)
    Write-Host ""

    $loginId = (Read-Host "Login ID (lowercase letters, numbers, dot, underscore, or hyphen)").Trim().ToLowerInvariant()
    $displayName = (Read-Host "Display name").Trim()
    $securePin = Read-Host "PIN (6-8 digits; avoid repeated/sequential/common values)" -AsSecureString
    try {
        $pin = Convert-SecureValue $securePin
    } finally {
        $securePin.Dispose()
    }

    if ($loginId -notmatch "^[a-z0-9][a-z0-9._-]{2,63}$") { throw "Login ID format is invalid." }
    if ($displayName.Length -lt 1 -or $displayName.Length -gt 120) { throw "Display name must be 1-120 characters." }
    if ($pin -notmatch "^\d{6,8}$") { throw "PIN must contain 6-8 digits." }
    if ($pin -match "^(\d)\1+$" -or @("123456", "1234567", "12345678", "654321", "7654321", "87654321", "121212", "112233") -contains $pin) {
        throw "Choose a less predictable PIN."
    }
    if ((Read-Host "Type CREATE PRODUCTION to create the sole initial Super Admin") -cne "CREATE PRODUCTION") {
        throw "Cancelled before hosted change."
    }

    $stage = "secret-install"
    $bootstrapToken = New-RandomToken
    if ((Invoke-SupabaseQuiet @("secrets", "set", "GDAD_BOOTSTRAP_TOKEN=$bootstrapToken", "--project-ref", $ProjectRef)) -ne 0) {
        throw "Could not install the one-time bootstrap secret."
    }
    $secretInstalled = $true

    $stage = "bootstrap-request"
    $requestId = [Guid]::NewGuid().ToString()
    $bootstrapJson = @{
        action = "bootstrap_super_admin"
        request_id = $requestId
        login_id = $loginId
        display_name = $displayName
        pin = $pin
    } | ConvertTo-Json -Compress

    for ($attempt = 1; $attempt -le 5; $attempt++) {
        $bootstrapResponse = Invoke-JsonPost "$expectedUrl/functions/v1/manage-users" @{
            apikey = $PublishableKey
            "x-gdad-bootstrap-token" = $bootstrapToken
        } $bootstrapJson
        Assert-CorrelationId $bootstrapResponse.CorrelationId
        if ($bootstrapResponse.Status -in @(200, 201)) { break }
        if ($attempt -lt 5 -and $bootstrapResponse.Status -in @(500, 503)) {
            Start-Sleep -Seconds 2
            continue
        }
        break
    }
    if ($bootstrapResponse.Status -notin @(200, 201)) {
        $errorPayload = try { $bootstrapResponse.Body | ConvertFrom-Json } catch { $null }
        $errorCode = if ($errorPayload -and $errorPayload.code) { [string]$errorPayload.code } else { "UNKNOWN" }
        $errorStage = if ($errorPayload -and $errorPayload.stage) { "; stage=" + [string]$errorPayload.stage } else { "" }
        throw ("Bootstrap failed with HTTP {0} ({1}{2}; correlation={3})." -f $bootstrapResponse.Status, $errorCode, $errorStage, $bootstrapResponse.CorrelationId)
    }
    $account = ConvertFrom-JsonSafe $bootstrapResponse.Body "Bootstrap returned an invalid response."

    $stage = "pin-login-verification"
    $loginJson = @{
        login_id = $loginId
        pin = $pin
        request_id = [Guid]::NewGuid().ToString()
        device_id = "production-bootstrap-" + [Guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    $loginResponse = Invoke-JsonPost "$expectedUrl/functions/v1/pin-login" @{
        apikey = $PublishableKey
    } $loginJson
    Assert-CorrelationId $loginResponse.CorrelationId
    if ($loginResponse.Status -ne 200) {
        $errorCode = try { [string](($loginResponse.Body | ConvertFrom-Json).code) } catch { "UNKNOWN" }
        throw ("Account was created, but PIN login verification failed with HTTP {0} ({1}; correlation={2})." -f $loginResponse.Status, $errorCode, $loginResponse.CorrelationId)
    }
    $session = ConvertFrom-JsonSafe $loginResponse.Body "PIN login returned an invalid response."
    if ((Get-JwtSubject ([string]$session.access_token)) -ne [string]$account.user_id) {
        throw "PIN login returned a session for the wrong Auth subject."
    }

    $completed = $true
    Write-Host ""
    Write-Host "Production Super Admin created and PIN-login Auth subject verified." -ForegroundColor Green
    Write-Host ("Safe correlation ID: {0}" -f $loginResponse.CorrelationId)
} catch {
    Write-Host ""
    Write-Host ("Production bootstrap failed at {0}: {1}" -f $stage, $_.Exception.Message) -ForegroundColor Red
    Write-Host "No PIN, session token, or bootstrap token was logged."
} finally {
    if ($secretInstalled) {
        $unsetExitCode = Invoke-SupabaseQuiet @("secrets", "unset", "GDAD_BOOTSTRAP_TOKEN", "--project-ref", $ProjectRef, "--yes")
        if ($unsetExitCode -eq 0) {
            Write-Host "One-time production bootstrap secret removed." -ForegroundColor Green
        } else {
            Write-Host "CRITICAL: bootstrap secret removal failed; remove GDAD_BOOTSTRAP_TOKEN in Supabase immediately." -ForegroundColor Red
            $completed = $false
        }
    }
    $pin = $null
    $bootstrapToken = $null
    $bootstrapJson = $null
    $loginJson = $null
    $bootstrapResponse = $null
    $loginResponse = $null
    $account = $null
    $session = $null
    if (-not $completed) { exit 1 }
}
