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

            // Handle builtins only if there's NO pipeline in the input
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

            // If it's not a standalone builtin, treat it as an external command or pipeline
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
        // Split the command by pipe characters
        String[] pipeStages = input.split("\\|");
        List<ProcessBuilder> builders = new ArrayList<>();

        for (String stage : pipeStages) {
            stage = stage.trim();
            // Basic argument splitting (by space)
            String[] args = stage.split("\\s+");
            if (args.length == 0 || args[0].isEmpty()) continue;

            // Resolve the actual path of the executable
            String cmdPath = getCommandPath(args[0]);
            if (cmdPath == null) {
                System.out.println(input + ": command not found");
                return;
            }
            args[0] = cmdPath;

            builders.add(new ProcessBuilder(args));
        }

        if (builders.isEmpty()) return;

        try {
            // Redirect standard input/output between adjacent processes sequentially
            List<Process> processes = ProcessBuilder.startPipeline(builders);
            
            // Wait for the final process in the pipeline to finish
            Process lastProcess = processes.get(processes.size() - 1);
            
            // Inherit standard error and pump output to the shell's console
            lastProcess.getInputStream().transferTo(System.out);
            lastProcess.getErrorStream().transferTo(System.err);
            
            lastProcess.waitFor();
        } catch (Exception e) {
            System.out.println(input + ": command not found");
        }
    }
}