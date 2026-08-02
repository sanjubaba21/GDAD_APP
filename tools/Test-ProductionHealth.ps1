param(
    [string]$ProjectRef = $env:SUPABASE_PRODUCTION_PROJECT_REF,
    [string]$PublishableKey = $env:SUPABASE_PRODUCTION_HEALTH_PUBLISHABLE_KEY
)

$ErrorActionPreference = "Stop"
$expectedProjectRef = "skfxfbssfeetquteubcn"

if ($ProjectRef -ne $expectedProjectRef) {
    throw "Production health check rejected an unexpected project reference."
}

if ([string]::IsNullOrWhiteSpace($PublishableKey) -or
    $PublishableKey.Length -gt 256 -or
    -not $PublishableKey.StartsWith("sb_publishable_", [System.StringComparison]::Ordinal)) {
    throw "Production health check requires the client-safe publishable key."
}

$PublishableKey = $PublishableKey.Trim()
$projectUrl = "https://$ProjectRef.supabase.co"

Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(20)
$client.DefaultRequestHeaders.Add("apikey", $PublishableKey)
$client.DefaultRequestHeaders.Add("user-agent", "gdad-production-health/1")

function Invoke-HealthProbe {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpMethod]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [int]$ExpectedStatus,

        [string]$JsonBody,
        [string]$ExpectedCode,
        [string]$ExpectedMessage,
        [switch]$RequireOperationalHeaders
    )

    $request = New-Object System.Net.Http.HttpRequestMessage($Method, "$projectUrl$Path")
    if (-not [string]::IsNullOrWhiteSpace($JsonBody)) {
        $request.Content = New-Object System.Net.Http.StringContent(
            $JsonBody,
            [System.Text.Encoding]::UTF8,
            "application/json"
        )
    }

    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $status = [int]$response.StatusCode

        if ($status -ne $ExpectedStatus) {
            throw "Health probe returned HTTP $status instead of $ExpectedStatus."
        }

        if (-not [string]::IsNullOrWhiteSpace($ExpectedCode)) {
            try {
                $parsed = $body | ConvertFrom-Json
            }
            catch {
                throw "Health probe returned an invalid JSON error envelope."
            }

            if ($parsed.code -ne $ExpectedCode) {
                throw "Health probe returned an unexpected safe error code."
            }
        }

        if (-not [string]::IsNullOrWhiteSpace($ExpectedMessage)) {
            try {
                $parsedMessage = $body | ConvertFrom-Json
            }
            catch {
                throw "Health probe returned an invalid JSON boundary response."
            }

            if ($parsedMessage.message -ne $ExpectedMessage) {
                throw "Health probe returned an unexpected security-boundary message."
            }
        }

        if ($RequireOperationalHeaders) {
            if ($null -eq $response.Headers.CacheControl -or
                -not $response.Headers.CacheControl.NoStore) {
                throw "Health probe response is missing Cache-Control: no-store."
            }

            $correlationValues = $null
            if (-not $response.Headers.TryGetValues("x-gdad-correlation-id", [ref]$correlationValues)) {
                throw "Health probe response is missing its correlation identifier."
            }

            $correlationId = [Guid]::Empty
            if (-not [Guid]::TryParse(($correlationValues | Select-Object -First 1), [ref]$correlationId) -or
                $correlationId -eq [Guid]::Empty) {
                throw "Health probe response has an invalid correlation identifier."
            }
        }

        Write-Output ("PASS {0} HTTP {1}" -f $Path, $status)
    }
    finally {
        if ($null -ne $response) { $response.Dispose() }
        $request.Dispose()
    }
}

try {
    Invoke-HealthProbe -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/auth/v1/health" -ExpectedStatus 200
    Invoke-HealthProbe -Method ([System.Net.Http.HttpMethod]::Get) `
        -Path "/rest/v1/" -ExpectedStatus 401 -ExpectedMessage "Secret API key required"

    foreach ($functionName in @("pin-login", "manage-users")) {
        Invoke-HealthProbe -Method ([System.Net.Http.HttpMethod]::Post) `
            -Path "/functions/v1/$functionName" -ExpectedStatus 400 -JsonBody "{}" `
            -ExpectedCode "INVALID_REQUEST" -RequireOperationalHeaders
    }

    $protectedBody = '{"action":"disable_user","request_id":"550e8400-e29b-41d4-a716-446655440000","target_user_id":"650e8400-e29b-41d4-a716-446655440000","reauth_pin":"000000"}'
    Invoke-HealthProbe -Method ([System.Net.Http.HttpMethod]::Post) `
        -Path "/functions/v1/manage-accounts" -ExpectedStatus 401 -JsonBody $protectedBody `
        -ExpectedCode "UNAUTHORIZED" -RequireOperationalHeaders

    Write-Output "Production health checks passed without a privileged credential or mutation."
}
finally {
    $client.Dispose()
    $handler.Dispose()
    $PublishableKey = $null
}
