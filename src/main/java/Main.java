import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

            executePipeline(input);
        }
    }

    private static void executePipeline(String input) {
        String[] pipeStages = input.split("\\|");
        
        // This will hold the output of the previous stage
        InputStream currentIn = new ByteArrayInputStream(new byte[0]);

        for (int i = 0; i < pipeStages.length; i++) {
            String stage = pipeStages[i].trim();
            String[] args = stage.split("\\s+");
            if (args.length == 0 || args[0].isEmpty()) continue;

            // Strip quotes from arguments if present
            for (int j = 0; j < args.length; j++) {
                if (args[j].startsWith("\"") && args[j].endsWith("\"") && args[j].length() >= 2) {
                    args[j] = args[j].substring(1, args[j].length() - 1);
                }
            }

            String command = args[0];
            boolean isLastStage = (i == pipeStages.length - 1);
            ByteArrayOutputStream stageOut = new ByteArrayOutputStream();

            if (isBuiltin(command)) {
                executeBuiltin(command, args, currentIn, stageOut);
                if (isLastStage) {
                    System.out.print(stageOut.toString(StandardCharsets.UTF_8));
                } else {
                    currentIn = new ByteArrayInputStream(stageOut.toByteArray());
                }
            } else {
                try {
                    String cmdPath = getCommandPath(command);
                    if (cmdPath != null) {
                        args[0] = cmdPath;
                    }
                    
                    ProcessBuilder pb = new ProcessBuilder(args);
                    Process process = pb.start();

                    // Concurrently pump the input data into the process stdin
                    final InputStream fIn = currentIn;
                    OutputStream pOut = process.getOutputStream();
                    Thread inputPump = new Thread(() -> {
                        try {
                            fIn.transferTo(pOut);
                            pOut.close();
                        } catch (Exception ignored) {}
                    });
                    inputPump.start();

                    if (isLastStage) {
                        // Actively stream process stdout to system console out
                        InputStream pIn = process.getInputStream();
                        Thread outputPump = new Thread(() -> {
                            try {
                                pIn.transferTo(System.out);
                            } catch (Exception ignored) {}
                        });
                        outputPump.start();

                        process.getErrorStream().transferTo(System.err);
                        process.waitFor();
                        inputPump.join();
                        outputPump.join();
                    } else {
                        // Intermediate stages: capture output concurrently
                        InputStream pIn = process.getInputStream();
                        Thread outputPump = new Thread(() -> {
                            try {
                                pIn.transferTo(stageOut);
                            } catch (Exception ignored) {}
                        });
                        outputPump.start();

                        process.waitFor();
                        inputPump.join();
                        outputPump.join();
                        currentIn = new ByteArrayInputStream(stageOut.toByteArray());
                    }
                } catch (Exception e) {
                    System.out.println(stage + ": command not found");
                    return;
                }
            }
        }
    }

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd");
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