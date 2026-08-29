$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$targets = @(
  (Join-Path $projectRoot 'packages\mobile-client\dist'),
  (Join-Path $projectRoot 'android\app\build'),
  (Join-Path $projectRoot 'artifacts\apk'),
  (Join-Path $projectRoot 'artifacts\gateway')
)
foreach ($target in $targets) {
  $resolved = [System.IO.Path]::GetFullPath($target)
  if (-not $resolved.StartsWith($projectRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw "Refusing path outside project: $resolved" }
  if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
