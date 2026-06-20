import java.io.File;
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

            // Handle exit builtin
            if (input.startsWith("exit ")) {
                try {
                    int status = Integer.parseInt(input.substring(5).trim());
                    System.exit(status);
                } catch (NumberFormatException e) {
                    System.exit(0);
                }
            } else if (input.equals("exit")) {
                System.exit(0);
            }

            // Handle echo builtin
            else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else if (input.equals("echo")) {
                System.out.println();
            }

            // Handle pwd builtin
            else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            }

            // Handle type builtin
            else if (input.startsWith("type ")) {
                String cmd = input.substring(5).trim();
                
                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type") || cmd.equals("pwd")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    boolean found = false;
                    if (pathEnv != null) {
                        String[] paths = pathEnv.split(":");
                        for (String p : paths) {
                            File file = new File(p, cmd);
                            if (file.exists() && file.isFile() && file.canExecute()) {
                                System.out.println(cmd + " is " + file.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        System.out.println(cmd + ": not found");
                    }
                }
            } 
            
            // Handle external commands or missing commands
            else {
                System.out.println(input + ": command not found");
            }
        }
    }
}