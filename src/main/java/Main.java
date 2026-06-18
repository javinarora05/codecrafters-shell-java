import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
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

            // Parse the command line string into separate arguments handling single quotes
            List<String> parsedArgs = parseArguments(input);
            if (parsedArgs.isEmpty()) {
                continue;
            }

            String command = parsedArgs.get(0);

            // Handle built-in commands
            if (command.equals("exit")) {
                if (parsedArgs.size() > 1 && parsedArgs.get(1).equals("0")) {
                    System.exit(0);
                }
            } else if (command.equals("echo")) {
                // Print all arguments separated by a single space
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i));
                    if (i < parsedArgs.size() - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("type")) {
                if (parsedArgs.size() < 2) {
                    System.out.println("type: missing operand");
                    continue;
                }
                String targetCmd = parsedArgs.get(1);
                if (targetCmd.equals("echo") || targetCmd.equals("exit") || targetCmd.equals("type") || targetCmd.equals("pwd")) {
                    System.out.println(targetCmd + " is a shell builtin");
                } else {
                    String path = getPath(targetCmd);
                    if (path != null) {
                        System.out.println(targetCmd + " is " + path);
                    } else {
                        System.out.println(targetCmd + ": not found");
                    }
                }
            } else {
                // Handle external executables (e.g., cat)
                String fullPath = getPath(command);
                if (fullPath != null) {
                    try {
                        // Pass the entire parsed arguments list directly to ProcessBuilder
                        ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                        pb.redirectErrorStream(true);
                        Process process = pb.start();
                        
                        // Read and print the executable's output
                        try (Scanner processScanner = new Scanner(process.getInputStream())) {
                            while (processScanner.hasNextLine()) {
                                System.out.println(processScanner.nextLine());
                            }
                        }
                        process.waitFor();
                    } catch (IOException | InterruptedException e) {
                        System.out.println(command + ": execution failed");
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    /**
     * Parses a command string into a list of arguments, respecting single quotes.
     */
    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean insideSingleQuotes = false;
        boolean inArgument = false; // Ensures empty quotes '' are captured if necessary or trigger context

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (insideSingleQuotes) {
                if (c == '\'') {
                    insideSingleQuotes = false; // Exit quote block
                } else {
                    currentArg.append(c); // Everything inside is literal
                }
            } else {
                if (c == '\'') {
                    insideSingleQuotes = true;
                    inArgument = true; // Mark that we're assembling an argument block
                } else if (Character.isWhitespace(c)) {
                    if (inArgument) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                        inArgument = false;
                    }
                } else {
                    currentArg.append(c);
                    inArgument = true;
                }
            }
        }

        // Flush any remaining argument at the end of the string
        if (inArgument) {
            args.add(currentArg.toString());
        }

        return args;
    }

    /**
     * Helper to locate an executable within system PATH environment variables.
     */
    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] directories = pathEnv.split(File.pathSeparator);
        for (String directory : directories) {
            Path path = Paths.get(directory, command);
            if (Files.isExecutable(path)) {
                return path.toString();
            }
        }
        return null;
    }
}