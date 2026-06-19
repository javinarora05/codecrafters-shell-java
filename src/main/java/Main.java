import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final File[] PATH_DIRS;
    static {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] split = pathEnv.split(":");
            PATH_DIRS = new File[split.length];
            for (int i = 0; i < split.length; i++) {
                PATH_DIRS[i] = new File(split[i]);
            }
        } else {
            PATH_DIRS = new File[0];
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        StringBuilder argBuilder = new StringBuilder(64);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            List<String> parsedArgs = new ArrayList<>(8);
            argBuilder.setLength(0);
            
            boolean inDoubleQuotes = false;
            boolean inSingleQuotes = false;
            boolean hasArg = false;

            // --- OPTIMIZED SINGLE-PASS PARSER ---
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (inDoubleQuotes) {
                    if (c == '\\') {
                        if (i + 1 < input.length()) {
                            char next = input.charAt(i + 1);
                            if (next == '"' || next == '\\') {
                                argBuilder.append(next);
                                i++;
                            } else {
                                argBuilder.append(c);
                            }
                        } else {
                            argBuilder.append(c);
                        }
                    } else if (c == '"') {
                        inDoubleQuotes = false;
                    } else {
                        argBuilder.append(c);
                    }
                } else if (inSingleQuotes) {
                    if (c == '\'') {
                        inSingleQuotes = false;
                    } else {
                        argBuilder.append(c);
                    }
                } else {
                    if (c == '\\') {
                        if (i + 1 < input.length()) {
                            argBuilder.append(input.charAt(i + 1));
                            hasArg = true;
                            i++; 
                        }
                    } else if (c == '"') {
                        inDoubleQuotes = true;
                        hasArg = true;
                    } else if (c == '\'') {
                        inSingleQuotes = true;
                        hasArg = true;
                    } else if (c == ' ' || c == '\t') {
                        if (hasArg) {
                            parsedArgs.add(argBuilder.toString());
                            argBuilder.setLength(0);
                            hasArg = false;
                        }
                    } else {
                        argBuilder.append(c);
                        hasArg = true;
                    }
                }
            }

            if (hasArg) {
                parsedArgs.add(argBuilder.toString());
            }

            if (parsedArgs.isEmpty()) continue;

            // --- REDIRECTION DETECTION AND SETUP ---
            String redirectFile = null;
            // Scan backward to find redirection tokens
            for (int i = 0; i < parsedArgs.size(); i++) {
                String arg = parsedArgs.get(i);
                if ((arg.equals(">") || arg.equals("1>")) && i + 1 < parsedArgs.size()) {
                    redirectFile = parsedArgs.get(i + 1);
                    // Truncate the list to exclude the redirection parts from command arguments
                    parsedArgs = parsedArgs.subList(0, i);
                    break;
                }
            }

            if (parsedArgs.isEmpty()) continue;
            String command = parsedArgs.get(0);

            // Setup file redirection output stream if required
            FileOutputStream fos = null;
            java.io.PrintStream originalOut = System.out;
            try {
                if (redirectFile != null) {
                    File file = new File(redirectFile);
                    // Ensure parent directories exist if applicable
                    if (file.getParentFile() != null) {
                        file.getParentFile().mkdirs();
                    }
                    fos = new FileOutputStream(file);
                    System.setOut(new java.io.PrintStream(fos));
                }

                // --- COMMAND EXECUTION ---
                if (command.equals("exit")) {
                    break;
                } else if (command.equals("echo")) {
                    int size = parsedArgs.size();
                    for (int i = 1; i < size; i++) {
                        System.out.print(parsedArgs.get(i));
                        if (i < size - 1) {
                            System.out.print(" ");
                        }
                    }
                    System.out.println();
                } else if (command.equals("type")) {
                    if (parsedArgs.size() < 2) continue;
                    String target = parsedArgs.get(1);

                    if (target.equals("echo") || target.equals("exit") || target.equals("type")) {
                        System.out.println(target + " is a shell builtin");
                    } else {
                        boolean found = false;
                        for (File dir : PATH_DIRS) {
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
                    try {
                        ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                        // Redirect standard output to file if requested, keep standard error on console
                        if (redirectFile != null) {
                            pb.redirectOutput(new File(redirectFile));
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.inheritIO();
                        }
                        pb.start().waitFor();
                    } catch (Exception e) {
                        System.out.println(command + ": command not found");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Restore standard output back to console safely
                if (redirectFile != null) {
                    System.setOut(originalOut);
                    try {
                        if (fos != null) fos.close();
                    } catch (Exception ignored) {}
                }
            }
        }
        scanner.close();
    }
}