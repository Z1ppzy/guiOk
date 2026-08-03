/*
 * Copyright (c) 2026 Z1ppzy. All rights reserved.
 * Licensed under the GuiOk Source-Available License 1.0.
 */

package dev.z1ppzy.guiok.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private Path writeConfig(String content) throws Exception {
        Path config = temporary.resolve("items.yml");
        Files.writeString(config, content);
        return config;
    }

    private static void writeTexture(Path target) throws Exception {
        Files.createDirectories(target.getParent());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(8, 8, 0xffffcc00);
        ImageIO.write(image, "png", target.toFile());
    }
}
