import java.io.File;
import java.io.InputStream;
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
                    // Create the full arguments array where argument 0 is the clean command name
                    String[] cmdArray = new String[inputParts.length];
                    cmdArray[0] = command; // e.g., "custom_exe_5395"
                    for (int i = 1; i < inputParts.length; i++) {
                        cmdArray[i] = inputParts[i];
                    }

                    try {
                        // Use Runtime.exec with the absolute path, but passing our custom clean cmdArray
                        Process process = Runtime.getRuntime().exec(cmdArray, null, null);

                        // Safely pipe the process output stream back to stdout
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