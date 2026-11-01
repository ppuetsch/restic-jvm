import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stand-in for the restic binary, run through the Java source launcher so it behaves the same on Linux and Windows.
 * Reads the scenario named by FAKE_RESTIC_SCENARIO, one instruction per line:
 * out TEXT | err TEXT | self FILE | child FILE | sleep | exit CODE
 */
public final class FakeRestic {
    private static final String SLEEPER_ARGUMENT = "--sleeper";

    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && arguments[0].equals(SLEEPER_ARGUMENT)) {
            Thread.sleep(Long.MAX_VALUE);
        }
        Path scenario = Path.of(System.getenv("FAKE_RESTIC_SCENARIO"));
        for (String instruction : Files.readAllLines(scenario)) {
            execute(instruction);
        }
    }

    private static void execute(String instruction) throws Exception {
        String verb = instruction.contains(" ") ? instruction.substring(0, instruction.indexOf(' ')) : instruction;
        String rest = instruction.contains(" ") ? instruction.substring(instruction.indexOf(' ') + 1) : "";
        switch (verb) {
            case "out" -> System.out.println(rest);
            case "err" -> System.err.println(rest);
            case "self" -> Files.writeString(Path.of(rest), Long.toString(ProcessHandle.current().pid()));
            case "child" -> Files.writeString(Path.of(rest), Long.toString(startSleepingChild().pid()));
            case "sleep" -> Thread.sleep(Long.MAX_VALUE);
            case "exit" -> System.exit(Integer.parseInt(rest));
            default -> throw new IllegalArgumentException("unknown instruction: " + instruction);
        }
    }

    private static Process startSleepingChild() throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(java, System.getenv("FAKE_RESTIC_SOURCE"), SLEEPER_ARGUMENT);
        return new ProcessBuilder(command).inheritIO().start();
    }
}
