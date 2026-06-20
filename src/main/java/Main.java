import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ShellCommandExecutor {

    public static void executeCommand(List<String> tokens) {
        String errorRedirectFile = null;
        List<String> commandArgs = new ArrayList<>();

        // Parse tokens to look for '2>'
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

            // Inherit standard output so it still prints to the terminal
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            if (errorRedirectFile != null) {
                File errFile = new File(errorRedirectFile);
                
                // Ensure parent directories exist if necessary
                if (errFile.getParentFile() != null) {
                    errFile.getParentFile().mkdirs();
                }
                
                // Redirect stderr to the file (this overwrites the file, matching '>')
                pb.redirectError(ProcessBuilder.Redirect.to(errFile));
            } else {
                // Default behavior: inherit stderr to terminal if not redirected
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process process = pb.start();
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            System.err.println(commandArgs.get(0) +": command not found");
        }
    }
}