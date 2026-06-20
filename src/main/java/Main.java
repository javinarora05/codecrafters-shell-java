import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Main {
    
    // Track background jobs
    private static class BackgroundJob {
        int jobId;
        Process process;
        String command;

        BackgroundJob(int jobId, Process process, String command) {
            this.jobId = jobId;
            this.process = process;
            this.command = command;
        }
    }

    private static final List<BackgroundJob> activeJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Check and report on any background jobs that finished before showing the prompt
            checkCompletedJobs();

            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            executePipeline(input);
        }
    }

    private static void checkCompletedJobs() {
        Iterator<BackgroundJob> iterator = activeJobs.iterator();
        while (iterator.hasNext()) {
            BackgroundJob job = iterator.next();
            if (!job.process.isAlive()) {
                System.out.println("[" + job.jobId + "]+  Done                 " + job.command);
                iterator.remove();
            }
        }
    }

    // Dynamically calculate the smallest available Job ID starting from 1
    private static int getNextAvailableJobId() {
        int candidate = 1;
        while (true) {
            boolean matches = false;
            for (BackgroundJob job : activeJobs) {
                if (job.jobId == candidate) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return candidate;
            }
            candidate++;
        }
    }

    private static void executePipeline(String input) {
        boolean isBackground = false;
        String originalCommand = input; 
        
        if (input.endsWith("&")) {
            isBackground = true;
            input = input.substring(0, input.length() - 1).trim();
            originalCommand = input; 
        }

        // Handle 'jobs' builtin natively if requested
        if (input.equals("jobs")) {
            for (BackgroundJob job : activeJobs) {
                System.out.println("[" + job.jobId + "]+  Running              " + job.command + " &");
            }
            return;
        }

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

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", input);
            pb.environment().put("PATH", System.getenv("PATH"));
            
            Process process = pb.start();

            if (isBackground) {
                // Find and allocate the recycled lowest job ID
                int jobId = getNextAvailableJobId();
                System.out.println("[" + jobId + "] " + process.pid());
                
                activeJobs.add(new BackgroundJob(jobId, process, originalCommand));
                
                process.getInputStream().close();
                process.getErrorStream().close();
            } else {
                process.getInputStream().transferTo(System.out);
                process.getErrorStream().transferTo(System.err);
                process.waitFor();
            }
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