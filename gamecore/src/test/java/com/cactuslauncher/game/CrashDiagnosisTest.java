package com.cactuslauncher.game;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashDiagnosisTest {
    @TempDir Path temp;
    private CrashDiagnosis.Result diagnose(String log) throws Exception {
        Files.createDirectories(temp.resolve("logs"));
        Files.writeString(temp.resolve("logs/cactus-launch.log"), log);
        return new CrashDiagnosis().analyze(temp, "26.3", "Vanilla");
    }

    @Test
    void graphicsFailureIsNotMistakenForMemoryFailureBecauseOfJvmOptions() throws Exception {
        var result = diagnose("Настройки запуска Java: heap=6 ГБ, флаги=-XX:+ExitOnOutOfMemoryError\n"
                + "Window$WindowInitFailed: No supported graphics backend was found.\n"
                + "Couldn't find matching GLX visual\nVulkan is not supported\n");
        assertEquals("Видеокарта не поддерживает запуск игры", result.title());
        assertTrue(result.detail().contains("aka.ms/mcdriver"));
        assertTrue(result.evidence().contains("No supported graphics backend"));
    }

    @Test
    void flagsAloneDoNotEstablishAnOutOfMemoryError() throws Exception {
        assertEquals(CrashDiagnosis.UNKNOWN_TITLE, diagnose("JVM options: -XX:+ExitOnOutOfMemoryError "
                + "-XX:+CrashOnOutOfMemoryError -XX:OnOutOfMemoryError=notify\nUnrecognised failure\n").title());
    }

    @Test
    void genuineMemoryErrorIsStillRecognizedAndEvidenceSkipsTheFlag() throws Exception {
        var result = diagnose("JVM options: -XX:+ExitOnOutOfMemoryError\n" + "padding ".repeat(30)
                + "\nException in thread main java.lang.OutOfMemoryError: Java heap space\n");
        assertEquals("Не хватило памяти Java", result.title());
        assertTrue(result.evidence().contains("java.lang.OutOfMemoryError"));
        assertFalse(result.evidence().contains("ExitOnOutOfMemoryError"));
    }
}
