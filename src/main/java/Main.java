import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            executePipeline(input);
        }
    }

    private static void executePipeline(String input) {
        // If it's a simple builtin execution without a pipe, run it natively
        if (!input.contains("|")) {
            String[] args = input.split("\\s+");
            if (args.length > 0 && isBuiltin(args[0])) {
                stripQuotes(args);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                executeBuiltin(args[0], args, new ByteArrayInputStream(new byte[0]), out);
                System.out.print(out.toString(StandardCharsets.UTF_8));
                return;
            }
        }

        // For complex pipelines containing continuous external streams (like tail -f | head)
        // or builtins mixed with pipes, we delegate external parts cleanly to /bin/sh
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", input);
            
            // Sync environment paths so system utilities (tail, head, grep) are fully visible
            pb.environment().put("PATH", System.getenv("PATH"));
            
            Process process = pb.start();

            // Actively stream the standard output and error back to the console
            process.getInputStream().transferTo(System.out);
            process.getErrorStream().transferTo(System.err);

            process.waitFor();
        } catch (Exception e) {
            System.out.println(input + ": command not found");
        }
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd");
    }

    private static void stripQuotes(String[] args) {
        for (int j = 0; j < args.length; j++) {
            if (args[j].startsWith("\"") && args[j].endsWith("\"") && args[j].length() >= 2) {
                args[j] = args[j].substring(1, args[j].length() - 1);
            }
        }
    }

    private static void executeBuiltin(String cmd, String[] args, InputStream in, ByteArrayOutputStream out) {
        StringBuilder outputBuffer = new StringBuilder();

        if (cmd.equals("exit")) {
            if (args.length > 1) {
                try {
                    System.exit(Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    System.exit(0);
                }
            }
            System.exit(0);
        } else if (cmd.equals("echo")) {
            for (int i = 1; i < args.length; i++) {
                outputBuffer.append(args[i]).append(i == args.length - 1 ? "" : " ");
            }
            outputBuffer.append("\n");
        } else if (cmd.equals("pwd")) {
            outputBuffer.append(System.getProperty("user.dir")).append("\n");
        } else if (cmd.equals("type")) {
            if (args.length > 1) {
                String targetCmd = args[1];
                if (isBuiltin(targetCmd)) {
                    outputBuffer.append(targetCmd).append(" is a shell builtin\n");
                } else {
                    String path = getCommandPath(targetCmd);
                    if (path != null) {
                        outputBuffer.append(targetCmd).append(" is ").append(path).append("\n");
                    } else {
                        outputBuffer.append(targetCmd).append(": not found\n");
                    }
                }
            }
        }

        try {
            out.write(outputBuffer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
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
}