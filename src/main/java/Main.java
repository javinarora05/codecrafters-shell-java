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

            if (input.equals("pwd")) {
                // Print the current working directory
                System.out.println(System.getProperty("user.dir"));
            } else if (input.startsWith("exit")) {
                System.exit(0);
            } else {
                System.out.println(input + ": command not found");
            }
        }
    }
}