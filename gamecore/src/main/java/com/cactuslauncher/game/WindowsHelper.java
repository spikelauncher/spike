package com.cactuslauncher.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

final class WindowsHelper implements AutoCloseable {
    private static final String SCRIPT = """
            $ErrorActionPreference = 'Stop'
            Add-Type -TypeDefinition @'
            using System; using System.Diagnostics; using System.Runtime.InteropServices;
            public static class SpikeWin {
              [DllImport("user32.dll")] static extern bool IsIconic(IntPtr h);
              [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
              [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint p);
              [DllImport("kernel32.dll")] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);
              [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
              [DllImport("kernel32.dll")] static extern bool SetProcessInformation(IntPtr h, int cls, ref Throttle info, int size);
              [StructLayout(LayoutKind.Sequential)] public struct Throttle { public uint Version; public uint ControlMask; public uint StateMask; }
              public static string State(int pid) {
                uint fp; GetWindowThreadProcessId(GetForegroundWindow(), out fp);
                bool iconic = false;
                try { var p = Process.GetProcessById(pid); if (p.MainWindowHandle != IntPtr.Zero) iconic = IsIconic(p.MainWindowHandle); } catch {}
                return (iconic ? "1" : "0") + " " + (fp == (uint)pid ? "1" : "0");
              }
              public static string Priority(int pid, string cls) {
                try {
                  var p = Process.GetProcessById(pid); string old = p.PriorityClass.ToString();
                  if (cls.Length > 0) p.PriorityClass = (ProcessPriorityClass)Enum.Parse(typeof(ProcessPriorityClass), cls);
                  return old;
                } catch { return "-"; }
              }
              public static string Eco(int pid, bool on) {
                IntPtr h = OpenProcess(0x0200, false, (uint)pid);
                if (h == IntPtr.Zero) return "0";
                try {
                  var t = new Throttle(); t.Version = 1; t.ControlMask = on ? 1u : 0u; t.StateMask = on ? 1u : 0u;
                  return SetProcessInformation(h, 4, ref t, Marshal.SizeOf(t)) ? "1" : "0";
                } finally { CloseHandle(h); }
              }
            }
            '@
            $ErrorActionPreference = 'Continue'
            [Console]::Out.WriteLine('ready'); [Console]::Out.Flush()
            while ($true) {
              $line = [Console]::In.ReadLine(); if ($line -eq $null) { break }
              $a = $line.Split(' ')
              $r = '-'
              try {
                switch ($a[0]) {
                  'state' { $r = [SpikeWin]::State([int]$a[1]) }
                  'prio' { $c = ''; if ($a.Length -gt 2) { $c = $a[2] }; $r = [SpikeWin]::Priority([int]$a[1], $c) }
                  'eco' { $r = [SpikeWin]::Eco([int]$a[1], $a[2] -eq '1') }
                  'gpu' {
                    $q = "Name like 'pid_" + [int]$a[1] + "_%engtype_3D'"
                    $s = (Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine -Filter $q -ErrorAction Stop | Measure-Object UtilizationPercentage -Sum).Sum
                    if ($s -eq $null) { $r = '-1' } else { $r = [string][int]$s }
                  }
                }
              } catch { $r = '-' }
              [Console]::Out.WriteLine($r); [Console]::Out.Flush()
            }
            """;

    private final Process process;
    private final Writer input;
    private final BufferedReader output;
    private boolean broken;

    private WindowsHelper(Process process) {
        this.process = process;
        this.input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII);
        this.output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII));
    }

    static WindowsHelper start() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return null;
        Process process = null;
        try {
            String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encoded).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            var helper = new WindowsHelper(process);
            String first = helper.output.readLine();
            if (!"ready".equals(first)) { process.destroyForcibly(); return null; }
            return helper;
        } catch (IOException | RuntimeException e) {
            if (process != null) process.destroyForcibly();
            return null;
        }
    }

    synchronized String call(String command) {
        if (broken) return null;
        try {
            input.write(command);
            input.write('\n');
            input.flush();
            String line = output.readLine();
            if (line == null) { broken = true; return null; }
            return line.trim();
        } catch (IOException e) {
            broken = true;
            return null;
        }
    }

    boolean[] windowState(long pid) {
        String answer = call("state " + pid);
        if (answer == null || !answer.matches("[01] [01]")) return null;
        return new boolean[] { answer.charAt(0) == '1', answer.charAt(2) == '1' };
    }

    String priority(long pid, String priority) {
        String answer = call("prio " + pid + (priority.isEmpty() ? "" : " " + priority));
        return answer == null || answer.equals("-") || !answer.matches("[A-Za-z]+") ? null : answer;
    }

    void efficiencyMode(long pid, boolean on) {
        call("eco " + pid + (on ? " 1" : " 0"));
    }

    int gpuPercent(long pid) {
        String answer = call("gpu " + pid);
        try { return answer == null ? -1 : Integer.parseInt(answer); }
        catch (NumberFormatException e) { return -1; }
    }

    @Override
    public synchronized void close() {
        broken = true;
        try { input.close(); } catch (IOException ignored) { }
        process.destroy();
    }
}
