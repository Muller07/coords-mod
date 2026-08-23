# Cords Webhook — Minecraft 1.21.11

A client-side Fabric mod that sends your current Minecraft coordinates to a Discord webhook when you press a configurable keybind.

## Requirements

- Minecraft Java Edition 1.21.11
- Fabric Loader 0.18.5 or newer
- Fabric API 0.141.6+1.21.11
- Java 21+

Minecraft 1.21.11 is the final obfuscated Minecraft release, so this project uses Fabric Loom with Mojang mappings.

## Setup

1. Build the mod with `gradle build` from the project folder (or open it in IntelliJ IDEA and use the Gradle tasks).
2. Put `build/libs/cords-webhook-1.0.0.jar` into your Minecraft `mods` folder.
3. Start Minecraft once with Fabric. The mod creates `config/cords-webhook.properties`.
4. Put your Discord webhook URL after `webhookUrl=`.
5. In Minecraft, open **Options → Controls → Key Binds** and find **Cords Webhook** / **Send coordinates to Discord**. The default key is **G**, and you can change it there.
6. Press the key in-game to send the coordinates.

## Discord message

The webhook receives the player name, dimension, precise XYZ position to three decimal places, and integer block coordinates.

## Security

The webhook URL is a secret credential. Do not publish it in source control or send it to other people. The mod only sends a request when you press the configured key; it does not continuously track or upload your position.
