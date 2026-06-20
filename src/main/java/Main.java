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
    
    private static class BackgroundJob {
        int jobId;
        Process process;
        String commandClean;

        BackgroundJob(int jobId, Process process, String commandClean) {
            this.jobId = jobId;
            this.process = process;
            this.commandClean = commandClean;
        }
    }

    private static final List<BackgroundJob> activeJobs = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
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
                System.out.println("[" + job.jobId + "]+  Done                 " + job.commandClean);
                iterator.remove();
            }
        }
    }

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
        
        if (input.endsWith("&")) {
            isBackground = true;
            input = input.substring(0, input.length() - 1).trim();
        }
        
        String cleanCommand = input; 

        if (cleanCommand.equals("jobs")) {
            int size = activeJobs.size();
            List<BackgroundJob> finishedDuringJobsCmd = new ArrayList<>();
            
            for (int i = 0; i < size; i++) {
                BackgroundJob job = activeJobs.get(i);
                
                String marker = " ";
                if (i == size - 1) {
                    marker = "+";
                } else if (i == size - 2) {
                    marker = "-";
                }
                
                if (!job.process.isAlive()) {
                    System.out.printf("[%d]%s  Done                 %s\n", job.jobId, marker, job.commandClean);
                    finishedDuringJobsCmd.add(job);
                } else {
                    System.out.printf("[%d]%s  Running                 %s &\n", job.jobId, marker, job.commandClean);
                }
            }
            activeJobs.removeAll(finishedDuringJobsCmd);
            return;
        }

        if (!cleanCommand.contains("|")) {
            String[] args = cleanCommand.split("\\s+");
            if (args.length > 0 && isBuiltin(args[0])) {
                stripQuotes(args);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                executeBuiltin(args[0], args, new ByteArrayInputStream(new byte[0]), out);
                System.out.print(out.toString(StandardCharsets.UTF_8));
                return;
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cleanCommand);
            pb.environment().put("PATH", System.getenv("PATH"));
            
            if (isBackground) {
                // Inherit stdout to allow background processes to print into the terminal dynamically
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                
                Process process = pb.start();
                
                int jobId = getNextAvailableJobId();
                System.out.println("[" + jobId + "] " + process.pid());
                
                activeJobs.add(new BackgroundJob(jobId, process, cleanCommand));
            } else {
                Process process = pb.start();
                process.getInputStream().transferTo(System.out);
                process.getErrorStream().transferTo(System.err);
                process.waitFor();
            }
        } catch (Exception e) {
            System.out.println(cleanCommand + ": command not found");
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
