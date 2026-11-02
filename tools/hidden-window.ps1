# Keeps the windows of a dev client hidden while it runs, for automated tests
# on a machine somebody is working at.
#
#   ./gradlew runClient -Phidden      (marks the JVM with -Dfn.hidden=true)
#   pwsh tools/hidden-window.ps1      (hides every window of marked JVMs)
#
# Started before the client, it catches the early loading window as well.
# It ends once no marked client has run for a while. Minecraft keeps
# rendering into its own framebuffer, so screenshots still work.

param(
    [int]$IdleSeconds = 120
)

Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class HiddenWindow {
    public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc proc, IntPtr lParam);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int cmd);
    public static int HideAll(uint[] pids) {
        int hidden = 0;
        EnumWindows((h, l) => {
            uint pid;
            GetWindowThreadProcessId(h, out pid);
            if (Array.IndexOf(pids, pid) >= 0 && IsWindowVisible(h)) {
                ShowWindow(h, 0);
                hidden++;
            }
            return true;
        }, IntPtr.Zero);
        return hidden;
    }
}
"@

function IsMarked([string]$commandLine) {
    if ($commandLine -match '-Dfn\.hidden=true') {
        return $true
    }
    foreach ($m in [regex]::Matches($commandLine, '@("?)([^"\s]+RunVmArgs\.txt)')) {
        $file = $m.Groups[2].Value -replace '\\\\', '\'
        if ((Test-Path $file) -and (Select-String -Path $file -Pattern 'fn\.hidden=true' -Quiet)) {
            return $true
        }
    }
    return $false
}

$lastSeen = Get-Date
$lastQuery = [datetime]::MinValue
$pids = @()
while (((Get-Date) - $lastSeen).TotalSeconds -lt $IdleSeconds) {
    # The process list is slow to ask; the windows are cheap to check.
    if (((Get-Date) - $lastQuery).TotalMilliseconds -gt 1000) {
        $lastQuery = Get-Date
        # ModDevGradle passes the JVM arguments through a file, so the
        # marker is looked for there and not on the command line.
        $found = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
            Where-Object { IsMarked $_.CommandLine } |
            ForEach-Object { [uint32]$_.ProcessId })
        foreach ($p in $found) {
            if ($pids -notcontains $p) {
                Write-Output "hidden-window: watching client $p"
            }
        }
        $pids = $found
    }
    if ($pids.Count -gt 0) {
        $lastSeen = Get-Date
        [void][HiddenWindow]::HideAll([uint32[]]$pids)
        Start-Sleep -Milliseconds 30
    } else {
        Start-Sleep -Milliseconds 100
    }
}
Write-Output "hidden-window: no marked client for $IdleSeconds s, done"
