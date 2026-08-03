import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.Zip

plugins {
    java
    jacoco
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "dev.z1ppzy"
version = "1.0.3"

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

val logoSource = layout.projectDirectory.file("resourcepack/logo.png")
val preparedLogo = layout.buildDirectory.file(
    "generated-resourcepack/assets/guiok/textures/font/logo.png")
val coinSource = layout.projectDirectory.file("resourcepack/coin.png")
val preparedCoin = layout.buildDirectory.file(
    "generated-resourcepack/assets/guiok/textures/font/coin.png")

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
    val maxWidth = 12
    val maxHeight = 12
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

val resourcePackZip by tasks.registering(Zip::class) {
    dependsOn(prepareLogo, prepareCoin)
    archiveFileName.set("GuiOkResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.projectDirectory.dir("resourcepack/pack"))
    from(preparedLogo) {
        into("assets/guiok/textures/font")
    }
    from(preparedCoin) {
        into("assets/guiok/textures/font")
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

val generateBuildInfo by tasks.registering {
    dependsOn(resourcePackZip)
    inputs.file(resourcePackZip.flatMap { it.archiveFile })
    inputs.property("pluginVersion", pluginVersion)
    inputs.property("commit", buildCommit)
    inputs.property("commitDate", buildCommitDate)
    inputs.property("paperTarget", paperApiVersion)
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
                "resourcePackSha1=${sha1(resourcePackZip.get().archiveFile.get().asFile)}"
            ).joinToString(System.lineSeparator(), postfix = System.lineSeparator()),
            Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated-resources"))
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
                "assets/guiok/textures/font/coin.png")
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
        dependsOn(generateBuildInfo)
        filteringCharset = "UTF-8"
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
        dependsOn(validateResourcePack)
    }
    jar {
        dependsOn(resourcePackZip)
        archiveFileName.set("GuiOk.jar")
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
