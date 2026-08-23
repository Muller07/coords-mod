package com.example.cordswebhook;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CordsWebhookClient implements ClientModInitializer {
    private static final String MOD_ID = "cords-webhook";
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cords-webhook.properties");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ExecutorService WEBHOOK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cords-webhook-http");
        thread.setDaemon(true);
        return thread;
    });

    private static String webhookUrl = "";

    private static final KeyMapping SEND_COORDINATES = KeyMappingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.cords-webhook.send_coordinates",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    KeyMapping.Category.MISC
            )
    );

    @Override
    public void onInitializeClient() {
        loadConfig();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SEND_COORDINATES.consumeClick()) {
                sendCoordinates(client);
            }
        });
    }

    private static void loadConfig() {
        Properties properties = new Properties();

        try {
            if (Files.notExists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                properties.setProperty("webhookUrl", "");
                try (var writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                    properties.store(writer, "Cords Webhook configuration");
                }
            } else {
                try (var reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
        } catch (IOException e) {
            System.err.println("[Cords Webhook] Could not read config: " + e.getMessage());
        }

        webhookUrl = properties.getProperty("webhookUrl", "").trim();
    }

    private static void sendCoordinates(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        if (webhookUrl.isBlank()) {
            client.player.displayClientMessage(
                    Component.literal("§cCords Webhook: set webhookUrl in config/cords-webhook.properties"),
                    false
            );
            return;
        }

        URI webhook;
        try {
            webhook = URI.create(webhookUrl);
        } catch (IllegalArgumentException e) {
            client.player.displayClientMessage(
                    Component.literal("§cCords Webhook: invalid webhook URL"),
                    false
            );
            return;
        }

        if (!isAllowedDiscordWebhook(webhook)) {
            client.player.displayClientMessage(
                    Component.literal("§cCords Webhook: URL must be a Discord webhook URL"),
                    false
            );
            return;
        }

        final String username = client.player.getGameProfile().name();
        final String dimension = client.level.dimension().location().toString();
        final double x = client.player.getX();
        final double y = client.player.getY();
        final double z = client.player.getZ();
        final int blockX = client.player.blockPosition().getX();
        final int blockY = client.player.blockPosition().getY();
        final int blockZ = client.player.blockPosition().getZ();

        String content = "**Minecraft coordinates**\n"
                + "Player: `" + escapeDiscord(username) + "`\n"
                + "Dimension: `" + escapeDiscord(dimension) + "`\n"
                + "Position: `X " + format(x) + " | Y " + format(y) + " | Z " + format(z) + "`\n"
                + "Block: `" + blockX + " " + blockY + " " + blockZ + "`";

        String json = "{\"content\":\"" + escapeJson(content) + "\",\"allowed_mentions\":{\"parse\":[]}}";

        HttpRequest request = HttpRequest.newBuilder(webhook)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        WEBHOOK_EXECUTOR.execute(() -> {
            try {
                HttpResponse<Void> response = HTTP_CLIENT.send(
                        request,
                        HttpResponse.BodyHandlers.discarding()
                );

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.displayClientMessage(Component.literal("§aCoordinates sent to Discord"), false);
                        }
                    });
                } else {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.displayClientMessage(
                                    Component.literal("§cDiscord webhook failed (HTTP " + response.statusCode() + ")"),
                                    false
                            );
                        }
                    });
                }
            } catch (Exception e) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("§cCould not reach Discord webhook: " + e.getClass().getSimpleName()),
                                false
                        );
                    }
                });
            }
        });
    }

    private static boolean isAllowedDiscordWebhook(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase();
        return normalizedHost.equals("discord.com")
                || normalizedHost.equals("discordapp.com")
                || normalizedHost.endsWith(".discord.com")
                || normalizedHost.endsWith(".discordapp.com");
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String escapeDiscord(String value) {
        return value.replace("`", "'").replace("\\", "\\\\");
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
