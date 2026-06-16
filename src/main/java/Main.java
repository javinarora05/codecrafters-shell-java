import java.io.File;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            
            if (input.equals("exit")) {
                break;
            } else if (input.startsWith("echo ")) {
                String arguments = input.substring(5);
                System.out.println(arguments);
            } else if (input.startsWith("type ")) {
                String command = input.substring(5);
                
                if (command.equals("echo") || command.equals("exit") || command.equals("type")) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    System.out.println(command + ": not found");
                }
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
    private static String getPathToExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return null;
        }

        // Split PATH using the OS-agnostic separator (':' on Linux/macOS, ';' on Windows)
        String[] directories = pathEnv.split(File.pathSeparator);

        for (String directory : directories) {
            File file = new File(directory, command);
            // Check if the file exists and is executable
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

}
