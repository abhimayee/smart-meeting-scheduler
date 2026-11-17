Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
Write-Host ("[start-local] Script: {0} (PowerShell {1})" -f $PSCommandPath, $PSVersionTable.PSVersion)

$projectRoot = Split-Path -Parent $PSScriptRoot
$localEnvFile = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $localEnvFile) {
    foreach ($line in Get-Content -LiteralPath $localEnvFile) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -match '^([^=]+)=(.*)$') {
            $name = $Matches[1].Trim()
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                    ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
    throw 'DB_PASSWORD is missing. Create a local .env from .env.example and set the local PostgreSQL password, or provide DB_PASSWORD in this PowerShell process.'
}
$runDirectory = Join-Path $projectRoot 'target/local-run'
$logDirectory = Join-Path $projectRoot 'target/local-logs'
$pidFile = Join-Path $runDirectory 'services.json'
$services = @(
    @{ Name = 'user-service'; Jar = 'user-service/target/user-service-0.2.0-SNAPSHOT.jar'; Port = 8081; Probe = '/api/users/scheduler-demo-organizer@example.com' },
    @{ Name = 'calendar-service'; Jar = 'calendar-service/target/calendar-service-0.2.0-SNAPSHOT.jar'; Port = 8082; Probe = '/v3/api-docs' },
    @{ Name = 'scheduling-service'; Jar = 'scheduling-service/target/scheduling-service-0.2.0-SNAPSHOT.jar'; Port = 8083; Probe = '/v3/api-docs' },
    @{ Name = 'meeting-service'; Jar = 'meeting-service/target/meeting-service-0.2.0-SNAPSHOT.jar'; Port = 8084; Probe = '/v3/api-docs' },
    @{ Name = 'ai-meeting-service'; Jar = 'future/phase7-ai-prototype/target/ai-meeting-service-0.2.0-SNAPSHOT.jar'; Port = 8085; Probe = '/v3/api-docs' }
)

function Test-LocalPort([int] $Port, [string] $Address = '127.0.0.1') {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect($Address, $Port, $null, $null)
        return $pending.AsyncWaitHandle.WaitOne(250) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-ServiceLogTail($Service, [int] $LineCount = 60) {
    foreach ($suffix in @('out.log', 'err.log')) {
        $path = Join-Path $logDirectory ($Service.Name + '.' + $suffix)
        if (Test-Path -LiteralPath $path) {
            "--- $($Service.Name) $suffix (last $LineCount lines) ---"
            Get-Content -LiteralPath $path -Tail $LineCount
        }
    }
}

function Wait-HttpReady($Service, [System.Diagnostics.Process] $Process, [int] $TimeoutSeconds = 120) {
    $uri = "http://127.0.0.1:{0}{1}" -f $Service.Port, $Service.Probe
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastProbe = 'No HTTP response received yet.'
    $httpClient = [System.Net.Http.HttpClient]::new()
    $httpClient.Timeout = [TimeSpan]::FromSeconds(3)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) {
            $Process.Refresh()
            throw "Service '$($Service.Name)' process exited before HTTP readiness at $uri.`n$(Get-ServiceLogTail $Service)"
        }
        try {
            $response = $httpClient.GetAsync($uri).GetAwaiter().GetResult()
            try {
                $statusCode = [int] $response.StatusCode
                $lastProbe = "HTTP $statusCode $($response.ReasonPhrase)"
                if ($statusCode -lt 200 -or $statusCode -ge 300) {
                    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                    if ($body.Length -gt 1000) { $body = $body.Substring(0, 1000) + '…' }
                    if (-not [string]::IsNullOrWhiteSpace($body)) { $lastProbe += "`nResponse body: $body" }
                }
            } finally {
                $response.Dispose()
            }
            if ($statusCode -ge 200 -and $statusCode -lt 300) {
                $httpClient.Dispose()
                return
            }
        } catch {
            $probeException = $_.Exception.GetBaseException()
            $lastProbe = "Request exception ($($probeException.GetType().FullName)): $($probeException.Message)"
        }
        Start-Sleep -Seconds 2
    }
    $httpClient.Dispose()
    throw "Service '$($Service.Name)' did not become ready within $TimeoutSeconds seconds at $uri. Last probe: $lastProbe.`n$(Get-ServiceLogTail $Service)"
}

Push-Location $projectRoot
try {
    if (Test-Path -LiteralPath $pidFile) {
        $previous = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
        $running = @($previous | Where-Object { Get-Process -Id $_.processId -ErrorAction SilentlyContinue })
        if ($running.Count -gt 0) { throw 'A local service run is already recorded. Stop those processes before starting another set.' }
        Remove-Item -LiteralPath $pidFile -Force
    }
    foreach ($service in $services) {
        if (Test-LocalPort $service.Port) { throw "Port $($service.Port) is already in use; no services were started." }
        if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $service.Jar))) {
            throw "Runnable jar is missing: $($service.Jar). Run mvn -DskipTests package first."
        }
    }
    if (-not (Test-LocalPort -Port 5432 -Address 'localhost') -and
            -not (Test-LocalPort -Port 5432 -Address '127.0.0.1')) {
        throw 'PostgreSQL is not accepting TCP connections at localhost:5432 as configured by the database-backed services. No application services were started.'
    }

    New-Item -ItemType Directory -Path $runDirectory, $logDirectory -Force | Out-Null
    $started = [System.Collections.Generic.List[object]]::new()
    $currentService = $null
    try {
        foreach ($service in $services) {
            $currentService = $service
            $jarPath = Join-Path $projectRoot $service.Jar
            $stdout = Join-Path $logDirectory ($service.Name + '.out.log')
            $stderr = Join-Path $logDirectory ($service.Name + '.err.log')
            Write-Host ("Launching {0}; stdout={1}; stderr={2}" -f $service.Name, $stdout, $stderr)
            $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', ('"{0}"' -f $jarPath)) `
                -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout `
                -RedirectStandardError $stderr -PassThru
            $started.Add([pscustomobject]@{ name = $service.Name; processId = $process.Id; port = $service.Port })
            Wait-HttpReady -Service $service -Process $process
            Write-Host ("Started {0} on port {1} (PID {2})" -f $service.Name, $service.Port, $process.Id)
        }
        $started | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding utf8
        Write-Host 'All services are ready. Logs are in target/local-logs.'
    } catch {
        if ($null -ne $currentService) {
            Write-Error "Local startup failed while starting '$($currentService.Name)': $($_.Exception.Message)" -ErrorAction Continue
        }
        foreach ($entry in $started) { Stop-Process -Id $entry.processId -Force -ErrorAction SilentlyContinue }
        throw
    }
} finally {
    Pop-Location
}
