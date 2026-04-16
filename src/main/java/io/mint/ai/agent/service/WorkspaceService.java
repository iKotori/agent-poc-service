package io.mint.ai.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Service
public class WorkspaceService {

    private final Path backendTemplateRoot;
    private final Path frontendTemplateRoot;

    public WorkspaceService(
            @Value("${app.template.backend-root:./templates/backend-template}") String backendTemplateRoot,
            @Value("${app.template.frontend-root:./templates/frontend-template}") String frontendTemplateRoot) {
        this.backendTemplateRoot = Path.of(backendTemplateRoot).toAbsolutePath().normalize();
        this.frontendTemplateRoot = Path.of(frontendTemplateRoot).toAbsolutePath().normalize();
    }

    public String prepareWorkspace(String taskId, String targetRoot) {
        Path workspaceRoot = Path.of(targetRoot).toAbsolutePath().normalize();
        Path backendTarget = workspaceRoot.resolve("backend");
        Path frontendTarget = workspaceRoot.resolve("frontend");

        try {
            Files.createDirectories(workspaceRoot);

            if (!Files.exists(backendTarget)) {
                copyDirectory(backendTemplateRoot, backendTarget);
            }
            if (!Files.exists(frontendTarget)) {
                copyDirectory(frontendTemplateRoot, frontendTarget);
            }

            return workspaceRoot.toString();
        }
        catch (IOException e) {
            throw new IllegalStateException("Prepare workspace failed for taskId=" + taskId, e);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            throw new IllegalArgumentException("Template root not found: " + source);
        }

        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Path targetDir = target.resolve(relative);
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path targetFile = target.resolve(relative);
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
