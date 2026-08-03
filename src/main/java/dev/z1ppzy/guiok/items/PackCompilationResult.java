/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import java.nio.file.Path;
import java.util.Set;

record PackCompilationResult(int itemCount, Set<Path> generatedFiles) {
    PackCompilationResult {
        generatedFiles = Set.copyOf(generatedFiles);
    }
}
