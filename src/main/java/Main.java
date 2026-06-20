import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
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

            if (input.equals("exit 0")) {
                break;
            }

            List<String> tokens = new ArrayList<>(Arrays.asList(input.split("\\s+")));

            int redirectIndex = -1;
            boolean append = false;

            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);

                if (token.equals(">") || token.equals("1>")) {
                    redirectIndex = i;
                    append = false;
                    break;
                }

                if (token.equals(">>") || token.equals("1>>")) {
                    redirectIndex = i;
                    append = true;
                    break;
                }
            }

            String outputFile = null;
            List<String> command;

            if (redirectIndex != -1) {
                outputFile = tokens.get(redirectIndex + 1);
                command = tokens.subList(0, redirectIndex);
            } else {
                command = tokens;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();

                String output = new String(process.getInputStream().readAllBytes());

                process.waitFor();

                if (outputFile != null) {
                    try (FileWriter writer = new FileWriter(outputFile, append)) {
                        writer.write(output);
                    }
                } else {
                    System.out.print(output);
                }
            } catch (Exception e) {
                System.out.println(command.get(0) + ": command not found");
            }
        }

        scanner.close();
    }
}