# Schreibt jede Sekunde mit, welche Chromium-Prozesse laufen.
#
# Aufruf:  pwsh -File tools/procwatch.ps1 -Seconds 180 -Out lauf.csv
#
# Warum von aussen und nicht aus dem Spiel heraus: Ein Prozess, der auf sein
# eigenes Aufraeumen wartet, ist ein schlechter Zeuge. Chromium raeumt seine
# Helfer asynchron ab, teils erst Sekunden nach dem Schliessen — wer im selben
# Takt zaehlt, in dem er schliesst, sieht einen Zwischenzustand und nennt ihn
# ein Leck.

param(
    [int]$Seconds = 180,
    [string]$Out = "procwatch.csv"
)

"zeit;browser;renderer;gpu;utility;summe_mb" | Out-File -FilePath $Out -Encoding utf8

$ende = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $ende) {
    $rollen = @{ browser = 0; renderer = 0; 'gpu-process' = 0; utility = 0 }
    $summe = 0
    foreach ($p in Get-CimInstance Win32_Process -Filter "Name='jcef_helper.exe'") {
        $typ = if ($p.CommandLine -match '--type=([a-z-]+)') { $Matches[1] } else { 'browser' }
        if ($rollen.ContainsKey($typ)) { $rollen[$typ]++ } else { $rollen[$typ] = 1 }
        $proc = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
        if ($proc) { $summe += $proc.WorkingSet64 }
    }
    $zeile = "{0};{1};{2};{3};{4};{5:N0}" -f (Get-Date -Format "HH:mm:ss"),
        $rollen['browser'], $rollen['renderer'], $rollen['gpu-process'],
        $rollen['utility'], ($summe / 1MB)
    $zeile | Out-File -FilePath $Out -Append -Encoding utf8
    Start-Sleep -Seconds 1
}
