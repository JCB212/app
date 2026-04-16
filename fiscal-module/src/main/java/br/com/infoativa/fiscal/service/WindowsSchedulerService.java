package br.com.infoativa.fiscal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * Configura o Agendador de Tarefas do Windows (Task Scheduler)
 * para abrir o programa automaticamente no dia configurado.
 *
 * Usa o comando SCHTASKS nativo do Windows.
 * A tarefa roda todo mes no dia especificado as 08:00.
 */
public class WindowsSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WindowsSchedulerService.class);
    private static final String TASK_NAME = "InfoAtivaFiscalModule";

    /**
     * Cria/atualiza tarefa no Task Scheduler do Windows.
     * @param diaDoMes dia do mes (1-28) para executar
     * @return true se criou com sucesso
     */
    public static boolean createScheduledTask(int diaDoMes) {
        if (!isWindows()) {
            log.warn("Agendamento automatico so funciona no Windows");
            return false;
        }

        try {
            // Descobre o caminho do JAR em execucao
            String jarPath = getJarPath();
            String javaPath = System.getProperty("java.home") + "\\bin\\javaw.exe";

            // Remove tarefa antiga se existir
            removeScheduledTask();

            // Cria nova tarefa
            // SCHTASKS /CREATE /SC MONTHLY /D dia /TN nome /TR "comando" /ST hora /F
            String command = String.format(
                "SCHTASKS /CREATE /SC MONTHLY /D %d /TN \"%s\" /TR \"\\\"%s\\\" -jar \\\"%s\\\"\" /ST 08:00 /F",
                diaDoMes, TASK_NAME, javaPath, jarPath
            );

            log.info("Criando tarefa agendada: {}", command);
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Tarefa agendada criada: dia {} de cada mes as 08:00", diaDoMes);
                return true;
            } else {
                String error = readStream(process);
                log.error("Erro ao criar tarefa: {}", error);
                return false;
            }
        } catch (Exception e) {
            log.error("Erro ao configurar agendamento: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove a tarefa agendada
     */
    public static boolean removeScheduledTask() {
        if (!isWindows()) return false;
        try {
            String command = String.format("SCHTASKS /DELETE /TN \"%s\" /F", TASK_NAME);
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            process.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifica se a tarefa existe no scheduler
     */
    public static boolean isTaskScheduled() {
        if (!isWindows()) return false;
        try {
            String command = String.format("SCHTASKS /QUERY /TN \"%s\"", TASK_NAME);
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retorna info da tarefa agendada
     */
    public static String getTaskInfo() {
        if (!isWindows()) return "Agendamento disponivel apenas no Windows";
        try {
            String command = String.format("SCHTASKS /QUERY /TN \"%s\" /FO LIST", TASK_NAME);
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", command});
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return readStream(process);
            }
            return "Nenhuma tarefa agendada";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    private static String getJarPath() {
        try {
            // Tenta pegar o caminho do JAR em execucao
            String path = WindowsSchedulerService.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            // Remove / inicial em Windows (ex: /C:/path -> C:/path)
            if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            return path;
        } catch (Exception e) {
            return Path.of(System.getProperty("user.dir"), "FiscalModule.jar").toString();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String readStream(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
