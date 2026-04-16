package io.mint.ai.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ProjectReadTools {

    private static final int MAX_FILE_READ_CHARS = 50_000;
    private static final int MAX_SNIPPET_CHARS = 800;
    private static final int MAX_RESULTS = 12;
    private static final long MAX_SCAN_FILE_SIZE = 512 * 1024; // 512 KB

    @Tool(description = """
            Search files under the given workspace for code or config related to a keyword.
            Use this before generating code.
            Returns a compact plain-text list with file paths and short snippets.
            Only files inside workspaceRoot are allowed.
            """)
    public String searchProjectFiles(
            @ToolParam(description = "Absolute path of the current task workspace root") String workspaceRoot,
            @ToolParam(description = "Keyword, module name, class name, endpoint, or feature to search for") String keyword) {

        Path root = normalizeWorkspaceRoot(workspaceRoot);
        String query = safeLower(keyword);
        if (query.isBlank()) {
            return "No keyword provided.";
        }

        List<SearchHit> hits = new ArrayList<>();

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (!attrs.isRegularFile()) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (attrs.size() > MAX_SCAN_FILE_SIZE) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (shouldSkip(file)) {
                            return FileVisitResult.CONTINUE;
                        }

                        String pathText = root.relativize(file).toString().replace('\\', '/');
                        String content = Files.readString(file, StandardCharsets.UTF_8);

                        int score = score(pathText, content, query);
                        if (score <= 0) {
                            return FileVisitResult.CONTINUE;
                        }

                        String snippet = buildSnippet(content, query);
                        hits.add(new SearchHit(pathText, score, snippet));
                    }
                    catch (Exception ignored) {
                        // 忽略单个文件异常，继续扫描
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            return "Search failed: " + e.getMessage();
        }

        if (hits.isEmpty()) {
            return "No matching files found under " + root;
        }

        return hits.stream()
                .sorted(Comparator.comparingInt(SearchHit::score).reversed()
                        .thenComparing(SearchHit::path))
                .limit(MAX_RESULTS)
                .map(hit -> """
                        path: %s
                        score: %d
                        snippet:
                        %s
                        ----
                        """.formatted(hit.path(), hit.score(), hit.snippet()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = """
            Read a file under the given workspace.
            Use this to inspect reference controller, service, mapper, xml, vue, api, router, or config files.
            Returns plain text content.
            Only files inside workspaceRoot are allowed.
            """)
    public String readProjectFile(
            @ToolParam(description = "Absolute path of the current task workspace root") String workspaceRoot,
            @ToolParam(description = "Path relative to workspaceRoot, such as backend/src/main/java/... or frontend/src/views/...") String path) {

        Path root = normalizeWorkspaceRoot(workspaceRoot);
        Path target = resolveInsideWorkspace(root, path);

        if (!Files.exists(target)) {
            return "File not found: " + root.relativize(target).toString().replace('\\', '/');
        }
        if (!Files.isRegularFile(target)) {
            return "Target is not a regular file: " + root.relativize(target).toString().replace('\\', '/');
        }

        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            if (content.length() <= MAX_FILE_READ_CHARS) {
                return content;
            }
            return content.substring(0, MAX_FILE_READ_CHARS)
                    + "\n\n[TRUNCATED: file too long, only first "
                    + MAX_FILE_READ_CHARS + " chars returned]";
        }
        catch (IOException e) {
            return "Read failed: " + e.getMessage();
        }
    }

    private int score(String pathText, String content, String query) {
        String lowerPath = safeLower(pathText);
        String lowerContent = safeLower(content);

        int score = 0;
        if (lowerPath.contains(query)) {
            score += 20;
        }

        String[] terms = query.split("\\s+");
        for (String term : terms) {
            if (term.isBlank()) {
                continue;
            }
            if (lowerPath.contains(term)) {
                score += 8;
            }

            int idx = 0;
            while (true) {
                idx = lowerContent.indexOf(term, idx);
                if (idx < 0) {
                    break;
                }
                score += 2;
                idx += term.length();
            }
        }
        return score;
    }

    private String buildSnippet(String content, String query) {
        String lower = safeLower(content);
        int idx = lower.indexOf(query);

        if (idx < 0) {
            String[] terms = query.split("\\s+");
            for (String term : terms) {
                if (term.isBlank()) {
                    continue;
                }
                idx = lower.indexOf(term);
                if (idx >= 0) {
                    break;
                }
            }
        }

        if (idx < 0) {
            return shorten(content, MAX_SNIPPET_CHARS);
        }

        int start = Math.max(0, idx - 220);
        int end = Math.min(content.length(), idx + 580);
        return shorten(content.substring(start, end), MAX_SNIPPET_CHARS);
    }

    private boolean shouldSkip(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return name.endsWith(".jar")
                || name.endsWith(".class")
                || name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".pdf")
                || name.endsWith(".zip")
                || name.endsWith(".tar")
                || name.endsWith(".gz")
                || name.endsWith(".min.js")
                || file.toString().contains(".git")
                || file.toString().contains("node_modules")
                || file.toString().contains("target")
                || file.toString().contains("dist");
    }

    private String shorten(String text, int max) {
        String normalized = text.replace("\r\n", "\n");
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...";
    }

    private String safeLower(String value) {
        return Objects.toString(value, "").toLowerCase(Locale.ROOT);
    }

    private Path normalizeWorkspaceRoot(String workspaceRoot) {
        try {
            Path root = Path.of(workspaceRoot).toAbsolutePath().normalize();
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                throw new IllegalArgumentException("Workspace root does not exist or is not a directory: " + workspaceRoot);
            }
            return root;
        }
        catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid workspace root: " + workspaceRoot, e);
        }
    }

    private Path resolveInsideWorkspace(Path root, String relativePath) {
        try {
            Path resolved = root.resolve(relativePath).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
            }
            return resolved;
        }
        catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid path: " + relativePath, e);
        }
    }

    private record SearchHit(String path, int score, String snippet) {
    }
}
