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

            // --- ONE SIMPLE FOR-LOOP TO PARSE EVERYTHING ---
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

            // --- COMMAND EXECUTION ---
            String command = parsedArgs.get(0);

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                // Fix: Safely print individual parts separated by ONE space 
                // while keeping the internal spaces of any single argument intact!
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i));
                    if (i < parsedArgs.size() - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            } else {
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