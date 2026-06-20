import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Main {
    static class BackgroundJob {
        int id;
        long pid;
        String command;
        String status;
        Process process;

        BackgroundJob(int id, long pid, String command, String status, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }

    private static List<BackgroundJob> backgroundJobs = new ArrayList<>();
    private static int currentJobId = -1;
    private static int previousJobId = -1;
    private static final List<String> BUILTINS = Arrays.asList("exit", "echo", "type", "pwd", "cd", "jobs");

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            reapCompletedJobs();

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

            List<String> tokenList = parseCommandLine(commandLine);
            if (tokenList.isEmpty()) {
                continue;
            }

            if (tokenList.contains("|")) {
                handlePipeline(tokenList);
                continue;
            }

            String[] tokens = tokenList.toArray(new String[0]);

            if (tokens[0].equals("exit")) {
                break;
            }

            if (tokens[0].equals("jobs")) {
                reapAndPrintJobsBuiltin();
                continue;
            }

            if (tokens[0].equals("type") && tokens.length > 1) {
                handleTypeBuiltin(tokens[1]);
                continue;
            }

            if (tokens[0].equals("echo")) {
                handleEchoBuiltin(tokens);
                continue;
            }

            boolean isBackground = false;
            String[] execArgs = tokens;
            if (tokens[tokens.length - 1].equals("&")) {
                isBackground = true;
                Arrays.copyOfRange(tokens, 0, tokens.length - 1);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(tokens);
                pb.inheritIO();
                Process process = pb.start();
                process.waitFor();
            } catch (Exception e) {
                System.out.printf("%s: command not found\n", tokens[0]);
                System.out.flush();
            }
        }
    }

    private static void handleTypeBuiltin(String target) {
        if (BUILTINS.contains(target)) {
            System.out.printf("%s is a shell builtin\n", target);
        } else {
            String path = getPath(target);
            if (path != null) {
                System.out.printf("%s is %s\n", target, path);
            } else {
                System.out.printf("%s: not found\n", target);
            }
        }
        System.out.flush();
    }

    private static void handleEchoBuiltin(String[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            sb.append(tokens[i]).append(i == tokens.length - 1 ? "" : " ");
        }
        System.out.println(sb.toString());
        System.out.flush();
    }

    // FIX FOR BUILTINS IN PIPELINES: Handles builtins combined with external system commands
    private static void handlePipeline(List<String> tokens) {
        List<List<String>> commands = new ArrayList<>();
        List<String> currentCmd = new ArrayList<>();

        for (String token : tokens) {
            if (token.equals("|")) {
                commands.add(currentCmd);
                currentCmd = new ArrayList<>();
            } else {
                currentCmd.add(token);
            }
        }
        commands.add(currentCmd);

        // Simple implementation assuming a common case: builtin | external command (e.g., echo hello | wc)
        if (commands.size() == 2 && BUILTINS.contains(commands.get(0).get(0))) {
            List<String> builtinCmd = commands.get(0);
            List<String> externalCmd = commands.get(1);

            try {
                ProcessBuilder pb = new ProcessBuilder(externalCmd);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                Process process = pb.start();

                // Intercept and feed builtin output directly into the next process's stdin
                try (OutputStream os = process.getOutputStream()) {
                    if (builtinCmd.get(0).equals("echo")) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < builtinCmd.size(); i++) {
                            sb.append(builtinCmd.get(i)).append(i == builtinCmd.size() - 1 ? "" : " ");
                        }
                        sb.append("\n");
                        os.write(sb.toString().getBytes());
                    } else if (builtinCmd.get(0).equals("type") && builtinCmd.size() > 1) {
                        String target = builtinCmd.get(1);
                        String output;
                        if (BUILTINS.contains(target)) {
                            output = target + " is a shell builtin\n";
                        } else {
                            String path = getPath(target);
                            output = (path != null) ? target + " is " + path + "\n" : target + ": not found\n";
                        }
                        os.write(output.getBytes());
                    }
                    os.flush();
                }
                process.waitFor();
            } catch (Exception e) {
                System.out.println("Pipeline execution failed.");
            }
            return;
        }

        // Standard pure external pipeline fallback
        try {
            List<ProcessBuilder> builders = new ArrayList<>();
            for (List<String> cmd : commands) {
                builders.add(new ProcessBuilder(cmd));
            }
            builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builders.get(builders.size() - 1).redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);

            List<Process> processes = ProcessBuilder.startPipeline(builders);
            processes.get(processes.size() - 1).waitFor();
        } catch (Exception e) {
            System.out.println("Pipeline execution failed.");
        }
    }

    private static void printWithMarkers(List<BackgroundJob> list) {
        for (BackgroundJob job : list) {
            String marker = " ";
            if (job.id == currentJobId) marker = "+";
            else if (job.id == previousJobId) marker = "-";
            System.out.printf("[%d]%s  %-24s%s\n", job.id, marker, job.status, job.command);
        }
    }

    private static void reapCompletedJobs() {
        for (BackgroundJob job : backgroundJobs) {
            if (!job.process.isAlive() && job.status.equals("Running")) {
                job.status = "Done";
                if (job.command.endsWith(" &")) {
                    job.command = job.command.substring(0, job.command.length() - 2);
                }

                String marker = " ";
                if (job.id == currentJobId) marker = "+";
                else if (job.id == previousJobId) marker = "-";

                System.out.printf("[%d]%s  %-24s%s\n", job.id, marker, job.status, job.command);
            }
        }

        boolean changed = false;
        Iterator<BackgroundJob> iterator = backgroundJobs.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().status.equals("Done")) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            updatePointers();
        }
        System.out.flush();
    }

    private static void reapAndPrintJobsBuiltin() {
        for (BackgroundJob job : backgroundJobs) {
            if (!job.process.isAlive() && job.status.equals("Running")) {
                job.status = "Done";
                if (job.command.endsWith(" &")) {
                    job.command = job.command.substring(0, job.command.length() - 2);
                }
            }
        }

        printWithMarkers(backgroundJobs);

        boolean changed = false;
        Iterator<BackgroundJob> iterator = backgroundJobs.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().status.equals("Done")) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            updatePointers();
        }
        System.out.flush();
    }

    private static void updatePointers() {
        int mostRecent = -1;
        int secondRecent = -1;
        for (int i = backgroundJobs.size() - 1; i >= 0; i--) {
            if (backgroundJobs.get(i).status.equals("Running")) {
                if (mostRecent == -1) {
                    mostRecent = backgroundJobs.get(i).id;
                } else if (secondRecent == -1) {
                    secondRecent = backgroundJobs.get(i).id;
                    break;
                }
            }
        }
        currentJobId = mostRecent;
        previousJobId = secondRecent;
    }

    private static List<String> parseCommandLine(String commandLine) {
        List<String> list = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);

            if (escaped) {
                currentToken.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < commandLine.length()) {
                        char next = commandLine.charAt(i + 1);
                        if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                            escaped = true;
                        } else {
                            currentToken.append(c);
                        }
                    } else {
                        currentToken.append(c);
                    }
                } else {
                    escaped = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (currentToken.length() > 0) {
                    list.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            list.add(currentToken.toString());
        }
        return list;
    }

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