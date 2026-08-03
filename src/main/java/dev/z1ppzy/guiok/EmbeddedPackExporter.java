package dev.z1ppzy.guiok;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Logger;

public final class EmbeddedPackExporter {
    private static final String RESOURCE = "embedded/GuiOkResourcePack.zip";

    private EmbeddedPackExporter() {
    }

    public static Path export(Path dataFolder, ClassLoader classLoader, Logger logger)
            throws IOException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Files.createDirectories(dataFolder);
        Path target = dataFolder.resolve("GuiOkResourcePack.zip");
        Path temporary = dataFolder.resolve("GuiOkResourcePack.zip.tmp");
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("JAR does not contain " + RESOURCE);
            }
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        logger.info("Exported bundled resource pack to " + target.toAbsolutePath());
        return target;
    }
}
