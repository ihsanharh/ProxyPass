package com.ihsanharh.hiveutils.mods.utility;

import com.sun.net.httpserver.HttpServer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimatedTextureType;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationExpressionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceTintData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;

import com.ihsanharh.hiveutils.api.BaseMod;
import com.ihsanharh.hiveutils.api.ModResult;
import com.ihsanharh.hiveutils.core.CustomForm;
import com.ihsanharh.hiveutils.core.PlayerData;
import com.ihsanharh.hiveutils.utils.SimpleFormBuilder;
import com.ihsanharh.hiveutils.utils.TextPacketUtils;

@Log4j2
public class SkinStealer extends BaseMod {
    private static final int ITEMS_PER_PAGE = 25;
        
    private final File wardrobeFile = new File("wardrobe.bin");    
    private Map<String, SerializedSkin> skins = new ConcurrentHashMap<>();
    private Map<String, byte[]> headCache = new ConcurrentHashMap<>();
    private Map<String, SerializedSkin> savedWardrobe = new ConcurrentHashMap<>();

    private HttpServer imageServer;
    private int imageServerPort;
    private NgrokClient ngrokClient;
    private String activeTunnelUrl;

    @Override
    public void onInitialize() {
        loadWardrobeFromDisk();
        startHttpServer();
    }

    @Override
    public ModResult handleUpstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof CommandRequestPacket commandRequest) {
            String rawCommand = commandRequest.getCommand();

            if (rawCommand.startsWith("/skins")) {
                executeSkinsCommand(session, 0, "");
                return ModResult.DENY;
            }
        }

        return ModResult.PASS;
    }

    @Override
    public ModResult handleDownstream(BedrockPacket packet, ProxyPlayerSession session) {
        if (packet instanceof AvailableCommandsPacket availableCommandsPacket) {
            availableCommandsPacket.getCommands().add(buildSkinsCommandData());
            log.debug("[SkinStealer] Injected /skins command.");

            skins.clear();
            headCache.clear();
            
            return ModResult.MODIFIED;
        }

        if (packet instanceof PlayerListPacket playerListPacket) {
            if (playerListPacket.getAction() == PlayerListPacket.Action.ADD) {
                for (PlayerListPacket.Entry entry : playerListPacket.getEntries()) {
                    if (entry.getSkin() != null) {
                        String playerName = entry.getName();

                        if (playerName != null && !playerName.contains("{")) {
                            cacheSkinSafely(playerName, entry.getSkin());
                        }
                    }
                }
            }
        }

        if (packet instanceof PlayerSkinPacket skinPacket) {
            PlayerData player = context.getPlayerStore().getPlayer(skinPacket.getUuid().toString());
            
            if (player != null) {
                String name = player.getPlayerName();

                if (name != null && skinPacket.getSkin() != null) {
                    cacheSkinSafely(name, skinPacket.getSkin());
                }
            }
        }

        return ModResult.PASS;
    }

    private CommandData buildSkinsCommandData() {
        CommandOverloadData[] overloads = new CommandOverloadData[0];

        Set<CommandData.Flag> commandFlags = new HashSet<>();
        List<ChainedSubCommandData> subCommands = new ArrayList<>();

        return new CommandData("skins", "Equip/Save other players' skins", commandFlags, null, null, subCommands, overloads);
    }

    private void cacheSkinSafely(String playerName, SerializedSkin newSkin) {
        if (playerName == null || playerName.contains("{")) return;

        String targetName = playerName;
        int duplicateCounter = 1;

        while (skins.containsKey(targetName)) {
            SerializedSkin existingSkin = skins.get(targetName);
            boolean isSameId = existingSkin.getSkinId().equals(newSkin.getSkinId());

            boolean isSameImage = Arrays.equals(
                existingSkin.getSkinData().getImage(), 
                newSkin.getSkinData().getImage()
            );

            if (isSameId || isSameImage) {
                skins.put(targetName, newSkin);
                return; 
            }

            targetName = playerName + " (" + duplicateCounter + ")";
            duplicateCounter++;
        }

        skins.put(targetName, newSkin);

        final String finalName = targetName;
        CompletableFuture.runAsync(() -> {
            try {
                ImageData skinData = newSkin.getSkinData();
                byte[] headPng = extractHead(skinData.getWidth(), skinData.getHeight(), skinData.getImage());
                headCache.put(finalName, headPng);
            } catch (Exception e) {
                log.error("Failed to extract head for " + finalName, e);
            }
        });
    }

    private void equipSkin(ProxyPlayerSession session, SerializedSkin skin) {
        PlayerSkinPacket skinPacket = new PlayerSkinPacket();
        skinPacket.setUuid(session.getAuthData().getIdentity());
        skinPacket.setNewSkinName("new.new.skins");
        skinPacket.setOldSkinName("");
        skinPacket.setTrustedSkin(true);
        skinPacket.setSkin(skin);

        session.getDownstream().sendPacket(skinPacket);
    }

    private void executeSkinsCommand(ProxyPlayerSession session, int page, String filter) {
        List<Map.Entry<String, SerializedSkin>> skinList = skins.entrySet().stream()
            .filter(entry -> filter.isEmpty() || entry.getKey().toLowerCase().contains(filter.toLowerCase()))
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .toList();
        int totalItems = skinList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        String title = String.format("Stolen Skins (Page %d/%d)", page + 1, Math.max(totalPages, 1));
        String content = filter.isEmpty() ? "Total active skins: " + skins.size() + "\nSelect a player to equip/save:" : "§eSearching for: '" + filter + "' (" + totalItems + " found)";
        SimpleFormBuilder builder = new SimpleFormBuilder(title, content);

        builder.add(filter.isEmpty() ? "§9Search Player" : "§cClear Search", "ACTION_SEARCH");
        if (!savedWardrobe.isEmpty()) {
            builder.add("§6Saved Skins Wardrobe", "ACTION_WARDROBE");
        }

        populateSkinFormRows(builder, skinList.subList(startIndex, endIndex), "SKIN_");

        if (totalItems == 0) {
            builder.add("§7No skins found...", "EMPTY");
        }

        if (page > 0) builder.add("§c« Previous Page", "NAV_PREV");
        if (endIndex < totalItems) builder.add("§aNext Page »", "NAV_NEXT");

        final int currentPage = page;
        context.getFormManager().sendForm(session, builder.build(), response -> {
            String action = builder.parseAction(response);
            if (action == null) return;

            if (action.equals("ACTION_SEARCH")) {
                openSearchForm(session, filter.isEmpty(), false);
            } else if (action.equals("ACTION_WARDROBE")) {
                openSavedSkinsMenu(session, 0, filter);
            } else if (action.equals("NAV_PREV")) {
                executeSkinsCommand(session, currentPage - 1, filter);
            } else if (action.equals("NAV_NEXT")) {
                executeSkinsCommand(session, currentPage + 1, filter);
            } else if (action.startsWith("SKIN_")) {
                String targetPlayer = action.substring(5);
                openSkinSubMenu(session, targetPlayer, skins.get(targetPlayer), currentPage, filter, false);
            }
        }, false);
    }

    private void openSavedSkinsMenu(ProxyPlayerSession session, int page, String filter) {
        List<Map.Entry<String, SerializedSkin>> wardrobeList = savedWardrobe.entrySet().stream()
            .filter(entry -> filter.isEmpty() || entry.getKey().toLowerCase().contains(filter.toLowerCase()))
            .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
            .toList();
        int totalItems = wardrobeList.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        String title = String.format("Wardrobe (Page %d/%d)", page + 1, Math.max(totalPages, 1));
        String content = filter.isEmpty() ? "Total saved skins: " + savedWardrobe.size() : "§eSearching: '" + filter + "' (" + totalItems + " found)";
        SimpleFormBuilder builder = new SimpleFormBuilder(title, content);

        builder.add("§c« Back to Active Players", "BACK_TO_MAIN");
        builder.add(filter.isEmpty() ? "§9Search Wardrobe" : "§cClear Search", "WARDROBE_SEARCH");
        builder.add("§4Clear All Saved Wardrobe", "CLEAR_ALL_WARDROBE");

        populateSkinFormRows(builder, wardrobeList.subList(startIndex, endIndex), "WARDROBE_");

        if (totalItems == 0) {
            builder.add("§7No saved skins available.", "EMPTY");
        }

        if (page > 0) builder.add("§c« Previous Page", "NAV_PREV");
        if (endIndex < totalItems) builder.add("§aNext Page »", "NAV_NEXT");

        final int currentPage = page;
        context.getFormManager().sendForm(session, builder.build(), response -> {
            String action = builder.parseAction(response);
            if (action == null) return;

            if (action.equals("BACK_TO_MAIN")) {
                executeSkinsCommand(session, 0, "");
            } else if (action.equals("WARDROBE_SEARCH")) {
                openSearchForm(session, filter.isEmpty(), true);
            } else if (action.equals("CLEAR_ALL_WARDROBE")) {
                savedWardrobe.clear();
                saveWardrobeToDisk();
                TextPacketUtils.sendRawToClient(session, "§cAll saved skins have been deleted.");
                executeSkinsCommand(session, 0, "");
            } else if (action.equals("NAV_PREV")) {
                openSavedSkinsMenu(session, currentPage - 1, filter);
            } else if (action.equals("NAV_NEXT")) {
                openSavedSkinsMenu(session, currentPage + 1, filter);
            } else if (action.startsWith("WARDROBE_")) {
                String targetName = action.substring(9);
                openSkinSubMenu(session, targetName, savedWardrobe.get(targetName), currentPage, filter, true);
            }
        }, false);
    }

    private void populateSkinFormRows(SimpleFormBuilder builder, List<Map.Entry<String, SerializedSkin>> segment, String prefix) {
        for (Map.Entry<String, SerializedSkin> entry : segment) {
            String entryName = entry.getKey();
            
            if (!headCache.containsKey(entryName)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        byte[] headPng = extractHead(entry.getValue().getSkinData().getWidth(), entry.getValue().getSkinData().getHeight(), entry.getValue().getSkinData().getImage());
                        headCache.put(entryName, headPng);
                    } catch (Exception ignored) {}
                });
            }

            try {
                String encodedName = URLEncoder.encode(entryName, "UTF-8").replace("+", "%20");
                builder.add("§7" + entryName, prefix + entryName, activeTunnelUrl + "/skin/" + encodedName + ".png"); 
            } catch (Exception e) {
                builder.add("§7" + entryName, prefix + entryName);
            }
        }
    }

    private void openSearchForm(ProxyPlayerSession session, boolean shouldOpenInput, boolean isWardrobe) {
        if (!shouldOpenInput) {
            if (isWardrobe) openSavedSkinsMenu(session, 0, "");
            else executeSkinsCommand(session, 0, "");
            return;
        }

        CustomForm searchForm = new CustomForm(isWardrobe ? "Search Wardrobe" : "Search Skins");
        searchForm.addInput("Enter name parameter:", "e.g. steve", "");
        
        context.getFormManager().sendForm(session, searchForm, response -> {
            if (response == null) return;
            String filter = response.replace("[\"", "").replace("\"]", "");
            
            if (isWardrobe) openSavedSkinsMenu(session, 0, filter);
            else executeSkinsCommand(session, 0, filter);
        }, false);
    }

    private void openSkinSubMenu(ProxyPlayerSession session, String playerName, SerializedSkin skin, int parentPage, String filter, boolean isWardrobe) {
        if (skin == null) {
            if (isWardrobe) openSavedSkinsMenu(session, parentPage, filter);
            else executeSkinsCommand(session, parentPage, filter);

            return;
        }

        SimpleFormBuilder builder = new SimpleFormBuilder("Manage: " + playerName, "Select action configuration:");
        
        builder.add("§aEquip Skin", "EQUIP");
        
        if (isWardrobe) {
            builder.add("§cDelete from Wardrobe", "DELETE");
        } else {
            builder.add("§bSave to File", "SAVE");
        }

        builder.add("§8« Back", "BACK");

        context.getFormManager().sendForm(session, builder.build(), response -> {
            String action = builder.parseAction(response);
            if (action == null) return;

            switch (action) {
                case "EQUIP" -> {
                    equipSkin(session, skin);
                    TextPacketUtils.sendRawToClient(session, "§aEquipped profile appearance: " + playerName);
                }
                case "SAVE" -> {
                    savedWardrobe.put(playerName, skin);
                    saveWardrobeToDisk();
                    TextPacketUtils.sendRawToClient(session, "§bSaved " + playerName + " to Wardrobe configuration target.");
                }
                case "DELETE" -> {
                    savedWardrobe.remove(playerName);
                    saveWardrobeToDisk();
                    TextPacketUtils.sendRawToClient(session, "§cDeleted skin entry: " + playerName);
                    openSavedSkinsMenu(session, parentPage, filter); 
                }
                case "BACK" -> {
                    if (isWardrobe) openSavedSkinsMenu(session, parentPage, filter);
                    else executeSkinsCommand(session, parentPage, filter);
                }
            }
        }, false);
    }

    private void startHttpServer() {
        try {
            imageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            imageServerPort = imageServer.getAddress().getPort();

            imageServer.createContext("/skin/", exchange -> {
                try {
                    String uri = exchange.getRequestURI().toString();
                    String fileName = uri.substring(uri.lastIndexOf("/") + 1).replace(".png", "");
                    String playerName = URLDecoder.decode(fileName, "UTF-8");
                
                    byte[] pngBytes = headCache.get(playerName);
                
                    if (pngBytes != null) {
                        exchange.getResponseHeaders().set("Content-Type", "image/png");
                        exchange.sendResponseHeaders(200, pngBytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(pngBytes);
                        }
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, -1);
                }
            });

            imageServer.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor()); 
            imageServer.start();
            log.info("[SkinStealer] Local HTTP Image Server started on port {}", imageServerPort);
            startNgrokTunnel(imageServerPort);
        } catch (Exception e) {
            log.error("Failed to start HTTP server", e);
        }
    }

    private void startNgrokTunnel(int port) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("[SkinStealer] Initializing java-ngrok...");

                ngrokClient = new NgrokClient.Builder().build();
                CreateTunnel createTunnel = new CreateTunnel.Builder()
                    .withAddr(port)
                    .build();

                Tunnel tunnel = ngrokClient.connect(createTunnel);
                activeTunnelUrl = tunnel.getPublicUrl();

                log.info("[SkinStealer] Success! Ngrok tunnel established at: {}", activeTunnelUrl);
            } catch (Exception e) {
                log.error("Failed to start ngrok via java-ngrok", e);
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (ngrokClient != null) {
                log.info("[SkinStealer] Terminating background Ngrok process...");
                ngrokClient.kill(); 
            }
        }));
    }

    private byte[] extractHead(int width, int height, byte[] rgbaData) throws Exception {
        BufferedImage skinImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        
        int dataIndex = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (rgbaData[dataIndex] & 0xFF) << 16;
                int g = (rgbaData[dataIndex + 1] & 0xFF) << 8;
                int b = (rgbaData[dataIndex + 2] & 0xFF);
                int a = (rgbaData[dataIndex + 3] & 0xFF) << 24;
                skinImage.setRGB(x, y, a | r | g | b);
                dataIndex += 4;
            }
        }

        int scale = width / 64;
        BufferedImage baseHead = skinImage.getSubimage(8 * scale, 8 * scale, 8 * scale, 8 * scale);
        BufferedImage hatLayer = skinImage.getSubimage(40 * scale, 8 * scale, 8 * scale, 8 * scale);

        BufferedImage finalHead = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = finalHead.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(baseHead, 0, 0, 64, 64, null);
        g2d.drawImage(hatLayer, 0, 0, 64, 64, null);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(finalHead, "png", baos);
        return baos.toByteArray();
    }

    private void loadWardrobeFromDisk() {
        if (!wardrobeFile.exists()) return;

        try (DataInputStream in = new DataInputStream(new FileInputStream(wardrobeFile))) {
            int skinCount = in.readInt();
            
            for (int i = 0; i < skinCount; i++) {
                String playerName = in.readUTF();
                
                // 1. Primitive Strings and Ids
                String skinId = in.readUTF();
                String playFabId = in.readUTF();
                String geometryName = in.readUTF();
                String skinResourcePatch = in.readUTF();
                
                // 2. Main Skin Data Payload
                int skinWidth = in.readInt();
                int skinHeight = in.readInt();
                int skinLen = in.readInt();
                byte[] skinBytes = new byte[skinLen];

                in.readFully(skinBytes);
                ImageData skinData = ImageData.of(skinWidth, skinHeight, skinBytes);
                
                // 3. Nested Object Array: List<AnimationData>
                int animationCount = in.readInt();
                List<AnimationData> animations = new ArrayList<>(animationCount);
                for (int j = 0; j < animationCount; j++) {
                    int animW = in.readInt();
                    int animH = in.readInt();
                    int animLen = in.readInt();
                    byte[] animBytes = new byte[animLen];

                    in.readFully(animBytes);
                    ImageData animImg = ImageData.of(animW, animH, animBytes);
                
                    // Read the Enum ordinals and convert them back to their Enum instances
                    int textureOrdinal = in.readInt();
                    float animFrames = in.readFloat();
                    int expressionOrdinal = in.readInt();
                
                    AnimatedTextureType textureType = AnimatedTextureType.values()[textureOrdinal];
                    AnimationExpressionType expressionType = AnimationExpressionType.values()[expressionOrdinal];
                
                    animations.add(new AnimationData(animImg, textureType, animFrames, expressionType));
                }
                
                // 4. Cape Data Payload
                ImageData capeData = ImageData.EMPTY;
                if (in.readBoolean()) {
                    int capeW = in.readInt();
                    int capeH = in.readInt();
                    int capeLen = in.readInt();
                    byte[] capeBytes = new byte[capeLen];

                    in.readFully(capeBytes);
                    
                    capeData = ImageData.of(capeW, capeH, capeBytes); 
                }
                
                // 5. Advanced Geometry Strings
                String geometryData = in.readUTF();
                String geometryDataEngineVersion = in.readUTF();
                String animationData = in.readUTF();
                
                // 6. Network/State Booleans
                boolean premium = in.readBoolean();
                boolean persona = in.readBoolean();
                boolean capeOnClassic = in.readBoolean();
                boolean primaryUser = in.readBoolean();
                
                // 7. Styling Strings
                String capeId = in.readUTF();
                String fullSkinId = in.readUTF();
                String armSize = in.readUTF();
                String skinColor = in.readUTF();
                
                // 8. Nested Persona Pieces
                int pieceCount = in.readInt();
                List<PersonaPieceData> personaPieces = new ArrayList<>(pieceCount);
                for (int j = 0; j < pieceCount; j++) {
                    String id = in.readUTF();
                    String type = in.readUTF();
                    String packId = in.readUTF();
                    boolean isDefault = in.readBoolean();
                    String productId = in.readUTF();

                    personaPieces.add(new PersonaPieceData(id, type, packId, isDefault, productId));
                }
            
                // 9. Nested Persona Piece Tints
                int tintCount = in.readInt();
                List<PersonaPieceTintData> tintColors = new ArrayList<>(tintCount);
                for (int j = 0; j < tintCount; j++) {
                    String type = in.readUTF();
                    int colorCount = in.readInt();
                    List<String> colors = new ArrayList<>(colorCount);

                    for (int k = 0; k < colorCount; k++) {
                        colors.add(in.readUTF());
                    }

                    tintColors.add(new PersonaPieceTintData(type, colors));
                }
                
                boolean overridingPlayerAppearance = in.readBoolean();
            
                // Reassemble the true state using Cloudburst's Builder pattern
                SerializedSkin rebuiltSkin = SerializedSkin.builder()
                    .skinId(skinId)
                    .playFabId(playFabId)
                    .geometryName(geometryName.isEmpty() ? null : geometryName)
                    .skinResourcePatch(skinResourcePatch.isEmpty() ? null : skinResourcePatch)
                    .skinData(skinData)
                    .animations(animations)
                    .capeData(capeData)
                    .geometryData(geometryData)
                    .geometryDataEngineVersion(geometryDataEngineVersion)
                    .animationData(animationData)
                    .premium(premium)
                    .persona(persona)
                    .capeOnClassic(capeOnClassic)
                    .primaryUser(primaryUser)
                    .capeId(capeId)
                    .fullSkinId(fullSkinId)
                    .armSize(armSize)
                    .skinColor(skinColor)
                    .personaPieces(personaPieces)
                    .tintColors(tintColors)
                    .overridingPlayerAppearance(overridingPlayerAppearance)
                    .build();
            
                savedWardrobe.put(playerName, rebuiltSkin);
            }
            log.info("[SkinStealer] Fully loaded {} complex SerializedSkins from wardrobe.bin", savedWardrobe.size());
        } catch (Exception e) {
            log.error("Failed to translate wardrobe binary file structure", e);
        }
    }
    
    private void saveWardrobeToDisk() {
        CompletableFuture.runAsync(() -> {
            File tempFile = new File(wardrobeFile.getAbsolutePath() + ".tmp");
            
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tempFile))) {
                out.writeInt(savedWardrobe.size());
                
                for (Map.Entry<String, SerializedSkin> entry : savedWardrobe.entrySet()) {
                    SerializedSkin skin = entry.getValue();
                    
                    out.writeUTF(entry.getKey());
                    
                    // 1. Strings
                    out.writeUTF(skin.getSkinId() == null ? "" : skin.getSkinId());
                    out.writeUTF(skin.getPlayFabId() == null ? "" : skin.getPlayFabId());
                    out.writeUTF(skin.getGeometryName() == null ? "" : skin.getGeometryName());
                    out.writeUTF(skin.getSkinResourcePatch() == null ? "" : skin.getSkinResourcePatch());
                    
                    // 2. Primary Texture Payload
                    ImageData skinData = skin.getSkinData();
                    out.writeInt(skinData.getWidth());
                    out.writeInt(skinData.getHeight());
                    out.writeInt(skinData.getImage().length);
                    out.write(skinData.getImage());
                    
                    // 3. Animations List
                    List<AnimationData> animations = skin.getAnimations();
                    out.writeInt(animations == null ? 0 : animations.size());
                    if (animations != null) {
                        for (AnimationData anim : animations) {
                            ImageData animImg = anim.getImage();
                            out.writeInt(animImg.getWidth());
                            out.writeInt(animImg.getHeight());
                            out.writeInt(animImg.getImage().length);
                            out.write(animImg.getImage());
                        
                            out.writeInt(anim.getTextureType().ordinal());
                            out.writeFloat(anim.getFrames());
                            out.writeInt(anim.getExpressionType().ordinal());
                        }
                    }
                    
                    // 4. Cape Payload Block
                    ImageData capeData = skin.getCapeData();
                    if (capeData != null && capeData.getImage() != null && capeData.getImage().length > 0) {
                        out.writeBoolean(true);
                        out.writeInt(capeData.getWidth());
                        out.writeInt(capeData.getHeight());
                        out.writeInt(capeData.getImage().length);
                        out.write(capeData.getImage());
                    } else {
                        out.writeBoolean(false);
                    }
                    
                    // 5. Geometry Elements
                    out.writeUTF(skin.getGeometryData() == null ? "" : skin.getGeometryData());
                    out.writeUTF(skin.getGeometryDataEngineVersion() == null ? "" : skin.getGeometryDataEngineVersion());
                    out.writeUTF(skin.getAnimationData() == null ? "" : skin.getAnimationData());
                    
                    // 6. Flags
                    out.writeBoolean(skin.isPremium());
                    out.writeBoolean(skin.isPersona());
                    out.writeBoolean(skin.isCapeOnClassic());
                    out.writeBoolean(skin.isPrimaryUser());
                    
                    // 7. Styling Information
                    out.writeUTF(skin.getCapeId() == null ? "" : skin.getCapeId());
                    out.writeUTF(skin.getFullSkinId() == null ? "" : skin.getFullSkinId());
                    out.writeUTF(skin.getArmSize() == null ? "wide" : skin.getArmSize());
                    out.writeUTF(skin.getSkinColor() == null ? "#0" : skin.getSkinColor());
                    
                    // 8. Persona Pieces Array 
                    List<PersonaPieceData> pieces = skin.getPersonaPieces();
                    out.writeInt(pieces == null ? 0 : pieces.size());
                    if (pieces != null) {
                        for (PersonaPieceData p : pieces) {
                            out.writeUTF(p.getId() == null ? "" : p.getId());
                            out.writeUTF(p.getType() == null ? "" : p.getType());
                            out.writeUTF(p.getPackId() == null ? "" : p.getPackId());
                            out.writeBoolean(p.isDefault());
                            out.writeUTF(p.getProductId() == null ? "" : p.getProductId());
                        }
                    }
                    
                    // 9. Tint Configuration
                    List<PersonaPieceTintData> tints = skin.getTintColors();
                    out.writeInt(tints == null ? 0 : tints.size());
                    if (tints != null) {
                        for (PersonaPieceTintData t : tints) {
                            out.writeUTF(t.getType() == null ? "" : t.getType());
                            
                            List<String> colors = t.getColors();
                            out.writeInt(colors == null ? 0 : colors.size());
                            if (colors != null) {
                                for (String c : colors) {
                                    out.writeUTF(c == null ? "" : c);
                                }
                            }
                        }
                    }
                    
                    out.writeBoolean(skin.isOverridingPlayerAppearance());
                }
            } catch (Exception e) {
                log.error("Failed to write to temporary wardrobe binary file", e);
                
                return;
            }

            try {
                Files.move(tempFile.toPath(), wardrobeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.error("Failed to swap temporary wardrobe file", e);
            }
        });
    } 
}
