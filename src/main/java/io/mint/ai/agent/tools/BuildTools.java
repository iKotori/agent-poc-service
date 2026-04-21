package io.mint.ai.agent.tools;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
public class BuildTools {

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_LOG_CHARS = 40_000;

    public String runBackendBuild(String backendRoot) {
        return runBackendBuild(backendRoot, line -> {}, () -> false);
    }

    public String runBackendBuild(String backendRoot, Consumer<String> onLine, BooleanSupplier isCancelled) {
        Path root = validateRoot(backendRoot);

        List<String> command = resolveBackendCommand(root);
        return runCommand(root, command, "backend-build", onLine, isCancelled);
    }

    public String runFrontendBuild(String frontendRoot) {
        return runFrontendBuild(frontendRoot, line -> {}, () -> false);
    }

    public String runFrontendBuild(String frontendRoot, Consumer<String> onLine, BooleanSupplier isCancelled) {
        Path root = validateRoot(frontendRoot);

        List<String> command = resolveFrontendCommand(root);
        return runCommand(root, command, "frontend-build", onLine, isCancelled);
    }

    private Path validateRoot(String rootPath) {
        Path root = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Build root does not exist: " + rootPath);
        }
        return root;
    }

    private List<String> resolveBackendCommand(Path root) {
        boolean windows = isWindows();

        Path mvnw = root.resolve(windows ? "mvnw.cmd" : "mvnw");
        if (Files.exists(mvnw)) {
            List<String> cmd = new ArrayList<>();
            cmd.add(mvnw.toAbsolutePath().toString());
            cmd.add("-q");
            cmd.add("-DskipTests");
            cmd.add("compile");
            return cmd;
        }

        return List.of("mvn", "-q", "-DskipTests", "compile");
    }

    private List<String> resolveFrontendCommand(Path root) {
        String npm = isWindows() ? "npm.cmd" : "npm";

        Path packageJson = root.resolve("package.json");
        if (!Files.exists(packageJson)) {
            throw new IllegalArgumentException("package.json not found under: " + root);
        }

        // POC 里默认直接 build；如需先 install，可在外层流程提前准备依赖
        return List.of(npm, "run", "build");
    }

    private String runCommand(Path workingDir,
                              List<String> command,
                              String tag,
                              Consumer<String> onLine,
                              BooleanSupplier isCancelled) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        StringBuilder log = new StringBuilder();
        log.append("[").append(tag).append("] workingDir=").append(workingDir).append("\n");
        log.append("[").append(tag).append("] command=").append(String.join(" ", command)).append("\n\n");

        Process process = null;
        try {
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    onLine.accept(line);
                    if (log.length() < MAX_LOG_CHARS) {
                        log.append(line).append('\n');
                    }

                    if (isCancelled.getAsBoolean()) {
                        process.destroyForcibly();
                        log.append("\nProcess cancelled by user.");
                        return log.toString();
                    }
                }
            }

            boolean finished = process.waitFor(BUILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.append("\nProcess timed out after ").append(BUILD_TIMEOUT.toSeconds()).append(" seconds.");
                return log.toString();
            }

            int exit = process.exitValue();
            log.append("\nExit code: ").append(exit);
            return log.toString();
        }
        catch (IOException e) {
            return log + "\nFailed to start process: " + e.getMessage();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return log + "\nBuild interrupted: " + e.getMessage();
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
