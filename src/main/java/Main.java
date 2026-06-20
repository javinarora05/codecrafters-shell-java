import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    // Class to track background jobs
    static class BackgroundJob {
        int id;
        String command;
        String status;

        BackgroundJob(int id, String command, String status) {
            this.id = id;
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

            // Split command line into arguments by whitespace
            String[] tokens = commandLine.split("\\s+");

            // 1. Handle 'exit' builtin
            if (tokens[0].equals("exit")) {
                break;
            }

            // 2. Handle 'jobs' builtin (JD6 Requirement)
            if (tokens[0].equals("jobs")) {
                for (BackgroundJob job : backgroundJobs) {
                    // %-24s pads the status "Running" with spaces to the right to be exactly 24 characters
                    System.out.printf("[ %d ] +  %-24s%s\n", job.id, job.status, job.command);
                }
                System.out.flush();
                continue;
            }

            // 3. Check for background execution indicator '&'
            boolean isBackground = false;
            String[] execArgs = tokens;
            if (tokens[tokens.length - 1].equals("&")) {
                isBackground = true;
                // Strip the trailing '&' so ProcessBuilder gets a valid command execution array
                execArgs = Arrays.copyOfRange(tokens, 0, tokens.length - 1);
            }

            // 4. Execute external commands
            try {
                ProcessBuilder pb = new ProcessBuilder(execArgs);
                
                if (isBackground) {
                    // Redirect streams to avoid blocking and run asynchronously
                    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                    pb.start(); // Start without calling .waitFor()

                    // Save the job metadata using the original string line
                    backgroundJobs.add(new BackgroundJob(
                        backgroundJobs.size() + 1,
                        commandLine,
                        "Running"
                    ));
                } else {
                    // Foreground job execution blocks normally
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