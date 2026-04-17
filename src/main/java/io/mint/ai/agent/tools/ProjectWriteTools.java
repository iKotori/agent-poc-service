package io.mint.ai.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Component
public class ProjectWriteTools {

    private static final Logger log = LoggerFactory.getLogger(ProjectWriteTools.class);

    private static final Set<String> DEFAULT_PROTECTED_NAMES = Set.of(
            "pom.xml",
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            "application-prod.yml"
    );

    @Tool(description = """
            Create or overwrite a file under the given workspace.
            Use this for creating entity, dto, vo, mapper, xml, controller, service, vue page, api js, or similar generated files.
            Only files inside workspaceRoot are allowed.
            """)
    public String writeProjectFile(
            @ToolParam(description = "Absolute path of the current task workspace root") String workspaceRoot,
            @ToolParam(description = "Path relative to workspaceRoot for the file to write") String path,
            @ToolParam(description = "Complete file content to write") String content) {
        log.info("[tool:writeProjectFile] workspaceRoot={}, path={}", workspaceRoot, path);

        Path root = normalizeWorkspaceRoot(workspaceRoot);
        Path target = resolveInsideWorkspace(root, path);
        ensureWritableTarget(target);

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            return "Wrote file: " + root.relativize(target).toString().replace('\\', '/');
        }
        catch (IOException e) {
            return "Write failed: " + e.getMessage();
        }
    }

    @Tool(description = """
            Patch an existing file under the given workspace.
            Preferred format:
            <<<<<<< SEARCH
            old text
            =======
            new text
            >>>>>>> REPLACE
            If the marker format is absent, the patch text will be appended to the end of the file.
            Only files inside workspaceRoot are allowed.
            """)
    public String patchProjectFile(
            @ToolParam(description = "Absolute path of the current task workspace root") String workspaceRoot,
            @ToolParam(description = "Path relative to workspaceRoot for the file to patch") String path,
            @ToolParam(description = "Patch text in SEARCH/REPLACE format, or raw text to append") String patch) {
        log.info("[tool:patchProjectFile] workspaceRoot={}, path={}", workspaceRoot, path);
        Path root = normalizeWorkspaceRoot(workspaceRoot);
        Path target = resolveInsideWorkspace(root, path);
        ensureWritableTarget(target);

        if (!Files.exists(target)) {
            return "Patch failed: file does not exist: " + root.relativize(target).toString().replace('\\', '/');
        }

        try {
            String original = Files.readString(target, StandardCharsets.UTF_8);
            String updated = applyPatch(original, patch == null ? "" : patch);

            if (updated.equals(original)) {
                return "Patch made no changes: " + root.relativize(target).toString().replace('\\', '/');
            }

            Files.writeString(target, updated, StandardCharsets.UTF_8);
            return "Patched file: " + root.relativize(target).toString().replace('\\', '/');
        }
        catch (IOException e) {
            return "Patch failed: " + e.getMessage();
        }
    }

    private String applyPatch(String original, String patch) {
        final String startMarker = "<<<<<<< SEARCH";
        final String middleMarker = "=======";
        final String endMarker = ">>>>>>> REPLACE";

        int start = patch.indexOf(startMarker);
        int middle = patch.indexOf(middleMarker);
        int end = patch.indexOf(endMarker);

        if (start >= 0 && middle > start && end > middle) {
            String searchBlock = patch.substring(start + startMarker.length(), middle).trim();
            String replaceBlock = patch.substring(middle + middleMarker.length(), end).trim();

            if (searchBlock.isEmpty()) {
                return original + System.lineSeparator() + replaceBlock;
            }

            int idx = original.indexOf(searchBlock);
            if (idx < 0) {
                return original + System.lineSeparator()
                        + "/* PATCH_SEARCH_NOT_FOUND */" + System.lineSeparator()
                        + replaceBlock;
            }

            return original.substring(0, idx)
                    + replaceBlock
                    + original.substring(idx + searchBlock.length());
        }

        String append = patch.trim();
        if (append.isEmpty()) {
            return original;
        }

        if (original.endsWith("\n") || original.endsWith("\r\n")) {
            return original + append + System.lineSeparator();
        }
        return original + System.lineSeparator() + append + System.lineSeparator();
    }

    private void ensureWritableTarget(Path target) {
        String fileName = target.getFileName().toString();
        if (DEFAULT_PROTECTED_NAMES.contains(fileName)) {
            throw new IllegalArgumentException("Protected file is not writable in POC mode: " + fileName);
        }

        String normalized = target.toString().replace('\\', '/');
        List<String> blockedDirs = List.of("/.git/", "/target/", "/node_modules/", "/dist/");
        for (String blocked : blockedDirs) {
            if (normalized.contains(blocked)) {
                throw new IllegalArgumentException("Blocked path is not writable: " + normalized);
            }
        }
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
}
