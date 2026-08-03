package dev.z1ppzy.guiok.items;

import dev.z1ppzy.guiok.ConfigException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ResourcePackItemCompiler {
    private static final int MAX_TEXTURE_EDGE = 1024;

    private ResourcePackItemCompiler() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "Expected: <items.yml> <texture-source-directory> <pack-output-directory>");
        }
        PackCompilationResult result = compile(
                Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
        System.out.println("Compiled GuiOk custom items: " + result.itemCount());
    }

    static PackCompilationResult compile(
            Path configFile,
            Path textureSourceDirectory,
            Path outputDirectory) throws IOException, ConfigException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new ConfigException("items.yml is invalid YAML: " + exception.getMessage());
        }

        ItemCatalog catalog = ItemConfigLoader.load(config);
        Path sourceRoot = textureSourceDirectory.toAbsolutePath().normalize();
        Path outputRoot = outputDirectory.toAbsolutePath().normalize();
        Set<Path> generated = new LinkedHashSet<>();
        Set<Path> models = new LinkedHashSet<>();
        Set<Path> copiedTextures = new LinkedHashSet<>();

        for (ItemPackDefinition source : catalog.packDefinitions().values()) {
            NamespacedKey model = source.item().model();
            NamespacedKey texture = source.texture();
            Path itemDefinition = inside(outputRoot, "assets", model.getNamespace(),
                    "items", model.getKey() + ".json");
            Path modelFile = inside(outputRoot, "assets", model.getNamespace(),
                    "models", "item", model.getKey() + ".json");
            if (!models.add(modelFile)) {
                throw new ConfigException("Duplicate generated model: " + model);
            }

            Path textureSource = inside(sourceRoot, texture.getNamespace(),
                    texture.getKey() + ".png");
            if (!Files.isRegularFile(textureSource)) {
                throw new ConfigException("Missing texture for " + source.item().id()
                        + ": " + textureSource);
            }
            validateTexture(textureSource, source.item().id());

            Path textureTarget = inside(outputRoot, "assets", texture.getNamespace(),
                    "textures", "item", texture.getKey() + ".png");
            write(itemDefinition, itemDefinitionJson(model));
            write(modelFile, modelJson(source.parent(), texture));
            if (copiedTextures.add(textureTarget)) {
                Files.createDirectories(textureTarget.getParent());
                Files.copy(textureSource, textureTarget, StandardCopyOption.REPLACE_EXISTING);
            }
            generated.add(itemDefinition);
            generated.add(modelFile);
            generated.add(textureTarget);
        }
        return new PackCompilationResult(catalog.size(), generated);
    }

    private static void validateTexture(Path texture, String itemId)
            throws IOException, ConfigException {
        BufferedImage image = ImageIO.read(texture.toFile());
        if (image == null) {
            throw new ConfigException("Texture for " + itemId + " is not a readable PNG: " + texture);
        }
        if (image.getWidth() > MAX_TEXTURE_EDGE || image.getHeight() > MAX_TEXTURE_EDGE) {
            throw new ConfigException("Texture for " + itemId + " exceeds "
                    + MAX_TEXTURE_EDGE + 'x' + MAX_TEXTURE_EDGE + ": " + texture);
        }
    }

    private static String itemDefinitionJson(NamespacedKey model) {
        return """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "%s:item/%s"
                  }
                }
                """.formatted(model.getNamespace(), model.getKey());
    }

    private static String modelJson(NamespacedKey parent, NamespacedKey texture) {
        return """
                {
                  "parent": "%s",
                  "textures": {
                    "layer0": "%s:item/%s"
                  }
                }
                """.formatted(parent, texture.getNamespace(), texture.getKey());
    }

    private static void write(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static Path inside(Path root, String first, String... more) throws ConfigException {
        Path target = root.resolve(Path.of(first, more)).normalize();
        if (!target.startsWith(root)) {
            throw new ConfigException("Generated resource path escapes its root: " + target);
        }
        return target;
    }
}
