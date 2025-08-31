<#
Fix SOR log: insert missing separator between tag 44 (Price) and tag 17 (ExecID) when they were merged (e.g. "44=150.017=").
Usage:
  pwsh .\tools\fix_sor_log.ps1 -LogPath 'D:\code\java_code\SOR\logs\sor.log'
This script will create a timestamped backup and write the repaired file in place.
#>
param(
    [Parameter(Mandatory=$false)]
    [string]$LogPath = "D:\code\java_code\SOR\logs\sor.log"
)
if (-not (Test-Path $LogPath)) {
    Write-Error "Log file not found: $LogPath"
    exit 1
}
$timestamp = (Get-Date).ToString('yyyyMMddHHmmss')
$backup = "$LogPath.bak.$timestamp"
Copy-Item -Path $LogPath -Destination $backup -Force
Write-Output "Backup created: $backup"

# Read raw text and repair occurrences where 44=<number> is immediately followed by 17= without a separator
# Regex explanation: find '44=<digits[.digits]>' that is directly followed by '17=' and insert a ';' between them
$text = Get-Content -Path $LogPath -Raw -Encoding UTF8
$pattern = '(\b44=\d+(?:\.\d+)?)(?=17=)'
$fixed = [regex]::Replace($text, $pattern, '$1;')

# Count fixes
$origCount = ([regex]::Matches($text, $pattern)).Count
$fixedCount = ([regex]::Matches($fixed, $pattern)).Count

if ($origCount -eq 0) {
    Write-Output "No merged 44->17 occurrences found. No changes made."
} else {
    Set-Content -Path $LogPath -Value $fixed -Encoding UTF8
    Write-Output "Inserted separators for $origCount occurrence(s)."
}

Write-Output "Done. If you want to keep the repaired copy elsewhere, the backup is at: $backup"
