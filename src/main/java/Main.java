import java.io.File;
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

            // --- 1. PARSE EVERYTHING USING A FOR-LOOP ---
            List<String> parsedArgs = new ArrayList<>();
            StringBuilder currentArg = new StringBuilder();
            
            boolean inDoubleQuotes = false;
            boolean inSingleQuotes = false;
            boolean hasArg = false; 

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (inDoubleQuotes) {
                    if (c == '"') {
                        inDoubleQuotes = false; 
                    } else {
                        currentArg.append(c);
                    }
                } else if (inSingleQuotes) {
                    if (c == '\'') {
                        inSingleQuotes = false; 
                    } else {
                        currentArg.append(c);
                    }
                } else {
                    if (c == '"') {
                        inDoubleQuotes = true;
                        hasArg = true; 
                    } else if (c == '\'') {
                        inSingleQuotes = true;
                        hasArg = true;
                    } else if (c == ' ' || c == '\t') {
                        if (hasArg) {
                            parsedArgs.add(currentArg.toString());
                            currentArg.setLength(0); 
                            hasArg = false;
                        }
                    } else {
                        currentArg.append(c);
                        hasArg = true;
                    }
                }
            }

            if (hasArg) {
                parsedArgs.add(currentArg.toString());
            }

            if (parsedArgs.isEmpty()) continue;

            // --- 2. COMMAND EXECUTION ---
            String command = parsedArgs.get(0);

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i));
                    if (i < parsedArgs.size() - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            } else if (command.equals("type")) {
                // Handle the 'type' command requirement for stage MG5
                if (parsedArgs.size() < 2) {
                    continue;
                }
                String target = parsedArgs.get(1);

                if (target.equals("echo") || target.equals("exit") || target.equals("type")) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    // Search for the executable in environmental PATH directories
                    String pathEnv = System.getenv("PATH");
                    String[] directories = pathEnv.split(":");
                    boolean found = false;

                    for (String dir : directories) {
                        File file = new File(dir, target);
                        if (file.exists() && file.canExecute()) {
                            System.out.println(target + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(target + ": not found");
                    }
                }
            } else {
                // Handle external commands (cat, custom_exe, etc.)
                try {
                    ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                    pb.inheritIO();
                    Process process = pb.start();
                    process.waitFor();
                } catch (Exception e) {
                    System.out.println(command + ": command not found");
                }
            }
        }
        scanner.close();
    }
}