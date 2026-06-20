import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        // Configure terminal to raw mode so we get character-by-character input immediately
        setTerminalRawMode();

        Reader reader = new InputStreamReader(System.in);
        StringBuilder currentLine = new StringBuilder();

        while (true) {
            System.out.print("$ " + currentLine.toString());
            System.out.flush();

            while (true) {
                int readChar = reader.read();
                if (readChar == -1) {
                    System.exit(0);
                }

                char c = (char) readChar;

                // Handle Tab Key
                if (c == '\t') {
                    String currentText = currentLine.toString();
                    
                    if (!currentText.contains(" ") && !currentText.isEmpty()) {
                        if ("echo".startsWith(currentText)) {
                            currentLine.setLength(0);
                            currentLine.append("echo ");
                        } else if ("exit".startsWith(currentText)) {
                            currentLine.setLength(0);
                            currentLine.append("exit ");
                        } else if ("type".startsWith(currentText)) {
                            currentLine.setLength(0);
                            currentLine.append("type ");
                        } else if ("jobs".startsWith(currentText)) {
                            currentLine.setLength(0);
                            currentLine.append("jobs ");
                        } else {
                            System.out.print("\u0007");
                            System.out.flush();
                            continue;
                        }
                    } else {
                        System.out.print("\u0007");
                        System.out.flush();
                        continue;
                    }
                    
                    System.out.print("\r\u001B[K$ " + currentLine.toString());
                    System.out.flush();
                    
                // Handle Enter Key
                } else if (c == '\n' || c == '\r') {
                    System.out.print("\r\n");
                    System.out.flush();
                    break;
                    
                // Handle Backspace Key (ASCII 127 or 8)
                } else if (readChar == 127 || readChar == 8) {
                    if (currentLine.length() > 0) {
                        currentLine.deleteCharAt(currentLine.length() - 1);
                        System.out.print("\r\u001B[K$ " + currentLine.toString());
                        System.out.flush();
                    }
                    
                // Regular visible character input
                } else {
                    currentLine.append(c);
                    System.out.print(c);
                    System.out.flush();
                }
            }

            String input = currentLine.toString().trim();
            currentLine.setLength(0);

            if (input.isEmpty()) {
                continue;
            }

            List<String> tokens = parseArguments(input);
            if (tokens.isEmpty()) {
                continue;
            }

            executeCommand(tokens);
        }
    }

    private static void setTerminalRawMode() {
        try {
            String[] cmd = {"/bin/sh", "-c", "stty -echo -icanon min 1 < /dev/tty"};
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {}
    }

    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                currentToken.append(c);
                escaped = false;
            } else if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < input.length() && (input.charAt(i + 1) == '$' || input.charAt(i + 1) == '`' || 
                        input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\' || input.charAt(i + 1) == '\n')) {
                        escaped = true;
                    } else {
                        currentToken.append(c);
                    }
                } else {
                    escaped = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
    }

    private static void executeCommand(List<String> tokens) {
        String stdoutRedirectFile = null;
        String stderrRedirectFile = null;
        boolean appendStdout = false;
        boolean appendStderr = false;
        boolean isBackgroundJob = false;
        List<String> commandArgs = new ArrayList<>();

        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
            isBackgroundJob = true;
            tokens.remove(tokens.size() - 1);
        }

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals(">")) {
                if (i + 1 < tokens.size()) {
                    stdoutRedirectFile = tokens.get(i + 1);
                    appendStdout = false;
                    i++;
                } else {
                    System.err.println("Syntax error: expected file after '>'");
                    return;
                }
            } else if (token.equals(">>")) {
                if (i + 1 < tokens.size()) {
                    stdoutRedirectFile = tokens.get(i + 1);
                    appendStdout = true;
                    i++;
                } else {
                    System.err.println("Syntax error: expected file after '>>'");
                    return;
                }
            } else if (token.equals("2>")) {
                if (i + 1 < tokens.size()) {
                    stderrRedirectFile = tokens.get(i + 1);
                    appendStderr = false;
                    i++;
                } else {
                    System.err.println("Syntax error: expected file after '2>'");
                    return;
                }
            } else if (token.equals("2>>")) {
                if (i + 1 < tokens.size()) {
                    stderrRedirectFile = tokens.get(i + 1);
                    appendStderr = true;
                    i++;
                } else {
                    System.err.println("Syntax error: expected file after '2>>'");
                    return;
                }
            } else {
                commandArgs.add(token);
            }
        }
    }
}