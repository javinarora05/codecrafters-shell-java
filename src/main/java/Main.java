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

    // Custom robust argument parser that handles single quotes, double quotes, and backslashes correctly
    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    currentArg.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                } else if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                        currentArg.append(next);
                        i++;
                    } else {
                        currentArg.append(c);
                    }
                } else {
                    currentArg.append(c);
                }
            } else {
                if (Character.isWhitespace(c)) {
                    if (currentArg.length() > 0) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                    }
                } else if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (c == '\\' && i + 1 < input.length()) {
                    currentArg.append(input.charAt(i + 1));
                    i++;
                } else {
                    currentArg.append(c);
                }
            }
        }

        if (currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
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

        // Parse arguments accurately through our custom tokenizer
        List<String> parsedArgs = parseArguments(cleanCommand);
        if (parsedArgs.isEmpty()) return;

        String command = parsedArgs.get(0);

        // Always run builtins natively using our un-quoted clean argument parser lists
        if (isBuiltin(command) && !cleanCommand.contains("|") && !cleanCommand.contains(">") && !cleanCommand.contains("<")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String[] argsArray = parsedArgs.toArray(new String[0]);
            executeBuiltin(command, argsArray, new ByteArrayInputStream(new byte[0]), out);
            System.out.print(out.toString(StandardCharsets.UTF_8));
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cleanCommand);
            pb.environment().put("PATH", System.getenv("PATH"));
            
            if (isBackground) {
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
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd") || cmd.equals("jobs");
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