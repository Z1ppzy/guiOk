/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.z1ppzy.guiok.ConfigException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackItemCompilerTest {
    @TempDir
    Path temporary;

    @Test
    void generatesModernItemDefinitionModelAndTexture() throws Exception {
        Path config = writeConfig("""
                prison:token:
                  material: GOLD_NUGGET
                  name: '<gold>Тюремный жетон'
                  texture: prison:currency/token
                  parent: minecraft:item/generated
                """);
        Path textures = temporary.resolve("textures");
        writeTexture(textures.resolve("prison/currency/token.png"));
        Path output = temporary.resolve("output");

        PackCompilationResult result = ResourcePackItemCompiler.compile(config, textures, output);

        Path itemDefinition = output.resolve("assets/prison/items/token.json");
        Path model = output.resolve("assets/prison/models/item/token.json");
        Path texture = output.resolve("assets/prison/textures/item/currency/token.png");
        assertEquals(1, result.itemCount());
        assertTrue(Files.isRegularFile(itemDefinition));
        assertTrue(Files.isRegularFile(model));
        assertTrue(Files.isRegularFile(texture));
        assertTrue(Files.readString(itemDefinition).contains("\"model\": \"prison:item/token\""));
        assertTrue(Files.readString(model).contains("\"layer0\": \"prison:item/currency/token\""));
    }

    @Test
    void rejectsMissingTextureBeforeCreatingBrokenPack() throws Exception {
        Path config = writeConfig("""
                prison:token:
                  material: PAPER
                  name: Token
                """);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, temporary.resolve("textures"), temporary.resolve("output")));

        assertTrue(exception.getMessage().contains("texture"));
    }

    @Test
    void rejectsTwoItemsGeneratingTheSameModel() throws Exception {
        Path config = writeConfig("""
                guiok:first:
                  material: PAPER
                  name: First
                  model: guiok:shared
                  texture: guiok:first
                guiok:second:
                  material: PAPER
                  name: Second
                  model: guiok:shared
                  texture: guiok:second
                """);
        Path textures = temporary.resolve("textures");
        writeTexture(textures.resolve("guiok/first.png"), 16, 16);
        writeTexture(textures.resolve("guiok/second.png"), 16, 16);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, textures, temporary.resolve("output")));

        assertTrue(exception.getMessage().contains("Duplicate generated model"));
    }

    @Test
    void sharesOneTextureBetweenItemsThatPointAtIt() throws Exception {
        Path config = writeConfig("""
                guiok:first:
                  material: PAPER
                  name: First
                  texture: guiok:shared
                guiok:second:
                  material: PAPER
                  name: Second
                  texture: guiok:shared
                """);
        Path textures = temporary.resolve("textures");
        writeTexture(textures.resolve("guiok/shared.png"), 16, 16);
        Path output = temporary.resolve("output");

        PackCompilationResult result = ResourcePackItemCompiler.compile(config, textures, output);

        assertEquals(2, result.itemCount());
        assertTrue(Files.isRegularFile(output.resolve("assets/guiok/models/item/first.json")));
        assertTrue(Files.isRegularFile(output.resolve("assets/guiok/models/item/second.json")));
        assertTrue(Files.isRegularFile(output.resolve("assets/guiok/textures/item/shared.png")));
        assertTrue(Files.readString(output.resolve("assets/guiok/models/item/second.json"))
                .contains("\"layer0\": \"guiok:item/shared\""));
    }

    @Test
    void rejectsTexturesLargerThanThePackLimit() throws Exception {
        Path config = writeConfig("""
                guiok:huge:
                  material: PAPER
                  name: Huge
                """);
        Path textures = temporary.resolve("textures");
        writeTexture(textures.resolve("guiok/huge.png"), 1025, 16);

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, textures, temporary.resolve("output")));

        assertTrue(exception.getMessage().contains("exceeds"));
    }

    @Test
    void rejectsAFileThatIsNotActuallyAPng() throws Exception {
        Path config = writeConfig("""
                guiok:fake:
                  material: PAPER
                  name: Fake
                """);
        Path textures = temporary.resolve("textures");
        Files.createDirectories(textures.resolve("guiok"));
        Files.writeString(textures.resolve("guiok/fake.png"), "definitely not a png");

        ConfigException exception = assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, textures, temporary.resolve("output")));

        assertTrue(exception.getMessage().contains("readable PNG"));
    }

    /** A texture namespace pointing outside the source tree must never be read or copied. */
    @Test
    void refusesToReadTexturesOutsideTheSourceTree() throws Exception {
        Path config = writeConfig("""
                guiok:escape:
                  material: PAPER
                  name: Escape
                  texture: '..:secret'
                """);

        assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, temporary.resolve("textures"), temporary.resolve("output")));
        assertFalse(Files.exists(temporary.resolve("output")));
    }

    @Test
    void reportsUnparseableItemFilesAsConfigurationErrors() throws Exception {
        Path config = writeConfig("""
                ..:coin:
                  material: PAPER
                  name: Coin
                """);

        assertThrows(
                ConfigException.class,
                () -> ResourcePackItemCompiler.compile(
                        config, temporary.resolve("textures"), temporary.resolve("output")));
    }

    private Path writeConfig(String content) throws Exception {
        Path config = temporary.resolve("items.yml");
        Files.writeString(config, content);
        return config;
    }

    private static void writeTexture(Path target) throws Exception {
        writeTexture(target, 16, 16);
    }

    private static void writeTexture(Path target, int width, int height) throws Exception {
        Files.createDirectories(target.getParent());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(width / 2, height / 2, 0xffffcc00);
        ImageIO.write(image, "png", target.toFile());
    }
}
