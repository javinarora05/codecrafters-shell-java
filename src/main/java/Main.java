import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while(true) {
            System.out.print("$ ");

            if(!sc.hasNextLine()) break;

            String input = sc.nextLine().trim();
            if(input.isEmpty()) continue;


            ArrayList<String> parsedArgs = new ArrayList<>();
            StringBuilder currentArg = new StringBuilder();

            boolean isDoubleQuotes = false;
            boolean isSingleQuotes = false;
            boolean hasArg = false;


            for(int i =0; i < input.length(); i++) {
                char c = input.charAt(i);

                if(isDoubleQuotes) {
                    if (c == '"') {
                        isDoubleQuotes = false;
                    } else {
                        currentArg.append(c);
                    }
                } else { 
                    if (c == '"') {
                        isDoubleQuotes = true;
                        hasArg = true;
                    } else if (c == '\'') {
                       isSingleQuotes = true;
                       hasArg = true;
                    } else if (c == ' ' || c == '\t') {
                        if (hasArg) {
                            parsedArgs.add(currentArg.toString());
                            currentArg.setLength(0); // Clear the buffer
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
                // Collect echo arguments (everything after index 0)
                List<String> echoArgs = parsedArgs.subList(1, parsedArgs.size());
                System.out.println(String.join(" ", echoArgs));
            } else {
                // Handle external commands like cat
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
        sc.close();
    }
}