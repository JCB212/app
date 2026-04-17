package br.com.infoativa.fiscal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

/**
 * Serviço de registro no startup do Windows.
 * Registra o aplicativo para iniciar automaticamente com o Windows
 * via chave de registro HKCU\Software\Microsoft\Windows\CurrentVersion\Run
 */
public class StartupService {

    private static final Logger log = LoggerFactory.getLogger(StartupService.class);
    private static final String APP_NAME = "ModuloFiscalInfoAtiva";
    private static final String REG_KEY = 
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private StartupService() {}

    /**
     * Registra o aplicativo no startup do Windows.
     * Usa reg.exe nativo (disponível em todos os Windows).
     */
    public static void registerStartup() {
        String exePath = getExePath();
        if (exePath == null) {
            log.warn("Nao foi possivel determinar o caminho do executavel");
            return;
        }

        try {
            String value = "\"" + exePath + "\" --tray";
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "add", REG_KEY,
                "/v", APP_NAME,
                "/t", "REG_SZ",
                "/d", value,
                "/f"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                log.info("Registro no startup do Windows realizado com sucesso: {}", value);
            } else {
                log.warn("Falha ao registrar no startup. Codigo de saida: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("Erro ao registrar no startup do Windows", e);
        }
    }

    /**
     * Remove o aplicativo do startup do Windows.
     */
    public static void unregisterStartup() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "delete", REG_KEY,
                "/v", APP_NAME,
                "/f"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
            log.info("Registro de startup removido");
        } catch (Exception e) {
            log.error("Erro ao remover startup do Windows", e);
        }
    }

    /**
     * Verifica se já está registrado no startup.
     */
    public static boolean isRegistered() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "query", REG_KEY, "/v", APP_NAME
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            int exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getExePath() {
        try {
            // Detectar se está rodando como .exe (Launch4j) ou .jar
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command != null && command.toLowerCase().endsWith(".exe")) {
                return command;
            }
            // Caso seja .jar, usar javaw
            Path jarPath = Path.of(
                StartupService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()
            );
            if (jarPath.toString().endsWith(".jar")) {
                String javaHome = System.getProperty("java.home");
                String javaw = javaHome + "\\bin\\javaw.exe";
                return javaw + " -jar \"" + jarPath + "\"";
            }
        } catch (Exception e) {
            log.debug("Erro ao determinar caminho do executavel: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Cria script BAT de inicialização como alternativa ao registro.
     */
    public static void createStartupBat() {
        try {
            Path startupDir = Path.of(System.getProperty("user.home"),
                "AppData", "Roaming", "Microsoft", "Windows",
                "Start Menu", "Programs", "Startup");

            if (!Files.exists(startupDir)) return;

            Path batFile = startupDir.resolve("ModuloFiscal.bat");
            String exePath = getExePath();
            if (exePath == null) return;

            String content = "@echo off\nstart \"\" " + exePath + " --tray\n";
            Files.writeString(batFile, content);
            log.info("BAT de startup criado: {}", batFile);
        } catch (Exception e) {
            log.warn("Nao foi possivel criar BAT de startup: {}", e.getMessage());
        }
    }
}
