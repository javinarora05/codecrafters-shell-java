import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    static class BackgroundJob {
        int id;
        long pid;
        String command;
        String status;

        BackgroundJob(int id, long pid, String command, String status) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
        }
    }

    public static void main(String[] args) throws Exception {
        List<BackgroundJob> backgroundJobs = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // List of builtins to check against for the 'type' command
        List<String> builtins = Arrays.asList("exit", "echo", "type", "pwd", "cd", "jobs");

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String commandLine = reader.readLine();
            if (commandLine == null) {
                break;
            }

            commandLine = commandLine.trim();
            if (commandLine.isEmpty()) {
                continue;
            }

            String[] tokens = commandLine.split("\\s+");

            // 1. Handle 'exit' builtin
            if (tokens[0].equals("exit")) {
                break;
            }

            // 2. Handle 'jobs' builtin
            if (tokens[0].equals("jobs")) {
                for (BackgroundJob job : backgroundJobs) {
                    System.out.printf("[%d]+  %-24s%s\n", job.id, job.status, job.command);
                }
                System.out.flush();
                continue;
            }

            // 3. Handle 'type' builtin (FIX FOR AF3)
            if (tokens[0].equals("type") && tokens.length > 1) {
                String target = tokens[1];
                if (builtins.contains(target)) {
                    System.out.printf("%s is a shell builtin\n", target);
                } else {
                    // Check PATH or print not found (reusing standard logic)
                    String path = getPath(target);
                    if (path != null) {
                        System.out.printf("%s is %s\n", target, path);
                    } else {
                        System.out.printf("%s: not found\n", target);
                    }
                }
                System.out.flush();
                continue;
            }

            // 4. Check for background execution indicator '&'
            boolean isBackground = false;
            String[] execArgs = tokens;
            if (tokens[tokens.length - 1].equals("&")) {
                isBackground = true;
                execArgs = Arrays.copyOfRange(tokens, 0, tokens.length - 1);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(execArgs);
                
                if (isBackground) {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    Process process = pb.start();

                    int jobId = backgroundJobs.size() + 1;
                    long pid = process.pid();

                    System.out.printf("[%d] %d\n", jobId, pid);
                    System.out.flush();

                    backgroundJobs.add(new BackgroundJob(
                        jobId,
                        pid,
                        commandLine,
                        "Running"
                    ));
                } else {
                    pb.inheritIO();
                    Process process = pb.start();
                    process.waitFor();
                }
            } catch (Exception e) {
                System.out.printf("%s: command not found\n", tokens[0]);
                System.out.flush();
            }
        }
    }

    // Helper method to resolve command PATH from earlier stages
    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] directories = pathEnv.split(":");
        for (String dir : directories) {
            java.io.File file = new java.io.File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}