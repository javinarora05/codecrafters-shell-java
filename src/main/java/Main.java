import java.io.File;
import java.io.InputStream;
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

            String[] inputParts = input.split(" ");
            String command = inputParts[0];

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                if (input.length() > 5) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
            } else if (command.equals("type")) {
                if (inputParts.length < 2) {
                    continue;
                }
                String targetCommand = inputParts[1];
                
                if (targetCommand.equals("echo") || targetCommand.equals("exit") || targetCommand.equals("type")) {
                    System.out.println(targetCommand + " is a shell builtin");
                } else {
                    String pathToExecutable = getPathToExecutable(targetCommand);
                    if (pathToExecutable != null) {
                        System.out.println(targetCommand + " is " + pathToExecutable);
                    } else {
                        System.out.println(targetCommand + ": not found");
                    }
                }
            } else {
                String pathToExecutable = getPathToExecutable(command);
                
                if (pathToExecutable != null) {
                    List<String> commandWithArgs = new ArrayList<>();
                    
                    // FIX: Pass the raw command name (e.g., "custom_exe_4225") instead of the absolute path
                    commandWithArgs.add(command); 
                    
                    for (int i = 1; i < inputParts.length; i++) {
                        commandWithArgs.add(inputParts[i]);
                    }

                    try {
                        ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
                        
                        // FIX: We must point the ProcessBuilder working environment or executable location 
                        // to the absolute path directory so the system knows where to find the command.
                        processBuilder.command().set(0, pathToExecutable);
                        
                        processBuilder.redirectErrorStream(true);
                        Process process = processBuilder.start();

                        InputStream inputStream = process.getInputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            System.out.write(buffer, 0, bytesRead);
                        }
                        
                        process.waitFor();
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }

    private static String getPathToExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        String[] directories = pathEnv.split(File.pathSeparator);
        for (String directory : directories) {
            File file = new File(directory, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}