import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            List<String> tokens = parseArguments(input);
            if (tokens.isEmpty()) {
                continue;
            }
            
            executeCommand(tokens);
        }
    }

    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                currentToken.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < input.length() && (input.charAt(i + 1) == '$' || input.charAt(i + 1) == '`' || 
                        input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\' || input.charAt(i + 1) == '\n')) {
                        escaped = true;
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
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
    }

    private static void executeCommand(List<String> tokens) {
        String stdoutRedirectFile = null;
        String stderrRedirectFile = null;
        boolean appendStdout = false;
        List<String> commandArgs = new ArrayList<>();

        // Strict exact-match evaluation to prevent operators from clashing
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals(">>") || token.equals("1>>")) {
                if (i + 1 < tokens.size()) {
                    stdoutRedirectFile = tokens.get(i + 1);
                    appendStdout = true;
                    i++; 
                }
            } else if (token.equals(">") || token.equals("1>")) {
                if (i + 1 < tokens.size()) {
                    stdoutRedirectFile = tokens.get(i + 1);
                    appendStdout = false;
                    i++; 
                }
            } else if (token.equals("2>")) {
                if (i + 1 < tokens.size()) {
                    stderrRedirectFile = tokens.get(i + 1);
                    i++; 
                }
            } else {
                commandArgs.add(token);
            }
        }

        if (commandArgs.isEmpty()) {
            return;
        }

        // --- PRE-CREATE REDIRECTION FILES ---
        if (stdoutRedirectFile != null) {
            try {
                File outFile = new File(stdoutRedirectFile);
                if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                if (!outFile.exists()) outFile.createNewFile();
            } catch (IOException e) {}
        }
        if (stderrRedirectFile != null) {
            try {
                File errFile = new File(stderrRedirectFile);
                if (errFile.getParentFile() != null) errFile.getParentFile().mkdirs();
                if (!errFile.exists()) errFile.createNewFile();
            } catch (IOException e) {}
        }

        String baseCommand = commandArgs.get(0);

        // --- HANDLE BUILT-IN COMMANDS ---
        if (baseCommand.equals("exit")) {
            int exitCode = 0;
            if (commandArgs.size() > 1) {
                try {
                    exitCode = Integer.parseInt(commandArgs.get(1));
                } catch (NumberFormatException e) {
                    exitCode = 0;
                }
            }
            System.exit(exitCode);
        }

        if (baseCommand.equals("type")) {
            if (commandArgs.size() > 1) {
                String target = commandArgs.get(1);
                handleTypeCommand(target, stdoutRedirectFile, appendStdout);
            }
            return;
        }
        
        if (baseCommand.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < commandArgs.size(); i++) {
                sb.append(commandArgs.get(i));
                if (i < commandArgs.size() - 1) sb.append(" ");
            }
            String output = sb.toString();
            
            if (stdoutRedirectFile != null) {
                try {
                    File outFile = new File(stdoutRedirectFile);
                    if (appendStdout) {
                        java.nio.file.Files.writeString(outFile.toPath(), output + "\n", java.nio.file.StandardOpenOption.APPEND);
                    } else {
                        java.nio.file.Files.writeString(outFile.toPath(), output + "\n");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println(output);
            }
            return;
        }

        // --- EXTERNAL COMMANDS ---
        try {
            ProcessBuilder pb = new ProcessBuilder(commandArgs);

            // Handle Standard Output (Overwrite vs Append)
            if (stdoutRedirectFile != null) {
                File outFile = new File(stdoutRedirectFile);
                if (appendStdout) {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.to(outFile));
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            // Handle Standard Error
            if (stderrRedirectFile != null) {
                pb.redirectError(ProcessBuilder.Redirect.to(new File(stderrRedirectFile)));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            System.out.println(baseCommand + ": command not found");
        }
    }

    private static void handleTypeCommand(String target, String stdoutRedirectFile, boolean appendStdout) {
        String result = "";
        
        if (target.equals("echo") || target.equals("type") || target.equals("exit") || target.equals("pwd")) {
            result = target + " is a shell builtin";
        } else {
            String pathEnv = System.getenv("PATH");
            boolean found = false;
            if (pathEnv != null) {
                String[] paths = pathEnv.split(":");
                for (String path : paths) {
                    File file = new File(path, target);
                    if (file.exists() && file.canExecute()) {
                        result = target + " is " + file.getAbsolutePath();
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                result = target + ": not found";
            }
        }

        if (stdoutRedirectFile != null) {
            try {
                File outFile = new File(stdoutRedirectFile);
                if (appendStdout) {
                    java.nio.file.Files.writeString(outFile.toPath(), result + "\n", java.nio.file.StandardOpenOption.APPEND);
                } else {
                    java.nio.file.Files.writeString(outFile.toPath(), result + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println(result);
        }
    }
}