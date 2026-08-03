package dev.z1ppzy.guiok;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

public record BuildInfo(
        String version,
        String commit,
        String commitDate,
        String paperTarget,
        String resourcePackSha1) {
    private static final String RESOURCE = "guiok-build.properties";

    public BuildInfo {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(commitDate, "commitDate");
        Objects.requireNonNull(paperTarget, "paperTarget");
        Objects.requireNonNull(resourcePackSha1, "resourcePackSha1");
    }

    public static BuildInfo load(ClassLoader classLoader, Logger logger) {
        Properties properties = new Properties();
        try (InputStream input = classLoader.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                logger.warning("Missing " + RESOURCE + "; version diagnostics will be incomplete");
                return unknown();
            }
            properties.load(input);
            return from(properties);
        } catch (IOException exception) {
            logger.warning("Cannot read " + RESOURCE + ": " + exception.getMessage());
            return unknown();
        }
    }

    static BuildInfo from(Properties properties) {
        return new BuildInfo(
                properties.getProperty("version", "unknown"),
                properties.getProperty("commit", "unknown"),
                properties.getProperty("commitDate", "unknown"),
                properties.getProperty("paperTarget", "unknown"),
                properties.getProperty("resourcePackSha1", "unknown"));
    }

    private static BuildInfo unknown() {
        return new BuildInfo("unknown", "unknown", "unknown", "unknown", "unknown");
    }
}
