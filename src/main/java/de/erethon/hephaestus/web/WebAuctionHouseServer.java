package de.erethon.hephaestus.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.erethon.hecate.Hecate;
import de.erethon.hecate.web.WebAccountService;
import de.erethon.hecate.web.WebInventoryService;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.BuyOrder;
import de.erethon.hephaestus.auctionhouse.SellOrder;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.tyche.TychePlugin;
import de.erethon.tyche.models.OwnerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

public class WebAuctionHouseServer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Hephaestus plugin;
    private final WebAccountService accountService;
    private final WebInventoryService inventoryService;
    private final ResourcePackIndexer resourcePackIndexer;
    private final Path staticRoot;
    private HttpServer server;

    public WebAuctionHouseServer(Hephaestus plugin, YamlConfiguration env) {
        this.plugin = plugin;
        this.accountService = Hecate.getInstance().getWebAccountService();
        this.inventoryService = Hecate.getInstance().getWebInventoryService();
        String resourcePackPath = env.getString("webResourcePackPath", "");
        this.resourcePackIndexer = new ResourcePackIndexer(resourcePackPath == null || resourcePackPath.isBlank() ? null : Path.of(resourcePackPath));
        String staticPath = env.getString("webStaticPath", new java.io.File(plugin.getDataFolder(), "web").getAbsolutePath());
        this.staticRoot = Path.of(staticPath);
    }

    public void start(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 64);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(6));
        server.start();
        plugin.getLogger().info("Web auction house listening on http://" + host + ":" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(2);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
                return;
            }
            if (path.startsWith("/resourcepack/")) {
                handleResourcePack(exchange, path);
                return;
            }
            handleStatic(exchange, path);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().warning("Web request failed: " + e.getMessage());
            e.printStackTrace();
            sendJson(exchange, 500, Map.of("error", "Internal server error"));
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        switch (path) {
            case "/api/auth/setup/start" -> requireMethod(exchange, "POST", () -> {
                JsonObject body = readJson(exchange);
                WebAccountService.SetupStartResult result = accountService.startSetup(
                        requiredString(body, "playerName"),
                        clientIp(exchange)
                ).join();
                sendJson(exchange, result.success() ? 200 : 404, result);
            });
            case "/api/auth/setup/finish" -> requireMethod(exchange, "POST", () -> {
                JsonObject body = readJson(exchange);
                WebAccountService.LoginResult result = accountService.finishSetup(
                        requiredString(body, "token"),
                        requiredString(body, "password").toCharArray()
                ).join();
                if (result.success()) {
                    setSessionCookie(exchange, result.sessionToken());
                }
                sendJson(exchange, result.success() ? 200 : 401, result);
            });
            case "/api/auth/login" -> requireMethod(exchange, "POST", () -> {
                JsonObject body = readJson(exchange);
                WebAccountService.LoginResult result = accountService.login(
                        requiredString(body, "playerName"),
                        requiredString(body, "password").toCharArray()
                ).join();
                if (result.success()) {
                    setSessionCookie(exchange, result.sessionToken());
                }
                sendJson(exchange, result.success() ? 200 : 401, result);
            });
            case "/api/auth/logout" -> requireAuth(exchange, playerId -> {
                accountService.logout(playerId).join();
                clearSessionCookie(exchange);
                sendJson(exchange, 200, Map.of("success", true));
            });
            case "/api/me" -> requireAuth(exchange, playerId -> sendJson(exchange, 200, Map.of("playerId", playerId.toString())));
            case "/api/market/items" -> requireMethod(exchange, "GET", () -> sendJson(exchange, 200, listMarketItems()));
            case "/api/market/orders" -> requireMethod(exchange, "GET", () -> {
                String itemId = query(exchange).get("itemId");
                if (itemId == null || itemId.isBlank()) {
                    throw new IllegalArgumentException("Missing itemId");
                }
                String upgrades = query(exchange).getOrDefault("upgrades", "");
                sendJson(exchange, 200, Map.of(
                        "sellOrders", plugin.getAuctionHouseManager().getSellOrders(itemId, upgrades).join().stream().map(this::sellOrderDto).toList(),
                        "buyOrders", plugin.getAuctionHouseManager().getBuyOrders(itemId, upgrades).join().stream().map(this::buyOrderDto).toList()
                ));
            });
            case "/api/orders" -> requireAuth(exchange, playerId -> sendJson(exchange, 200, Map.of(
                    "sellOrders", plugin.getAuctionHouseManager().getPlayerSellOrders(playerId).join().stream().map(this::sellOrderDto).toList(),
                    "buyOrders", plugin.getAuctionHouseManager().getPlayerBuyOrders(playerId).join().stream().map(this::buyOrderDto).toList()
            )));
            case "/api/collection" -> requireAuth(exchange, playerId -> sendJson(exchange, 200, Map.of(
                    "money", plugin.getAuctionHouseManager().getCollectableMoney(playerId).join(),
                    "items", plugin.getAuctionHouseManager().getCollectableItems(playerId).join().stream().map(item -> Map.of(
                            "id", item.id(),
                            "itemId", item.itemId(),
                            "upgrades", item.upgrades(),
                            "quantity", item.quantity(),
                            "createdAt", item.createdAt().toInstant().toString()
                    )).toList()
            )));
            case "/api/orders/cancel" -> requireAuth(exchange, playerId -> {
                JsonObject body = readJson(exchange);
                String type = requiredString(body, "type");
                long orderId = body.get("orderId").getAsLong();
                boolean success = switch (type) {
                    case "sell" -> plugin.getAuctionHouseManager().cancelSellOrder(playerId, orderId).join();
                    case "buy" -> plugin.getAuctionHouseManager().cancelBuyOrder(playerId, orderId).join();
                    default -> throw new IllegalArgumentException("Unknown order type");
                };
                sendJson(exchange, success ? 200 : 404, Map.of("success", success));
            });
            case "/api/market/instant-buy" -> requireAuth(exchange, playerId -> {
                JsonObject body = readJson(exchange);
                String itemId = requiredString(body, "itemId");
                String upgrades = body.has("upgrades") ? body.get("upgrades").getAsString() : "";
                int quantity = body.get("quantity").getAsInt();
                handleInstantBuy(exchange, playerId, itemId, upgrades, quantity);
            });
            case "/api/inventory" -> requireAuth(exchange, playerId -> sendJson(exchange, 200, inventoryDto(playerId)));
            case "/api/market/sell" -> requireAuth(exchange, playerId -> {
                JsonObject body = readJson(exchange);
                handleWebSell(exchange, playerId, body);
            });
            default -> sendJson(exchange, 404, Map.of("error", "Not found"));
        }
    }

    private void handleInstantBuy(HttpExchange exchange, UUID playerId, String itemId, String upgrades, int quantity) throws IOException {
        if (!Bukkit.getPluginManager().isPluginEnabled("Tyche")) {
            sendJson(exchange, 503, Map.of("success", false, "message", "Economy service is not available"));
            return;
        }
        Optional<SellOrder> order = plugin.getAuctionHouseDatabaseManager().getBestSellOrder(itemId, upgrades).join();
        if (order.isEmpty()) {
            sendJson(exchange, 404, Map.of("success", false, "message", "No sell orders available"));
            return;
        }
        long totalCost = (long) Math.min(quantity, order.get().quantity()) * order.get().pricePerUnit();
        long balance = TychePlugin.getEconomyService().getBalance(playerId, OwnerType.PLAYER, "herone").join();
        if (balance < totalCost) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Insufficient funds"));
            return;
        }
        TychePlugin.getEconomyService().withdraw(playerId, OwnerType.PLAYER, "herone", totalCost, "Web auction house purchase", playerId).join();
        var result = plugin.getAuctionHouseManager().instantBuy(playerId, itemId, upgrades, quantity).join();
        if (!result.success()) {
            TychePlugin.getEconomyService().deposit(playerId, OwnerType.PLAYER, "herone", totalCost, "Refund for failed web auction house purchase", playerId).join();
        }
        sendJson(exchange, result.success() ? 200 : 409, result);
    }

    private void handleWebSell(HttpExchange exchange, UUID playerId, JsonObject body) throws IOException {
        String source = requiredString(body, "source");
        int slot = body.get("slot").getAsInt();
        int quantity = body.get("quantity").getAsInt();
        int pricePerUnit = body.get("pricePerUnit").getAsInt();
        Optional<WebInventoryService.WebTakenItem> taken = switch (source) {
            case "bank" -> inventoryService.takeBankItem(playerId, slot, quantity).join();
            case "character" -> inventoryService.takeCharacterItem(playerId, UUID.fromString(requiredString(body, "characterId")), slot, quantity).join();
            default -> throw new IllegalArgumentException("Unknown inventory source");
        };
        if (taken.isEmpty()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Could not remove that item from the selected source"));
            return;
        }
        HItemStack hItemStack = createAuctionStack(taken.get());
        if (hItemStack == null) {
            sendJson(exchange, 400, Map.of("success", false, "message", "That item cannot be sold"));
            return;
        }
        var result = plugin.getAuctionHouseManager().createSellOrder(playerId, hItemStack, pricePerUnit).join();
        if (!result.success()) {
            plugin.getAuctionHouseDatabaseManager().addCollectableItem(
                    playerId,
                    hItemStack.getItem().getKey().toString(),
                    plugin.getAuctionHouseManager().serializeUpgrades(hItemStack),
                    plugin.getAuctionHouseManager().serializeItemStack(hItemStack),
                    taken.get().quantity()
            ).join();
        }
        sendJson(exchange, result.success() ? 200 : 409, result);
    }

    private HItemStack createAuctionStack(WebInventoryService.WebTakenItem taken) {
        if (taken.stack() != null) {
            return plugin.getLibrary().get(taken.stack());
        }
        HItem item = plugin.getLibrary().get(taken.itemId());
        if (item == null) {
            return null;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item.getBaseItem());
        stack.setCount(taken.quantity());
        if (taken.customData() != null && taken.customData().length > 0) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(taken.customData())) {
                CompoundTag customData = NbtIo.readCompressed(input, NbtAccounter.create(2 * 1024 * 1024));
                CustomData.set(DataComponents.CUSTOM_DATA, stack, customData);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to restore Hephaestus custom data for web-listed item " + taken.itemId() + ": " + e.getMessage());
            }
        }
        return new HItemStack(item, stack);
    }

    private List<Map<String, Object>> listMarketItems() {
        List<String> listedIds = plugin.getAuctionHouseManager().getDistinctListedItemIds().join();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String itemId : listedIds) {
            HItem item = plugin.getLibrary().get(itemId);
            if (item != null) {
                result.add(itemDto(item));
            }
        }
        return result;
    }

    private Map<String, Object> inventoryDto(UUID playerId) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("bank", inventoryService.listBankItems(playerId).join().stream().map(this::inventoryItemDto).toList());
        dto.put("currentCharacter", inventoryService.listCurrentCharacterItems(playerId).join().stream().map(this::inventoryItemDto).toList());
        dto.put("characterInventories", inventoryService.listCharacters(playerId).join().stream()
                .collect(java.util.stream.Collectors.toMap(
                        character -> character.characterId().toString(),
                        character -> inventoryService.listCharacterItems(playerId, character.characterId()).join().stream().map(this::inventoryItemDto).toList()
                )));
        dto.put("characters", inventoryService.listCharacters(playerId).join());
        dto.put("currentCharacterId", inventoryService.getCurrentCharacterId(playerId).map(UUID::toString).orElse(null));
        return dto;
    }

    private Map<String, Object> inventoryItemDto(WebInventoryService.WebInventoryItem item) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("source", item.source());
        dto.put("characterId", item.characterId() == null ? null : item.characterId().toString());
        dto.put("slot", item.slot());
        dto.put("quantity", item.quantity());
        HItemStack stack = item.stack() == null ? null : plugin.getLibrary().get(item.stack());
        HItem hItem = stack != null ? stack.getItem() : plugin.getLibrary().get(item.itemId());
        if (hItem != null) {
            dto.put("item", itemDto(hItem));
        } else {
            dto.put("item", Map.of("itemId", item.itemId(), "name", item.itemId()));
        }
        return dto;
    }

    private Map<String, Object> itemDto(HItem item) {
        Identifier modelKey = item.getEffectiveItemModel();
        Map<String, Object> dto = new HashMap<>();
        dto.put("itemId", item.getKey().toString());
        dto.put("name", item.getKey().toString());
        dto.put("baseItem", item.getBaseItemKey().toString());
        dto.put("modelKey", modelKey.toString());
        dto.put("iconUrl", resourcePackIndexer.resolveIconUrl(modelKey));
        dto.put("tags", item.getTags());
        return dto;
    }

    private Map<String, Object> sellOrderDto(SellOrder order) {
        return Map.of(
                "id", order.id(),
                "sellerUuid", order.sellerUuid().toString(),
                "itemId", order.itemId(),
                "upgrades", order.upgrades(),
                "quantity", order.quantity(),
                "pricePerUnit", order.pricePerUnit(),
                "createdAt", order.createdAt().toInstant().toString()
        );
    }

    private Map<String, Object> buyOrderDto(BuyOrder order) {
        return Map.of(
                "id", order.id(),
                "buyerUuid", order.buyerUuid().toString(),
                "itemId", order.itemId(),
                "upgrades", order.upgrades(),
                "quantity", order.quantity(),
                "pricePerUnit", order.pricePerUnit(),
                "createdAt", order.createdAt().toInstant().toString()
        );
    }

    private void handleResourcePack(HttpExchange exchange, String path) throws IOException {
        String relative = path.substring("/resourcepack/".length());
        Optional<Path> asset = resourcePackIndexer.resolveAssetPath(relative);
        if (asset.isEmpty()) {
            sendBytes(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        sendBytes(exchange, 200, contentType(asset.get()), Files.readAllBytes(asset.get()));
    }

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        String relative = path.equals("/") ? "index.html" : path.substring(1);
        Path target = staticRoot.resolve(relative).normalize();
        if (Files.isDirectory(target)) {
            target = target.resolve("index.html");
        }
        if (target.startsWith(staticRoot.normalize()) && Files.isRegularFile(target)) {
            sendBytes(exchange, 200, contentType(target), Files.readAllBytes(target));
            return;
        }

        byte[] packaged = readPackagedWebResource(relative);
        if (packaged != null) {
            sendBytes(exchange, 200, contentType(relative), packaged);
            return;
        }

        byte[] packagedIndex = readPackagedWebResource("index.html");
        if (packagedIndex != null) {
            sendBytes(exchange, 200, "text/html; charset=utf-8", packagedIndex);
            return;
        }

        sendBytes(exchange, 200, "text/html; charset=utf-8", fallbackHtml().getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readPackagedWebResource(String relative) throws IOException {
        if (relative == null || relative.contains("..")) {
            return null;
        }
        try (var input = plugin.getResource("web/" + relative.replace('\\', '/'))) {
            if (input == null) {
                return null;
            }
            return input.readAllBytes();
        }
    }

    private static String fallbackHtml() {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>Hephaestus Market</title></head>
                <body style="background:#101114;color:#f2f2f2;font-family:system-ui;margin:40px">
                <h1>Hephaestus Market</h1>
                <p>The web frontend has not been built or copied to the configured webStaticPath yet.</p>
                </body></html>
                """;
    }

    private void requireAuth(HttpExchange exchange, AuthenticatedHandler handler) throws IOException {
        Optional<UUID> playerId = accountService.authenticate(cookie(exchange, WebAccountService.SESSION_COOKIE)).join();
        if (playerId.isEmpty()) {
            sendJson(exchange, 401, Map.of("error", "Not authenticated"));
            return;
        }
        handler.handle(playerId.get());
    }

    private void requireMethod(HttpExchange exchange, String method, ThrowingRunnable runnable) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
            sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        runnable.run();
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return object.get(key).getAsString();
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String clientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String cookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals(name)) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private static void setSessionCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add("Set-Cookie", WebAccountService.SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=2592000");
    }

    private static void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie", WebAccountService.SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8", GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String contentType(Path path) {
        return contentType(path.getFileName().toString());
    }

    private static String contentType(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private interface ThrowingRunnable {
        void run() throws IOException;
    }

    private interface AuthenticatedHandler {
        void handle(UUID playerId) throws IOException;
    }
}
