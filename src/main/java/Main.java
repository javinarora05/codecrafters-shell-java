import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            // Handle standalone builtins (only if no pipeline is present)
            if (!input.contains("|")) {
                if (input.startsWith("exit ")) {
                    try {
                        int status = Integer.parseInt(input.substring(5).trim());
                        System.exit(status);
                    } catch (NumberFormatException e) {
                        System.exit(0);
                    }
                } else if (input.equals("exit")) {
                    System.exit(0);
                } else if (input.startsWith("echo ")) {
                    System.out.println(input.substring(5));
                    continue;
                } else if (input.equals("echo")) {
                    System.out.println();
                    continue;
                } else if (input.equals("pwd")) {
                    System.out.println(System.getProperty("user.dir"));
                    continue;
                } else if (input.startsWith("type ")) {
                    handleTypeCommand(input.substring(5).trim());
                    continue;
                }
            }

            // Route pipelines and external commands
            handlePipelineOrExternal(input);
        }
    }

    private static void handleTypeCommand(String cmd) {
        if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd")) {
            System.out.println(cmd + " is a shell builtin");
            return;
        }
        String path = getCommandPath(cmd);
        if (path != null) {
            System.out.println(cmd + " is " + path);
        } else {
            System.out.println(cmd + ": not found");
        }
    }

    private static String getCommandPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(":");
            for (String p : paths) {
                File file = new File(p, cmd);
                if (file.exists() && file.isFile() && file.canExecute()) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void handlePipelineOrExternal(String input) {
        String[] pipeStages = input.split("\\|");
        List<ProcessBuilder> builders = new ArrayList<>();

        for (String stage : pipeStages) {
            stage = stage.trim();
            
            // Handle quotes or arguments spacing gracefully
            String[] args = stage.split("\\s+");
            if (args.length == 0 || args[0].isEmpty()) continue;

            // Strip enclosing quotes from arguments if present (e.g., "f-73" -> f-73)
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("\"") && args[i].endsWith("\"") && args[i].length() >= 2) {
                    args[i] = args[i].substring(1, args[i].length() - 1);
                }
            }

            // Fallback to the command string directly if path lookup fails (delegates to OS shell execution environment)
            String cmdPath = getCommandPath(args[0]);
            if (cmdPath != null) {
                args[0] = cmdPath;
            }

            builders.add(new ProcessBuilder(args));
        }

        if (builders.isEmpty()) return;

        try {
            // Chains stdout -> stdin automatically for all processes in the list
            List<Process> processes = ProcessBuilder.startPipeline(builders);
            
            // Get the final process output
            Process lastProcess = processes.get(processes.size() - 1);
            
            lastProcess.getInputStream().transferTo(System.out);
            lastProcess.getErrorStream().transferTo(System.err);
            
            // Wait for all processes to gracefully finish execution
            for (Process p : processes) {
                p.waitFor();
            }
        } catch (Exception e) {
            System.out.println(input + ": command not found");
        }
    }
}
