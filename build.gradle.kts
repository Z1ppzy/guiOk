/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    java
    jacoco
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.z1ppzy"
version = "1.1.2"

val projectAuthor = "Z1ppzy"
val projectLicense = "GuiOk Source-Available License 1.0"
val licenseFile = layout.projectDirectory.file("LICENSE")

val paperApiVersion = providers.gradleProperty("paperApiVersion")
    .orElse("26.1.2.build.74-stable")
val minecraftVersion = providers.gradleProperty("minecraftVersion").orElse("26.2")

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")

    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

fun gitOutput(vararg command: String): String = runCatching {
    val output = providers.exec {
        commandLine(*command)
        isIgnoreExitValue = true
    }
    if (output.result.get().exitValue == 0) output.standardOutput.asText.get().trim() else ""
}.getOrDefault("")

fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun prepareBitmap(
    sourceFile: File,
    targetFile: File,
    maxWidth: Int,
    maxHeight: Int,
    padding: Int
): BufferedImage {
    val source = ImageIO.read(sourceFile)
        ?: throw GradleException("${sourceFile.invariantSeparatorsPath} is not a readable PNG")

    var minX = source.width
    var minY = source.height
    var maxX = -1
    var maxY = -1
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            if ((source.getRGB(x, y) ushr 24) > 8) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }
    if (maxX < minX || maxY < minY) {
        throw GradleException("${sourceFile.invariantSeparatorsPath} is fully transparent")
    }

    val cropWidth = maxX - minX + 1
    val cropHeight = maxY - minY + 1
    val scale = minOf(
        1.0,
        (maxWidth - padding * 2).toDouble() / cropWidth,
        (maxHeight - padding * 2).toDouble() / cropHeight)
    val scaledWidth = maxOf(1, (cropWidth * scale).toInt())
    val scaledHeight = maxOf(1, (cropHeight * scale).toInt())

    val result = BufferedImage(
        scaledWidth + padding * 2,
        scaledHeight + padding * 2,
        BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        graphics.drawImage(
            source,
            padding,
            padding,
            padding + scaledWidth,
            padding + scaledHeight,
            minX,
            minY,
            maxX + 1,
            maxY + 1,
            null)
    } finally {
        graphics.dispose()
    }

    targetFile.parentFile.mkdirs()
    if (!ImageIO.write(result, "png", targetFile)) {
        throw GradleException("No PNG writer is available")
    }
    return result
}

fun prepareButton(
    targetFile: File,
    fillTop: Int,
    fillBottom: Int,
    border: Int,
    highlight: Int
) {
    val width = 200
    val height = 20
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val transparentCorner =
                (x == 0 || x == width - 1) && (y == 0 || y == height - 1)
            val color = when {
                transparentCorner -> 0
                x == 0 || x == width - 1 || y == 0 || y == height - 1 ->
                    0xff100b18.toInt()
                x == 1 || x == width - 2 || y == 1 || y == height - 2 -> border
                y == 2 -> highlight
                y >= height - 4 -> fillBottom
                else -> fillTop
            }
            image.setRGB(x, y, color)
        }
    }

    targetFile.parentFile.mkdirs()
    if (!ImageIO.write(image, "png", targetFile)) {
        throw GradleException("No PNG writer is available")
    }
}

val logoSource = layout.projectDirectory.file("resourcepack/logo.png")
val preparedLogo = layout.buildDirectory.file(
    "generated-resourcepack/assets/guiok/textures/font/logo.png")
val coinSource = layout.projectDirectory.file("resourcepack/coin.png")
val preparedCoin = layout.buildDirectory.file(
    "generated-resourcepack/assets/guiok/textures/font/coin.png")
val pauseMenuSource = layout.projectDirectory.file("resourcepack/pause-logo.png")
val preparedPauseMenu = layout.buildDirectory.file(
    "generated-resourcepack/assets/guiok/textures/font/pause_menu.png")
val preparedPauseButtons = layout.buildDirectory.dir("generated-pause-buttons")
val itemConfigSource = layout.projectDirectory.file("resourcepack/items.yml")
val itemTextureSources = layout.projectDirectory.dir("resourcepack/items")
val compiledItemPack = layout.buildDirectory.dir("generated-item-pack")

val prepareLogo by tasks.registering {
    val maxWidth = 240
    val maxHeight = 72
    inputs.file(logoSource)
    inputs.property("maxWidth", maxWidth)
    inputs.property("maxHeight", maxHeight)
    outputs.file(preparedLogo)
    doLast {
        val result = prepareBitmap(
            logoSource.asFile, preparedLogo.get().asFile, maxWidth, maxHeight, 2)
        logger.lifecycle("Prepared HUD logo: ${result.width}x${result.height}")
    }
}

val prepareCoin by tasks.registering {
    val maxWidth = 16
    val maxHeight = 16
    inputs.file(coinSource)
    inputs.property("maxWidth", maxWidth)
    inputs.property("maxHeight", maxHeight)
    outputs.file(preparedCoin)
    doLast {
        val result = prepareBitmap(
            coinSource.asFile, preparedCoin.get().asFile, maxWidth, maxHeight, 1)
        logger.lifecycle("Prepared HUD coin: ${result.width}x${result.height}")
    }
}

val preparePauseMenu by tasks.registering {
    val maxWidth = 224
    val maxHeight = 88
    inputs.file(pauseMenuSource)
    inputs.property("maxWidth", maxWidth)
    inputs.property("maxHeight", maxHeight)
    outputs.file(preparedPauseMenu)
    doLast {
        val result = prepareBitmap(
            pauseMenuSource.asFile,
            preparedPauseMenu.get().asFile,
            maxWidth,
            maxHeight,
            2)
        logger.lifecycle("Prepared pause-menu logo: ${result.width}x${result.height}")
    }
}

val preparePauseButtons by tasks.registering {
    outputs.dir(preparedPauseButtons)
    doLast {
        val directory = preparedPauseButtons.get().asFile
        prepareButton(
            directory.resolve("button.png"),
            0xff4d2f6b.toInt(),
            0xff352047.toInt(),
            0xff76509a.toInt(),
            0xffa97acd.toInt())
        prepareButton(
            directory.resolve("button_highlighted.png"),
            0xffffa629.toInt(),
            0xffe97118.toInt(),
            0xffffd45c.toInt(),
            0xffffef9a.toInt())
        prepareButton(
            directory.resolve("button_disabled.png"),
            0xff32293e.toInt(),
            0xff251e2e.toInt(),
            0xff51445f.toInt(),
            0xff6b5b78.toInt())
        logger.lifecycle("Prepared HeavenlyWeiner pause-menu buttons")
    }
}

val compileItemPack by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Compiles items.yml and item PNGs into modern resource-pack models."
    dependsOn(tasks.named("classes"))
    inputs.file(itemConfigSource)
    inputs.dir(itemTextureSources)
    outputs.dir(compiledItemPack)
    classpath = sourceSets.main.get().output + configurations.compileClasspath.get()
    mainClass.set("dev.z1ppzy.guiok.items.ResourcePackItemCompiler")
    args(
        itemConfigSource.asFile.absolutePath,
        itemTextureSources.asFile.absolutePath,
        compiledItemPack.get().asFile.absolutePath)
    doFirst {
        delete(compiledItemPack)
    }
}

val resourcePackZip by tasks.registering(Zip::class) {
    dependsOn(
        prepareLogo,
        prepareCoin,
        preparePauseMenu,
        preparePauseButtons,
        compileItemPack)
    archiveFileName.set("GuiOkResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(layout.projectDirectory.dir("resourcepack/pack"))
    from(preparedLogo) {
        into("assets/guiok/textures/font")
    }
    from(preparedCoin) {
        into("assets/guiok/textures/font")
    }
    from(preparedPauseMenu) {
        into("assets/guiok/textures/font")
    }
    from(preparedPauseButtons) {
        into("assets/minecraft/textures/gui/sprites/widget")
    }
    from(compiledItemPack)
    from(licenseFile) {
        rename { "LICENSE.txt" }
    }
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

val rawCommit = gitOutput("git", "rev-parse", "--short=12", "HEAD").ifEmpty { "unknown" }
val dirty = gitOutput("git", "status", "--porcelain").isNotEmpty()
val buildCommit = if (dirty && rawCommit != "unknown") "$rawCommit-dirty" else rawCommit
val buildCommitDate = gitOutput("git", "show", "-s", "--format=%cI", "HEAD")
    .ifEmpty { "unknown" }

val generatedBuildInfo = layout.buildDirectory.file("generated-resources/guiok-build.properties")
val pluginVersion = project.version.toString()

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the compileOnly API artifact for dependent plugins."
    dependsOn(tasks.named("classes"))
    archiveFileName.set("GuiOk-api.jar")
    from(sourceSets.main.get().output) {
        include("dev/z1ppzy/guiok/api/**")
    }
    from(licenseFile) {
        rename { "LICENSE.txt" }
    }
    manifest {
        attributes(
            "Implementation-Title" to "GuiOk API",
            "Implementation-Version" to pluginVersion,
            "Implementation-Vendor" to projectAuthor,
            "Specification-Vendor" to projectAuthor,
            "Built-By" to projectAuthor,
            "GuiOk-Author" to projectAuthor,
            "GuiOk-License" to projectLicense,
            "GuiOk-API-Version" to "1")
    }
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

val generateBuildInfo by tasks.registering {
    dependsOn(resourcePackZip)
    inputs.file(resourcePackZip.flatMap { it.archiveFile })
    inputs.property("pluginVersion", pluginVersion)
    inputs.property("commit", buildCommit)
    inputs.property("commitDate", buildCommitDate)
    inputs.property("paperTarget", paperApiVersion)
    inputs.property("author", projectAuthor)
    inputs.property("license", projectLicense)
    outputs.file(generatedBuildInfo)
    doLast {
        val target = generatedBuildInfo.get().asFile
        target.parentFile.mkdirs()
        target.writeText(
            listOf(
                "version=$pluginVersion",
                "commit=$buildCommit",
                "commitDate=$buildCommitDate",
                "paperTarget=${paperApiVersion.get()}",
                "resourcePackSha1=${sha1(resourcePackZip.get().archiveFile.get().asFile)}",
                "author=$projectAuthor",
                "license=$projectLicense"
            ).joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
            Charsets.UTF_8)
    }
}

val validateResourcePack by tasks.registering {
    group = "verification"
    description = "Validates the generated resource-pack structure and glyph bounds."
    dependsOn(resourcePackZip)
    inputs.file(resourcePackZip.flatMap { it.archiveFile })
    doLast {
        val pack = resourcePackZip.get().archiveFile.get().asFile
        ZipFile(pack).use { zip ->
            val required = listOf(
                "pack.mcmeta",
                "assets/guiok/font/hud.json",
                "assets/guiok/textures/font/logo.png",
                "assets/guiok/textures/font/coin.png",
                "assets/guiok/textures/font/pause_menu.png",
                "assets/minecraft/font/default.json",
                "assets/minecraft/lang/en_us.json",
                "assets/minecraft/lang/ru_ru.json",
                "assets/minecraft/textures/gui/sprites/widget/button.png",
                "assets/minecraft/textures/gui/sprites/widget/button.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png",
                "assets/minecraft/textures/gui/sprites/widget/button_highlighted.png.mcmeta",
                "assets/minecraft/textures/gui/sprites/widget/button_disabled.png",
                "assets/minecraft/textures/gui/sprites/widget/button_disabled.png.mcmeta")
            val missing = required.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) {
                throw GradleException("Resource pack misses: ${missing.joinToString()}")
            }
            for (path in required.filter { it.endsWith(".png") }) {
                val image = ImageIO.read(zip.getInputStream(zip.getEntry(path)))
                    ?: throw GradleException("Generated glyph $path is not a readable PNG")
                if (image.width > 256 || image.height > 256) {
                    throw GradleException("Generated glyph $path exceeds Minecraft's 256x256 limit")
                }
            }
            val buttonPaths = required.filter {
                it.startsWith("assets/minecraft/textures/gui/sprites/widget/")
                    && it.endsWith(".png")
            }
            for (path in buttonPaths) {
                val image = ImageIO.read(zip.getInputStream(zip.getEntry(path)))
                    ?: throw GradleException("Generated button $path is not a readable PNG")
                if (image.width != 200 || image.height != 20) {
                    throw GradleException("Generated button $path must be exactly 200x20")
                }
            }
            val fontJson = zip.getInputStream(zip.getEntry("assets/minecraft/font/default.json"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            if (!fontJson.contains("guiok:font/pause_menu.png")
                    || !fontJson.contains("\\ue100")) {
                throw GradleException("Default font does not register the pause-menu glyph")
            }
            val hudFontJson = zip.getInputStream(zip.getEntry("assets/guiok/font/hud.json"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val sharedGlyphs = mapOf(
                "guiok:font/logo.png" to "\\ue001",
                "guiok:font/coin.png" to "\\ue002")
            for ((texture, codepoint) in sharedGlyphs) {
                if (!hudFontJson.contains(texture) || !hudFontJson.contains(codepoint)) {
                    throw GradleException("HUD font no longer provides $texture ($codepoint)")
                }
                if (!fontJson.contains(texture) || !fontJson.contains(codepoint)) {
                    throw GradleException(
                        "Default font does not expose $texture ($codepoint) to other plugins")
                }
            }
            for (language in listOf("en_us", "ru_ru")) {
                val languageJson = zip.getInputStream(
                    zip.getEntry("assets/minecraft/lang/$language.json"))
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                if (!languageJson.contains("HeavenlyWeiner")
                        || !languageJson.contains("\\ue100")) {
                    throw GradleException(
                        "$language pause-menu translations are incomplete")
                }
            }
        }
    }
}

val validateApiJar by tasks.registering {
    group = "verification"
    description = "Ensures the public API artifact contains no implementation classes."
    dependsOn(apiJar)
    inputs.file(apiJar.flatMap { it.archiveFile })
    doLast {
        ZipFile(apiJar.get().archiveFile.get().asFile).use { zip ->
            val required = listOf(
                "dev/z1ppzy/guiok/api/GuiOkApi.class",
                "dev/z1ppzy/guiok/api/GuiOkItemDefinition.class",
                "dev/z1ppzy/guiok/api/event/GuiOkItemsReloadedEvent.class")
            val missing = required.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) {
                throw GradleException("API JAR misses: ${missing.joinToString()}")
            }
            val leaked = zip.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") }
                .filterNot { it.startsWith("dev/z1ppzy/guiok/api/") }
                .toList()
            if (leaked.isNotEmpty()) {
                throw GradleException("API JAR leaks implementation classes: ${leaked.joinToString()}")
            }
        }
    }
}

val validateBranding by tasks.registering {
    group = "verification"
    description = "Validates Z1ppzy attribution and GuiOk licensing in sources and artifacts."
    dependsOn(tasks.named("jar"), apiJar, resourcePackZip)
    inputs.files(fileTree("src") { include("**/*.java") })
    inputs.files(
        tasks.named<Jar>("jar").flatMap { it.archiveFile },
        apiJar.flatMap { it.archiveFile },
        resourcePackZip.flatMap { it.archiveFile })
    doLast {
        val copyright = "Copyright (c) 2026 Z1ppzy. All rights reserved."
        val sourceFiles = fileTree("src") { include("**/*.java") }.files
        val missingHeaders = sourceFiles
            .filterNot { it.readText(Charsets.UTF_8).startsWith("/*\n * $copyright") }
            .map { it.relativeTo(projectDir).invariantSeparatorsPath }
        if (missingHeaders.isNotEmpty()) {
            throw GradleException(
                "Java sources without the Z1ppzy license header: ${missingHeaders.joinToString()}")
        }

        val jars = listOf(
            tasks.named<Jar>("jar").get().archiveFile.get().asFile,
            apiJar.get().archiveFile.get().asFile)
        for (artifact in jars) {
            JarFile(artifact).use { jar ->
                val attributes = jar.manifest.mainAttributes
                val expected = mapOf(
                    "Implementation-Vendor" to projectAuthor,
                    "GuiOk-Author" to projectAuthor,
                    "GuiOk-License" to projectLicense)
                val invalid = expected.filter { (name, value) ->
                    attributes.getValue(name) != value
                }
                if (invalid.isNotEmpty()) {
                    throw GradleException("${artifact.name} has incomplete GuiOk branding: $invalid")
                }
                if (jar.getEntry("LICENSE.txt") == null) {
                    throw GradleException("${artifact.name} does not contain LICENSE.txt")
                }
            }
        }

        ZipFile(resourcePackZip.get().archiveFile.get().asFile).use { zip ->
            if (zip.getEntry("LICENSE.txt") == null) {
                throw GradleException("GuiOkResourcePack.zip does not contain LICENSE.txt")
            }
            val metadata = zip.getInputStream(zip.getEntry("pack.mcmeta"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            if (!metadata.contains("GuiOk") || !metadata.contains(projectAuthor)) {
                throw GradleException(
                    "pack.mcmeta does not identify GuiOk and $projectAuthor")
            }
        }
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
    processResources {
        filteringCharset = "UTF-8"
        from(itemConfigSource) {
            rename { "items.yml" }
        }
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }
    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }
    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required = true
            html.required = true
        }
    }
    check {
        dependsOn(validateResourcePack, validateApiJar, validateBranding)
    }
    assemble {
        dependsOn(apiJar)
    }
    jar {
        dependsOn(resourcePackZip, generateBuildInfo)
        archiveFileName.set("GuiOk.jar")
        exclude(
            "dev/z1ppzy/guiok/items/ResourcePackItemCompiler.class",
            "dev/z1ppzy/guiok/items/PackCompilationResult.class")
        from(generatedBuildInfo)
        from(licenseFile) {
            rename { "LICENSE.txt" }
        }
        manifest {
            attributes(
                "Implementation-Title" to "GuiOk",
                "Implementation-Version" to pluginVersion,
                "Implementation-Vendor" to projectAuthor,
                "Specification-Vendor" to projectAuthor,
                "Built-By" to projectAuthor,
                "GuiOk-Author" to projectAuthor,
                "GuiOk-License" to projectLicense)
        }
        from(resourcePackZip.flatMap { it.archiveFile }) {
            into("embedded")
            rename { "GuiOkResourcePack.zip" }
        }
    }
    runServer {
        minecraftVersion(minecraftVersion.get())
        runDirectory = file("run/${minecraftVersion.get()}")
        jvmArgs("-Xms1G", "-Xmx1G")
    }
}
