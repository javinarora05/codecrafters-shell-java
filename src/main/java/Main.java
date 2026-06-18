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
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            // Simplified single-quote argument parser
            List<String> parsedArgs = new ArrayList<>();
            StringBuilder currentArg = new StringBuilder();
            boolean insideQuotes = false;
            boolean hasArg = false;

            for (char c : input.toCharArray()) {
                if (c == '\'') {
                    insideQuotes = !insideQuotes;
                    hasArg = true; // Handles empty quotes '' as an argument context
                } else if (c == ' ' && !insideQuotes) {
                    if (hasArg || currentArg.length() > 0) {
                        parsedArgs.add(currentArg.toString());
                        currentArg.setLength(0);
                        hasArg = false;
                    }
                } else {
                    currentArg.append(c);
                    hasArg = true;
                }
            }
            if (hasArg || currentArg.length() > 0) {
                parsedArgs.add(currentArg.toString());
            }

            if (parsedArgs.isEmpty()) continue;
            String command = parsedArgs.get(0);

            // Command processing execution
            if (command.equals("exit")) {
                System.exit(0);
            } else if (command.equals("echo")) {
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i) + (i < parsedArgs.size() - 1 ? " " : ""));
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
                    System.out.println(path != null ? targetCmd + " is " + path : targetCmd + ": not found");
                }
            } else {
                String fullPath = getPath(command);
                if (fullPath != null) {
                    try {
                        Process process = new ProcessBuilder(parsedArgs).redirectErrorStream(true).start();
                        try (Scanner processScanner = new Scanner(process.getInputStream())) {
                            while (processScanner.hasNextLine()) System.out.println(processScanner.nextLine());
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

    private static String getPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String directory : pathEnv.split(File.pathSeparator)) {
            Path path = Paths.get(directory, command);
            if (Files.isExecutable(path)) return path.toString();
        }
        return null;
    }
}