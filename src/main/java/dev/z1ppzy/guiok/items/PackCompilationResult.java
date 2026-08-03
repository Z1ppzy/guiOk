package dev.z1ppzy.guiok.items;

import java.nio.file.Path;
import java.util.Set;

record PackCompilationResult(int itemCount, Set<Path> generatedFiles) {
    PackCompilationResult {
        generatedFiles = Set.copyOf(generatedFiles);
    }
}
