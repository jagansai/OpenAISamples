param(
	[string]$JavaExe = 'D:\Program Files\Java\jdk-24.0.2\bin\java.exe',
	[string]$Jar = 'target\\02-rag-loader-0.0.1-SNAPSHOT.jar',
	[int]$Port = 8080
)

$ErrorActionPreference = 'Stop'

Write-Output "Using Java: $JavaExe"
Write-Output "Jar: $Jar"

if (!(Test-Path $Jar)) {
	Write-Error "Jar not found: $Jar"
	exit 1
}

if (!(Test-Path 'logs')) { New-Item -ItemType Directory -Path 'logs' | Out-Null }

# Stop any java processes started with this jar
$procs = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match [regex]::Escape($Jar) }
if ($procs) {
	Write-Output "Stopping existing processes: $($procs.Id -join ', ')"
	$procs | ForEach-Object { Stop-Process -Id $_.Id -Force }
	Start-Sleep -Seconds 1
}


wsl -e sudo sh -c 'echo 3 > /proc/sys/vm/drop_caches'
Write-Output "Starting application..."
$proc = Start-Process -FilePath $JavaExe -ArgumentList '-Xmx2048M -Xms512M  -jar', $Jar -RedirectStandardOutput 'logs\\out.log' -RedirectStandardError 'logs\\err.log' -PassThru
# Write-Output "Started PID $($proc.Id)"
# Start-Sleep -Seconds 2

# Write-Output '--- last 50 lines of logs\\err.log ---'
# if (Test-Path 'logs\\err.log') { Get-Content 'logs\\err.log' -Tail 50 } else { Write-Output '<no err.log yet>' }

# Write-Output '--- last 50 lines of logs\\out.log ---'
# if (Test-Path 'logs\\out.log') { Get-Content 'logs\\out.log' -Tail 50 } else { Write-Output '<no out.log yet>' }

#Write-Output "You can query: http://localhost:$Port/docs/list"