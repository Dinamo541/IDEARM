package io.github.dinamo541.idearm.infrastructure.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Boundary checks shared by the generated-file operations. Never follow reparse points. */
final class SafePaths {
    private SafePaths() { }

    static Path root(Path root) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        inspect(absolute);
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) throw new IOException("project.root.notDirectory: " + absolute);
        return absolute;
    }

    static Path relative(Path root, String relative) throws IOException {
        Path path = Path.of(relative.replace('\\', '/'));
        if (path.isAbsolute() || path.getNameCount() == 0 || relative.isBlank()) throw new IOException("path.invalid: " + relative);
        for (Path part : path) if (part.toString().equals("..") || part.toString().equals(".")) throw new IOException("path.traversal: " + relative);
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) throw new IOException("path.outsideProject: " + relative);
        return resolved;
    }

    static void inspect(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther() || !isLinkFree(path)) {
            throw new IOException("path.link.disallowed: " + path);
        }
    }

    /**
     * Whether no folder on the way to {@code path} is a link or junction. Both sides expand Windows 8.3 names, so the
     * short staging folder chosen for a user name with spaces or accents is not mistaken for a link.
     */
    static boolean isLinkFree(Path path) throws IOException {
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path.toRealPath());
    }

    static void inspectAncestors(Path root, Path path) throws IOException {
        if (!path.startsWith(root)) throw new IOException("path.outsideProject: " + path);
        inspect(root);
        Path current = root;
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) inspect(current);
        }
    }
}
