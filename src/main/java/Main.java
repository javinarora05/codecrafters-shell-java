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
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            if (input.equals("exit 0")) {
                break;
            }
            
            // Basic tokenization (splits by spaces)
            // Note: If you have advanced quoting logic from previous stages, 
            // use your existing tokenization mechanism here instead.
            String[] rawTokens = input.split("\\s+");
            List<String> tokens = new ArrayList<>();
            for (String t : rawTokens) {
                tokens.add(t);
            }
            
            // Handle built-ins like 'echo', 'type', 'pwd', 'cd' here if needed,
            // or pass directly to external command handler:
            executeCommand(tokens);
        }
    }

    private static void executeCommand(List<String> tokens) {
        String errorRedirectFile = null;
        List<String> commandArgs = new ArrayList<>();

        // Parse tokens to look for the '2>' operator
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals("2>")) {
                if (i + 1 < tokens.size()) {
                    errorRedirectFile = tokens.get(i + 1);
                    i++; // Skip the filename token
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

            // Keep standard output writing to the terminal
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            if (errorRedirectFile != null) {
                File errFile = new File(errorRedirectFile);
                
                // Create parent directories if they don't exist (e.g., /tmp/quz/)
                if (errFile.getParentFile() != null) {
                    errFile.getParentFile().mkdirs();
                }
                
                // Redirect standard error stream to the specified file
                pb.redirectError(ProcessBuilder.Redirect.to(errFile));
            } else {
                // If '2>' isn't provided, standard error prints to the terminal normally
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            System.out.println(commandArgs.get(0) + ": command not found");
        }
    }
}