param(
    [string[]]$Minecraft,
    [switch]$NoSystemProxy,
    [switch]$SkipTests,
    [string[]]$GradleArgument = @()
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$matrix = Get-Content -LiteralPath (Join-Path $projectRoot 'versioning/versions.json') -Raw | ConvertFrom-Json
if (-not $Minecraft) { $Minecraft = @($matrix.PSObject.Properties.Name) }
foreach ($targetMc in $Minecraft) {
    if (-not $matrix.PSObject.Properties[$targetMc]) { throw "Unknown Minecraft target: $targetMc" }
}

$commonArguments = @('--console=plain', '-Dorg.gradle.internal.http.socketTimeout=30000', '-Dorg.gradle.internal.http.connectionTimeout=15000')
$commonArguments += $GradleArgument
if (-not $NoSystemProxy) {
    $repositoryUri = [Uri]'https://maven.fabricmc.net/'
    $systemProxy = [Net.WebRequest]::GetSystemWebProxy().GetProxy($repositoryUri)
    if ($systemProxy -and $systemProxy.Host -ne $repositoryUri.Host) {
        $commonArguments += @("-Dhttps.proxyHost=$($systemProxy.Host)", "-Dhttps.proxyPort=$($systemProxy.Port)",
                              "-Dhttp.proxyHost=$($systemProxy.Host)", "-Dhttp.proxyPort=$($systemProxy.Port)")
        Write-Host "Using the configured system proxy: $($systemProxy.Host):$($systemProxy.Port)"
    }
}

$reportDirectory = Join-Path $projectRoot 'build/verification/matrix'
$releaseDirectory = Join-Path $projectRoot 'dist/1.3.0'
New-Item -ItemType Directory -Path $reportDirectory,$releaseDirectory -Force | Out-Null
$results = @()
Push-Location $projectRoot
try {
    foreach ($targetMc in $Minecraft) {
        $started = [DateTimeOffset]::Now
        $configuration = $matrix.PSObject.Properties[$targetMc].Value
        $log = Join-Path $reportDirectory "$targetMc.log"
        $arguments = @('build', "-Pmc=$targetMc") + $commonArguments
        if ($targetMc -eq '1.21.11') { $arguments += 'compileGametestJava' }
        if ($SkipTests) { $arguments += @('-x','test') }
        $row = [ordered]@{ minecraft=$targetMc; java=$configuration.java; status='failed'; tests=0; skipped=0; artifact=$null; sha256=$null; seconds=0; log=$log; error=$null }
        Write-Host "Building Minecraft $targetMc..."
        try {
            & ./gradlew.bat @arguments *> $log
            $buildExit = $LASTEXITCODE
            if ($buildExit -ne 0) { throw "Gradle exited with code $buildExit. See $log" }
            $testReports = @(Get-ChildItem -LiteralPath "build/versions/$targetMc/test-results/test" -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
            foreach ($report in $testReports) {
                [xml]$suite = Get-Content -LiteralPath $report.FullName -Raw
                $row.tests += [int]$suite.testsuite.tests
                $row.skipped += [int]$suite.testsuite.skipped
                if ([int]$suite.testsuite.failures -ne 0 -or [int]$suite.testsuite.errors -ne 0) { throw 'A test report contains failures.' }
            }
            if (-not $SkipTests -and ($row.tests -lt 3 -or $row.skipped -ne 0)) { throw 'Expected the three portable tests with no skipped cases.' }
            $artifactName = "schematic-totem-1.3.0+mc$targetMc.jar"
            $artifact = Join-Path $projectRoot "build/versions/$targetMc/libs/$artifactName"
            if (-not (Test-Path -LiteralPath $artifact)) { throw "Missing production artifact: $artifact" }
            $zip = [IO.Compression.ZipFile]::OpenRead($artifact)
            try {
                $entry = $zip.GetEntry('fabric.mod.json')
                if (-not $entry) { throw 'Mod metadata is absent.' }
                $reader = [IO.StreamReader]::new($entry.Open())
                try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
                if ($metadata.version -ne "1.3.0+mc$targetMc" -or $metadata.depends.minecraft -ne "=$targetMc") { throw 'Artifact version metadata does not match its target.' }
                $expectedMajor = [int]$configuration.java + 44
                foreach ($class in @($zip.Entries | Where-Object FullName -Like '*.class')) {
                    if (-not $class.FullName.StartsWith('dev/yimeng/schematictotem/')) { throw "Unexpected bundled class: $($class.FullName)" }
                    $stream = $class.Open()
                    try {
                        $header = [byte[]]::new(8)
                        if ($stream.Read($header,0,8) -ne 8) { throw 'Invalid class header.' }
                        $major = [int]$header[6] * 256 + [int]$header[7]
                        if ($major -ne $expectedMajor) { throw "Unexpected Java bytecode $major in $($class.FullName)" }
                    } finally { $stream.Dispose() }
                }
            } finally { $zip.Dispose() }
            $destination = Join-Path $releaseDirectory $artifactName
            Copy-Item -LiteralPath $artifact -Destination $destination -Force
            $row.artifact = $destination
            $row.sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
            $row.status = if ($SkipTests) { 'build-only' } else { 'passed' }
        } catch { $row.error = $_.Exception.Message }
        $row.seconds = [Math]::Round(([DateTimeOffset]::Now - $started).TotalSeconds,2)
        $results += [PSCustomObject]$row
        ConvertTo-Json -InputObject @($results) -Depth 8 | Set-Content -LiteralPath (Join-Path $reportDirectory 'matrix.json') -Encoding utf8
        Write-Host "$targetMc : $($row.status) ($($row.seconds)s)"
    }
} finally { Pop-Location }
if (@($results | Where-Object status -EQ 'failed').Count -gt 0) { exit 1 }
