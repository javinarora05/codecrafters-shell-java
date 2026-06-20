import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.util.in);
        
        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            if (input.equals("exit 0")) {
                break;
            }
            
            // Advanced command parsing supporting single/double quotes and backslashes
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
                    // Inside double quotes, backslash only escapes specific characters
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
        List<String> commandArgs = new ArrayList<>();

        // Parse tokens to look for both '>' and '2>' operators
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                if (i + 1 < tokens.size()) {
                    stdoutRedirectFile = tokens.get(i + 1);
                    i++; 
                }
            } else if (tokens.get(i).equals("2>")) {
                if (i + 1 < tokens.size()) {
                    stderrRedirectFile = tokens.get(i + 1);
                    i++; 
                }
            } else {
                commandArgs.add(tokens.get(i));
            }
        }

        if (commandArgs.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandArgs);

            // Handle Standard Output Redirection (>)
            if (stdoutRedirectFile != null) {
                File outFile = new File(stdoutRedirectFile);
                if (outFile.getParentFile() != null) {
                    outFile.getParentFile().mkdirs();
                }
                pb.redirectOutput(ProcessBuilder.Redirect.to(outFile));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            // Handle Standard Error Redirection (2>)
            if (stderrRedirectFile != null) {
                File errFile = new File(stderrRedirectFile);
                if (errFile.getParentFile() != null) {
                    errFile.getParentFile().mkdirs();
                }
                pb.redirectError(ProcessBuilder.Redirect.to(errFile));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            System.out.println(commandArgs.get(0) + ": command not found");
        }
    }
}