package de.erethon.hephaestus.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ResourcePackIndexer {

    private final Path resourcePackRoot;

    public ResourcePackIndexer(Path resourcePackRoot) {
        this.resourcePackRoot = resourcePackRoot;
    }

    public boolean isConfigured() {
        return resourcePackRoot != null && Files.isDirectory(resourcePackRoot);
    }

    public Optional<Path> resolveAssetPath(String relativePath) {
        if (!isConfigured() || relativePath == null || relativePath.contains("..")) {
            return Optional.empty();
        }
        Path resolved = resourcePackRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(resourcePackRoot.normalize()) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    public String resolveIconUrl(Identifier modelKey) {
        if (modelKey == null) {
            return null;
        }
        if (!isConfigured()) {
            return null;
        }
        return findTexture(modelKey)
                .or(() -> findExistingFallbackTexture(modelKey))
                .map(texture -> "/resourcepack/assets/" + texture.namespace() + "/textures/" + texture.path() + ".png")
                .orElse(null);
    }

    private Optional<TextureRef> findTexture(Identifier modelKey) {
        Optional<TextureRef> itemDefinitionTexture = readTexture(resourcePackRoot.resolve("assets")
                .resolve(modelKey.getNamespace())
                .resolve("items")
                .resolve(modelKey.getPath() + ".json"), modelKey.getNamespace());
        if (itemDefinitionTexture.isPresent()) {
            return itemDefinitionTexture;
        }
        return readTexture(resourcePackRoot.resolve("assets")
                .resolve(modelKey.getNamespace())
                .resolve("models")
                .resolve("item")
                .resolve(modelKey.getPath() + ".json"), modelKey.getNamespace());
    }

    private Optional<TextureRef> readTexture(Path jsonPath, String defaultNamespace) {
        if (!Files.isRegularFile(jsonPath)) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(jsonPath, StandardCharsets.UTF_8)).getAsJsonObject();
            Optional<TextureRef> direct = findTextureInObject(root, defaultNamespace);
            if (direct.isPresent()) {
                return direct;
            }
            if (root.has("model") && root.get("model").isJsonObject()) {
                JsonObject model = root.getAsJsonObject("model");
                Optional<TextureRef> nested = findTextureInObject(model, defaultNamespace);
                if (nested.isPresent()) {
                    return nested;
                }
                return findTextureFromModelReference(model, defaultNamespace);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<TextureRef> findTextureInObject(JsonObject object, String defaultNamespace) {
        if (object.has("textures") && object.get("textures").isJsonObject()) {
            JsonObject textures = object.getAsJsonObject("textures");
            if (textures.has("layer0")) {
                return resolveExistingTexture(textures.get("layer0").getAsString(), defaultNamespace);
            }
            if (textures.has("all")) {
                return resolveExistingTexture(textures.get("all").getAsString(), defaultNamespace);
            }
            if (!textures.entrySet().isEmpty()) {
                return resolveExistingTexture(textures.entrySet().iterator().next().getValue().getAsString(), defaultNamespace);
            }
        }
        if (object.has("texture")) {
            return resolveExistingTexture(object.get("texture").getAsString(), defaultNamespace);
        }
        return Optional.empty();
    }

    private Optional<TextureRef> findTextureFromModelReference(JsonObject object, String defaultNamespace) {
        if (!object.has("model") || !object.get("model").isJsonPrimitive()) {
            return Optional.empty();
        }
        Identifier modelReference = parseIdentifier(object.get("model").getAsString(), defaultNamespace);
        Path modelPath = resourcePackRoot.resolve("assets")
                .resolve(modelReference.getNamespace())
                .resolve("models")
                .resolve(modelReference.getPath() + ".json");
        return readTexture(modelPath, modelReference.getNamespace());
    }

    private Optional<TextureRef> findExistingFallbackTexture(Identifier modelKey) {
        if (modelKey.getNamespace().equals("minecraft")) {
            Optional<TextureRef> vanillaItemTexture = resolveExistingTexture("item/" + modelKey.getPath(), modelKey.getNamespace());
            if (vanillaItemTexture.isPresent()) {
                return vanillaItemTexture;
            }
        }
        return resolveExistingTexture(modelKey.getPath(), modelKey.getNamespace());
    }

    private Optional<TextureRef> resolveExistingTexture(String texture, String defaultNamespace) {
        TextureRef textureRef = TextureRef.parse(texture, defaultNamespace);
        Path texturePath = resourcePackRoot.resolve("assets")
                .resolve(textureRef.namespace())
                .resolve("textures")
                .resolve(textureRef.path() + ".png");
        return Files.isRegularFile(texturePath) ? Optional.of(textureRef) : Optional.empty();
    }

    private Identifier parseIdentifier(String value, String defaultNamespace) {
        int separator = value.indexOf(':');
        if (separator >= 0) {
            return Identifier.parse(value);
        }
        return Identifier.fromNamespaceAndPath(defaultNamespace, value);
    }

    private record TextureRef(String namespace, String path) {
        private static TextureRef parse(String value, String defaultNamespace) {
            int separator = value.indexOf(':');
            if (separator >= 0) {
                return new TextureRef(value.substring(0, separator), value.substring(separator + 1));
            }
            return new TextureRef(defaultNamespace, value);
        }
    }
}
