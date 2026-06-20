import java.io.BufferedReader;
import java.io.File;
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

            List<String> tokenList = parseCommandLine(commandLine);
            if (tokenList.isEmpty()) {
                continue;
            }
            String[] tokens = tokenList.toArray(new String[0]);

            if (tokens[0].equals("exit")) {
                break;
            }

            // Handle 'jobs' builtin with dynamic markers (+ / - / space)
            if (tokens[0].equals("jobs")) {
                int totalJobs = backgroundJobs.size();
                for (int i = 0; i < totalJobs; i++) {
                    BackgroundJob job = backgroundJobs.get(i);
                    
                    // FIX FOR DK5: Determine the correct marker
                    String marker = " ";
                    if (i == totalJobs - 1) {
                        marker = "+"; // Most recent
                    } else if (i == totalJobs - 2) {
                        marker = "-"; // Second most recent
                    }

                    // Exact format: "[1] +  Running                 sleep 10 &"
                    // If marker is space: "[1]    Running                 sleep 10 &"
                    System.out.printf("[%d]%s  %-24s%s\n", job.id, marker, job.status, job.command);
                }
                System.out.flush();
                continue;
            }

            if (tokens[0].equals("type") && tokens.length > 1) {
                String target = tokens[1];
                if (builtins.contains(target)) {
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
                continue;
            }

            boolean isBackground = false;
            String[] execArgs = tokens;
            if (tokens[tokens.length - 1].equals("&")) {
                isBackground = true;
                execArgs = Arrays.copyOfRange(tokens, 0, tokens.length - 1);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder();
                
                String redirectFile = null;
                boolean appendMode = false;
                int redirectStream = 1; 
                int redirectIndex = -1;

                for (int i = 0; i < execArgs.length; i++) {
                    if (execArgs[i].equals(">") || execArgs[i].equals("1>")) {
                        redirectStream = 1; appendMode = false; redirectIndex = i; break;
                    } else if (execArgs[i].equals(">>") || execArgs[i].equals("1>>")) {
                        redirectStream = 1; appendMode = true; redirectIndex = i; break;
                    } else if (execArgs[i].equals("2>")) {
                        redirectStream = 2; appendMode = false; redirectIndex = i; break;
                    } else if (execArgs[i].equals("2>>")) {
                        redirectStream = 2; appendMode = true; redirectIndex = i; break;
                    }
                }

                if (redirectIndex != -1 && redirectIndex + 1 < execArgs.length) {
                    redirectFile = execArgs[redirectIndex + 1];
                    execArgs = Arrays.copyOfRange(execArgs, 0, redirectIndex);
                }

                pb.command(execArgs);

                if (isBackground) {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    Process process = pb.start();

                    int jobId = backgroundJobs.size() + 1;
                    long pid = process.pid();

                    System.out.printf("[%d] %d\n", jobId, pid);
                    System.out.flush();

                    backgroundJobs.add(new BackgroundJob(jobId, pid, commandLine, "Running"));
                } else {
                    if (redirectFile != null) {
                        File file = new File(redirectFile);
                        if (file.getParentFile() != null) {
                            file.getParentFile().mkdirs();
                        }
                        
                        ProcessBuilder.Redirect targetRedirect = appendMode ? 
                                ProcessBuilder.Redirect.appendTo(file) : 
                                ProcessBuilder.Redirect.to(file);

                        if (redirectStream == 1) {
                            pb.redirectOutput(targetRedirect);
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.redirectError(targetRedirect);
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();
                    process.waitFor();
                }
            } catch (Exception e) {
                System.out.printf("%s: command not found\n", tokens[0]);
                System.out.flush();
            }
        }
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