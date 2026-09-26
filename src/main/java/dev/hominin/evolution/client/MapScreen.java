package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.network.MapActionPayload;
import dev.hominin.evolution.network.MapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The mental map. On the left, the country you have walked through, your ground in orange and every
 * other band's you know of in red, with everyone and everything you know of marked on it; drag to look
 * around, scroll to zoom, click a marker to see it and follow it. On the right, the places you are
 * holding in mind: follow one, let one go, remember where you stand (a name is optional), or ask the
 * band what they remember.
 */
public class MapScreen extends Screen {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "mental_map");
    private static final int PANEL = 158;
    private static final int[] ZOOMS = {1, 2, 4, 8};
    private static final int UNKNOWN = 0xFFD9CCAA;

    private final MapPayload map;
    private int zoom = 1;
    private int centreX;
    private int centreZ;
    private int size;
    private int mapLeft;
    private int mapTop;
    @Nullable
    private DynamicTexture texture;
    private boolean stale = true;
    @Nullable
    private MapPayload.Marker selected;
    private EditBox name;
    /** Packing up throws away the presence you built: the button asks once. */
    private boolean packArmed;
    /** How many of the places held in mind fit in the panel, and where the chosen marker's name goes. */
    private int maxRows = Integer.MAX_VALUE;
    private int labelY;

    private MapScreen(MapPayload map) {
        super(Component.literal("Mental map"));
        this.map = map;
        this.centreX = map.x();
        this.centreZ = map.z();
    }

    public static void open(MapPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MapScreen old) {
            MapScreen next = new MapScreen(payload);
            next.zoom = old.zoom;
            next.centreX = old.centreX;
            next.centreZ = old.centreZ;
            mc.setScreen(next);
            return;
        }
        mc.setScreen(new MapScreen(payload));
    }

    @Override
    protected void init() {
        size = Math.max(96, Math.min(width - PANEL - 24, height - 40));
        mapLeft = 10;
        mapTop = 24;
        int px = mapLeft + size + 8;
        // Laid out from the bottom up - Done, the chosen marker and its Follow, then the controls - and the places held
        // in mind take whatever room is left above them, so nothing is ever drawn on top of anything else.
        boolean following = map.markers().stream().anyMatch(m -> m.kind() == MapPayload.WAYPOINT);
        boolean chosenShown = selected != null;
        int doneY = height - 28;
        int followY = doneY - 22;
        labelY = (chosenShown && !selected.target().isEmpty() ? followY : doneY) - 12;
        int controls = 20 + 22 + 22 + 22 + (following ? 22 : 0);
        int controlsTop = (chosenShown ? labelY - 4 : labelY + 8) - controls;
        int listTop = mapTop + 58;
        maxRows = Math.max(1, (controlsTop - 8 - listTop) / 14);
        int y = listTop;
        int row = 0;
        for (MapPayload.Marker marker : map.markers()) {
            if (marker.memory() < 0) {
                continue;
            }
            if (row >= maxRows) {
                break;
            }
            int index = marker.memory();
            int rowY = y + row * 14;
            addRenderableWidget(Button.builder(Component.literal(">"), b -> follow(marker))
                    .bounds(px + PANEL - 32, rowY - 2, 14, 12).build());
            addRenderableWidget(Button.builder(Component.literal("x"), b -> PacketDistributor.sendToServer(
                    new MapActionPayload(MapActionPayload.FORGET, index, ""))).bounds(px + PANEL - 16, rowY - 2, 14, 12).build());
            row++;
        }
        y = Math.max(y + Math.max(1, row) * 14 + 8, controlsTop);
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, px, y, PANEL - 10, 16, Component.literal("A name for this place"));
        name.setMaxLength(32);
        name.setValue(typed);
        name.setHint(Component.literal("name it (optional)"));
        addRenderableWidget(name);
        y += 20;
        addRenderableWidget(Button.builder(Component.literal("Remember this place"), b -> PacketDistributor.sendToServer(
                new MapActionPayload(MapActionPayload.REMEMBER, -1, name.getValue()))).bounds(px, y, PANEL - 10, 18).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Ask the band what they know"), b -> PacketDistributor.sendToServer(
                new MapActionPayload(MapActionPayload.ASK, -1, ""))).bounds(px, y, PANEL - 10, 18).build());
        y += 22;
        // Your ground: pack it up and go, or - packed up - make where you stand your ground.
        if (map.settled()) {
            addRenderableWidget(Button.builder(Component.literal(packArmed ? "Pack up? Click again" : "Pack up"), b -> {
                if (!packArmed) {
                    packArmed = true;
                    rebuildWidgets();
                    return;
                }
                packArmed = false;
                PacketDistributor.sendToServer(new MapActionPayload(MapActionPayload.PACK_UP, -1, ""));
            }).bounds(px, y, PANEL - 10, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Set territory here"), b -> PacketDistributor.sendToServer(
                    new MapActionPayload(MapActionPayload.SETTLE, -1, ""))).bounds(px, y, PANEL - 10, 18).build());
        }
        y += 22;
        if (following) {
            addRenderableWidget(Button.builder(Component.literal("Stop following"), b -> {
                PacketDistributor.sendToServer(new MapActionPayload(MapActionPayload.STOP, -1, ""));
                WaypointHud.clear();
            }).bounds(px, y, PANEL - 10, 18).build());
            y += 22;
        }
        if (selected != null && !selected.target().isEmpty()) {
            // In with the other controls, never on top of them - a button underneath another never gets the click.
            MapPayload.Marker chosen = selected;
            addRenderableWidget(Button.builder(Component.literal("Follow: " + trim(chosen.label(), PANEL - 60)),
                    b -> follow(chosen)).bounds(px, followY, PANEL - 10, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("-"), b -> setZoom(1)).bounds(mapLeft, mapTop + size + 2, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setZoom(-1)).bounds(mapLeft + 18, mapTop + size + 2, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Here"), b -> {
            centreX = map.x();
            centreZ = map.z();
            stale = true;
        }).bounds(mapLeft + 36, mapTop + size + 2, 34, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(px, height - 28, PANEL - 10, 18).build());
    }

    private long lastClick;

    private void follow(MapPayload.Marker marker) {
        if (!marker.target().isEmpty()) {
            PacketDistributor.sendToServer(new MapActionPayload(MapActionPayload.LEAD, marker.memory(), marker.target()));
            onClose();
        }
    }

    private void setZoom(int step) {
        int at = 0;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (ZOOMS[i] == zoom) {
                at = i;
            }
        }
        zoom = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, at + step))];
        stale = true;
    }

    // ------------------------------------------------------------ the ground

    private void rebuild() {
        if (texture == null || texture.getPixels() == null || texture.getPixels().getWidth() != size) {
            if (texture != null) {
                texture.close();
            }
            texture = new DynamicTexture(size, size, false);
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        int half = size / 2;
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int bx = centreX + (px - half) * zoom;
                int bz = centreZ + (py - half) * zoom;
                int colour = ClientMapCache.colourAt(bx, bz);
                if (colour == -1) {
                    // Country you have never seen: blank, with a faint weave so it reads as unknown.
                    colour = ((bx >> 2) + (bz >> 2) & 3) == 0 ? 0xFFCFC19C : UNKNOWN;
                }
                for (MapPayload.Ground ground : map.grounds()) {
                    double dx = bx - ground.x();
                    double dz = bz - ground.z();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > ground.radius() + zoom) {
                        continue;
                    }
                    int tint = ground.kind() == 0 ? 0xFF8C1A : ground.kind() == 2 ? 0x8B5A2B : 0xDC2828;
                    boolean edge = distance > ground.radius() - zoom * 1.5D;
                    colour = blend(colour, tint, edge ? 0.85F : 0.22F);
                }
                image.setPixelRGBA(px, py, abgr(colour));
            }
        }
        texture.upload();
        stale = false;
    }

    private static int blend(int argb, int rgb, float amount) {
        int r = (int) (((argb >> 16) & 255) * (1 - amount) + ((rgb >> 16) & 255) * amount);
        int g = (int) (((argb >> 8) & 255) * (1 - amount) + ((rgb >> 8) & 255) * amount);
        int b = (int) ((argb & 255) * (1 - amount) + (rgb & 255) * amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF;
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (stale) {
            rebuild();
        }
        graphics.drawString(font, "Mental map - " + zoom + (zoom == 1 ? " block" : " blocks") + " a pixel", mapLeft, 10, 0xE9D8A6);
        graphics.fill(mapLeft - 1, mapTop - 1, mapLeft + size + 1, mapTop + size + 1, 0xFF3A3024);
        graphics.blit(TEXTURE, mapLeft, mapTop, 0, 0, size, size, size, size);
        MapPayload.Marker hovered = null;
        for (MapPayload.Marker marker : map.markers()) {
            int[] at = screenOf(marker.x(), marker.z());
            if (at == null) {
                continue;
            }
            drawMarker(graphics, marker, at[0], at[1]);
            if (Math.abs(mouseX - at[0]) <= 3 && Math.abs(mouseY - at[1]) <= 3) {
                hovered = marker;
            }
        }
        // You: a white point with a dark rim.
        int[] you = screenOf(map.x(), map.z());
        if (you != null) {
            graphics.fill(you[0] - 2, you[1] - 2, you[0] + 3, you[1] + 3, 0xFF000000);
            graphics.fill(you[0] - 1, you[1] - 1, you[0] + 2, you[1] + 2, 0xFFFFFFFF);
        }
        renderPanel(graphics);
        if (hovered != null) {
            graphics.renderTooltip(font, Component.literal(hovered.label()), mouseX, mouseY);
        } else if (mouseX >= mapLeft && mouseX < mapLeft + size && mouseY >= mapTop && mouseY < mapTop + size) {
            // Over somebody's ground: whose, and how strong they are there.
            int bx = centreX + (mouseX - mapLeft - size / 2) * zoom;
            int bz = centreZ + (mouseY - mapTop - size / 2) * zoom;
            for (MapPayload.Ground ground : map.grounds()) {
                double dx = bx - ground.x();
                double dz = bz - ground.z();
                if (dx * dx + dz * dz <= (double) ground.radius() * ground.radius()) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(ground.label()).withStyle(ground.kind() == 0
                            ? net.minecraft.ChatFormatting.GOLD : ground.kind() == 2 ? net.minecraft.ChatFormatting.DARK_GREEN
                            : net.minecraft.ChatFormatting.RED));
                    for (String line : ground.info().split("\\|")) {
                        lines.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                    graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Nullable
    private int[] screenOf(int x, int z) {
        int sx = mapLeft + size / 2 + (x - centreX) / zoom;
        int sy = mapTop + size / 2 + (z - centreZ) / zoom;
        if (sx < mapLeft + 2 || sx > mapLeft + size - 3 || sy < mapTop + 2 || sy > mapTop + size - 3) {
            return null;
        }
        return new int[] {sx, sy};
    }

    private void drawMarker(GuiGraphics graphics, MapPayload.Marker marker, int x, int y) {
        boolean chosenOne = selected != null && selected.x() == marker.x() && selected.z() == marker.z()
                && selected.kind() == marker.kind();
        if (marker.kind() >= MapPayload.PLACE) {
            // A place the band knows: a diamond in the colour of what it is.
            var kinds = dev.hominin.evolution.world.Pois.Kind.values();
            int colour = kinds[Math.min(kinds.length - 1, marker.kind() - MapPayload.PLACE)].colour;
            diamond(graphics, x, y, 4, chosenOne ? 0xFFFFFFFF : 0xFF1A1410);
            diamond(graphics, x, y, 3, colour);
            return;
        }
        if (marker.kind() == MapPayload.STRUCTURE) {
            // Something you built: a little house - pale while it is only marked out.
            boolean planned = marker.label().contains("marked out");
            int wall = chosenOne ? 0xFFFFFFFF : planned ? 0xFF8FA8B8 : 0xFFD8B070;
            graphics.fill(x - 3, y - 1, x + 4, y + 4, 0xFF1A1410);
            graphics.fill(x - 2, y, x + 3, y + 3, wall);
            for (int i = 0; i < 4; i++) {
                graphics.fill(x - i, y - 4 + i, x + i + 1, y - 3 + i, planned ? 0xFF6A7A88 : 0xFF8A5A30);
            }
            return;
        }
        if (marker.kind() == MapPayload.TOOL_STORE) {
            // Where the band's tools are: a stone-grey square in a ring that pulses gold.
            boolean bright = (net.minecraft.Util.getMillis() / 500L) % 2L == 0L;
            graphics.fill(x - 4, y - 4, x + 5, y + 5, chosenOne ? 0xFFFFFFFF : bright ? 0xFFFFD040 : 0xFFB08020);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF1A1410);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFA8A8B8);
            return;
        }
        int colour = switch (marker.kind()) {
            case MapPayload.BAND -> 0xFFE03030;
            case MapPayload.PARANTHROPUS -> 0xFF6A4A8A;
            case MapPayload.TROOP -> 0xFF40B040;
            case MapPayload.DEPOSIT -> 0xFF9A9AA8;
            case MapPayload.LAVA -> 0xFFFF7A10;
            case MapPayload.TERMITES -> 0xFFB06030;
            case MapPayload.CLAN -> 0xFFD8C040;
            case MapPayload.WATER -> 0xFF3070E0;
            case MapPayload.TOLD -> 0xFF60C0C0;
            case MapPayload.CAMP -> 0xFFFF9020;
            case MapPayload.WAYPOINT -> 0xFF7CFF7C;
            case MapPayload.PLAYER -> 0xFF40D8FF;
            default -> 0xFFFFFFFF;
        };
        boolean chosen = selected != null && selected.x() == marker.x() && selected.z() == marker.z()
                && selected.kind() == marker.kind();
        int r = marker.kind() == MapPayload.BAND || marker.kind() == MapPayload.CAMP || marker.kind() == MapPayload.PLAYER
                ? 3 : 2;
        graphics.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, chosen ? 0xFFFFFFFF : 0xFF1A1410);
        graphics.fill(x - r, y - r, x + r + 1, y + r + 1, colour);
        if (marker.kind() == MapPayload.WAYPOINT) {
            graphics.fill(x - 1, y - 6, x + 2, y - r - 1, colour);
        }
    }

    private static void diamond(GuiGraphics graphics, int x, int y, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int w = r - Math.abs(dy);
            graphics.fill(x - w, y + dy, x + w + 1, y + dy + 1, colour);
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        int px = mapLeft + size + 8;
        int y = mapTop;
        String own = map.ownName().isEmpty() ? "Your band" : Character.toUpperCase(map.ownName().charAt(0)) + map.ownName().substring(1);
        graphics.drawString(font, trim(own, PANEL - 8), px, y, 0xFF9020);
        y += 11;
        graphics.drawString(font, map.settled() ? "Presence here: " + map.presence() + "/20" : "Packed up - no ground",
                px, y, map.settled() ? 0xBBBBBB : 0xE0A040);
        y += 11;
        long told = map.markers().stream().filter(m -> m.kind() == MapPayload.TOLD).count();
        graphics.drawString(font, "Told today: " + told, px, y, 0x60C0C0);
        y += 11;
        long places = map.markers().stream().filter(m -> m.kind() >= MapPayload.PLACE).count();
        graphics.drawString(font, "The band knows: " + places + (places == 1 ? " place" : " places"), px, y, 0x9FD8FF);
        y += 16;
        long held = map.markers().stream().filter(m -> m.memory() >= 0).count();
        graphics.drawString(font, "Places held in mind: " + held + "/" + map.slots(), px, y, 0xE9D8A6);
        y = mapTop + 58;
        int row = 0;
        int hidden = 0;
        for (MapPayload.Marker marker : map.markers()) {
            if (marker.memory() < 0) {
                continue;
            }
            if (row >= maxRows) {
                hidden++;
                continue;
            }
            graphics.drawString(font, trim(marker.label(), PANEL - 40), px, y + row * 14, 0xDDDDDD);
            row++;
        }
        if (row == 0) {
            graphics.drawString(font, "- nothing yet -", px, y, 0x8C8578);
        } else if (hidden > 0) {
            graphics.drawString(font, "+" + hidden + " more (make the window bigger)", px, y + row * 14, 0x8C8578);
        }
        if (selected != null) {
            graphics.drawString(font, trim(selected.label(), PANEL - 8), px, labelY, 0xFFFFFF);
        }
        graphics.drawString(font, "Orange: yours. Red: bands'. Brown: chimps'. Diamonds: places.", mapLeft + 74,
                mapTop + size + 5, 0x8C8578);
    }

    private String trim(String text, int widthPx) {
        return font.width(text) > widthPx ? font.plainSubstrByWidth(text, widthPx - 6) + "..." : text;
    }

    // ------------------------------------------------------------ looking around

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= mapLeft && mouseX < mapLeft + size && mouseY >= mapTop && mouseY < mapTop + size) {
            MapPayload.Marker best = null;
            double bestDistance = 36.0D;
            for (MapPayload.Marker marker : map.markers()) {
                int[] at = screenOf(marker.x(), marker.z());
                // Only what can be followed: the pointer to where you are already going is not a place.
                if (at == null || marker.target().isEmpty()) {
                    continue;
                }
                double d = (mouseX - at[0]) * (mouseX - at[0]) + (mouseY - at[1]) * (mouseY - at[1]);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = marker;
                }
            }
            if (best != null) {
                long now = net.minecraft.Util.getMillis();
                if (best.equals(selected) && now - lastClick < 400L) {
                    // Double-click: follow it.
                    follow(best);
                    return true;
                }
                lastClick = now;
                selected = best;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mouseX >= mapLeft && mouseX < mapLeft + size && mouseY >= mapTop && mouseY < mapTop + size) {
            centreX -= (int) Math.round(dragX * zoom);
            centreZ -= (int) Math.round(dragY * zoom);
            stale = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < mapLeft + size) {
            setZoom(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
