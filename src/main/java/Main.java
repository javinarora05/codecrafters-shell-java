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

            if (tokens[0].equals("exit")) {
                break;
            }

            // 1. Handle 'jobs' builtin with the corrected exact spacing
            if (tokens[0].equals("jobs")) {
                for (BackgroundJob job : backgroundJobs) {
                    // %-24s left-aligns "Running" and pads it to exactly 24 characters total
                    // Format matches exactly: "[1]+  Running                 sleep 100 &"
                    System.out.printf("[%d]+  %-24s%s\n", job.id, job.status, job.command);
                }
                System.out.flush();
                continue;
            }

            boolean isBackground = false;
            String[] execArgs = tokens;
            if (tokens[tokens.length - 1].equals("&")) {
                isBackground = true;
                execArgs = Arrays.copyOfRange(tokens, 0, tokens.length - 1);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(execArgs);
                
                if (isBackground) {
                    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    Process process = pb.start();

                    int jobId = backgroundJobs.size() + 1;
                    long pid = process.pid();

                    // Print process info immediately upon launch: "[1] 104"
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
}