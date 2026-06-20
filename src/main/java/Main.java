import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        String[] pipeStages = input.split("\\|");
        
        // This will track the stream pipeline connections
        InputStream currentIn = new ByteArrayInputStream(new byte[0]);
        List<Thread> activeThreads = new ArrayList<>();
        List<Process> activeProcesses = new ArrayList<>();

        for (int i = 0; i < pipeStages.length; i++) {
            String stage = pipeStages[i].trim();
            String[] args = stage.split("\\s+");
            if (args.length == 0 || args[0].isEmpty()) continue;

            // Strip quotes from arguments
            for (int j = 0; j < args.length; j++) {
                if (args[j].startsWith("\"") && args[j].endsWith("\"") && args[j].length() >= 2) {
                    args[j] = args[j].substring(1, args[j].length() - 1);
                }
            }

            String command = args[0];
            boolean isLastStage = (i == pipeStages.length - 1);

            if (isBuiltin(command)) {
                ByteArrayOutputStream stageOut = new ByteArrayOutputStream();
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
                    activeProcesses.add(process);

                    // 1. Pump the input data from the previous stage into this process's stdin asynchronously
                    final InputStream fIn = currentIn;
                    OutputStream pOut = process.getOutputStream();
                    Thread inputPump = new Thread(() -> {
                        try {
                            fIn.transferTo(pOut);
                        } catch (Exception ignored) {}
                        try {
                            pOut.close();
                        } catch (Exception ignored) {}
                    });
                    inputPump.start();
                    activeThreads.add(inputPump);

                    // 2. Route the output stream
                    if (isLastStage) {
                        // The last stage pumps directly to console stdout concurrently
                        InputStream pIn = process.getInputStream();
                        Thread outputPump = new Thread(() -> {
                            try {
                                pIn.transferTo(System.out);
                            } catch (Exception ignored) {}
                        });
                        outputPump.start();
                        activeThreads.add(outputPump);
                        
                        // Handle standard error
                        process.getErrorStream().transferTo(System.err);
                    } else {
                        // Intermediate stage: Pipe directly to a memory stream structure 
                        // but DO NOT wait for it to finish. Create a pipe buffer.
                        java.io.PipedOutputStream pipedOut = new java.io.PipedOutputStream();
                        java.io.PipedInputStream pipedIn = new java.io.PipedInputStream(pipedOut);
                        
                        InputStream pIn = process.getInputStream();
                        Thread outputPump = new Thread(() -> {
                            try {
                                pIn.transferTo(pipedOut);
                            } catch (Exception ignored) {}
                            try {
                                pipedOut.close();
                            } catch (Exception ignored) {}
                        });
                        outputPump.start();
                        activeThreads.add(outputPump);

                        // Pass this piped reader stream to the next command stage
                        currentIn = pipedIn;
                    }
                } catch (Exception e) {
                    System.out.println(stage + ": command not found");
                    // Cleanup any running processes to prevent hanging terminal loops
                    for (Process p : activeProcesses) p.destroyForcibly();
                    return;
                }
            }
        }

        // Wait for execution completion loops safely
        try {
            // Wait for the final execution process to wrap up cleanly
            if (!activeProcesses.isEmpty()) {
                Process lastProcess = activeProcesses.get(activeProcesses.size() - 1);
                lastProcess.waitFor();
            }
            
            // Join all active stream management threads
            for (Thread t : activeThreads) {
                t.join(500); // 500ms max timeout constraint per process thread
            }
        } catch (Exception ignored) {}

        // Forcibly shut down any infinite stream processes (like tail -f) that are still lingering
        for (Process p : activeProcesses) {
            if (p.isAlive()) {
                p.destroyForcibly();
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