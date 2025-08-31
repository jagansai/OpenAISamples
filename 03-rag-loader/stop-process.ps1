# convert string to int
$raw = netstat -aon | findstr ":8080" | Select-Object -First 1
if (-not $raw) { Write-Error "No process found for :8080"; exit 1 }
$processid = ($raw -split '\s+' | Select-Object -Last 1).Trim()
$processid = [int]$processid

# display the process convert string to int.
Get-Process -Id $processid | Format-List
# and confirm from the user if that is the one to be killed.
$confirmation = Read-Host "Do you want to kill this process? (y/n)"
if ($confirmation -eq 'y') {
    Stop-Process -Id $processid -Force
    Write-Output "Stopped process $processid"
} else {
    Write-Output "Process $processid not stopped"
}
