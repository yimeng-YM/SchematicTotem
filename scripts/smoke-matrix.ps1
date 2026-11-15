param([string[]]$Minecraft, [string[]]$GradleArgument = @(), [switch]$NoSystemProxy)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$matrix = Get-Content -LiteralPath (Join-Path $projectRoot 'versioning/versions.json') -Raw | ConvertFrom-Json
if (-not $Minecraft) { $Minecraft = @($matrix.PSObject.Properties.Name) }
$arguments = @('-Psmoke', '--console=plain', '-Dfabric.loom.ci=true', '-Dorg.gradle.internal.http.socketTimeout=30000', '-Dorg.gradle.internal.http.connectionTimeout=15000') + $GradleArgument
if (-not $NoSystemProxy) {
    $uri = [Uri]'https://maven.fabricmc.net/'
    $proxy = [Net.WebRequest]::GetSystemWebProxy().GetProxy($uri)
    if ($proxy -and $proxy.Host -ne $uri.Host) {
        $arguments += @("-Dhttps.proxyHost=$($proxy.Host)", "-Dhttps.proxyPort=$($proxy.Port)", "-Dhttp.proxyHost=$($proxy.Host)", "-Dhttp.proxyPort=$($proxy.Port)")
    }
}
$reports = Join-Path $projectRoot 'build/verification/smoke'
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$results = @()
Push-Location $projectRoot
try {
    foreach ($targetMc in $Minecraft) {
        if (-not $matrix.PSObject.Properties[$targetMc]) { throw "Unknown Minecraft target: $targetMc" }
        $started = [DateTimeOffset]::Now
        $log = Join-Path $reports "$targetMc.log"
        $row = [ordered]@{ minecraft=$targetMc; status='failed'; seconds=0; screenshot=$null; report=$null; error=$null }
        Write-Host "Checking Minecraft $targetMc in an isolated client..."
        try {
            & ./gradlew.bat runSmokeClient "-Pmc=$targetMc" @arguments *> $log
            if ($LASTEXITCODE -ne 0) { throw "Client exited with code $LASTEXITCODE. See $log" }
            $reportPath = Join-Path $projectRoot "run/compat/$targetMc/compatibility-smoke.json"
            $reportFile = Get-Item -LiteralPath $reportPath
            if ($reportFile.LastWriteTimeUtc -lt $started.UtcDateTime) { throw 'The client did not produce a fresh result.' }
            $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
            if ($report.status -ne 'passed' -or $report.minecraft -ne $targetMc) { throw 'Client report does not match the requested version.' }
            $screenshot = Get-ChildItem -LiteralPath "run/compat/$targetMc/screenshots" -Filter '*.png' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
            if (-not $screenshot -or $screenshot.LastWriteTimeUtc -lt $started.UtcDateTime) { throw 'The client did not write a fresh screenshot.' }
            $row.screenshot = $screenshot.FullName
            $row.report = $report
            $row.status = 'passed'
        } catch { $row.error = $_.Exception.Message }
        $row.seconds = [Math]::Round(([DateTimeOffset]::Now - $started).TotalSeconds,2)
        $results += [PSCustomObject]$row
        ConvertTo-Json -InputObject @($results) -Depth 8 | Set-Content -LiteralPath (Join-Path $reports 'matrix.json') -Encoding utf8
        Write-Host "$targetMc : $($row.status) ($($row.seconds)s)"
    }
} finally { Pop-Location }
if (@($results | Where-Object status -EQ 'failed').Count -gt 0) { exit 1 }
