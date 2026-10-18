package 게임제작;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {

    static final int SCREEN_W = 1280;
    static final int SCREEN_H = 720;

    static final int CHUNK_SIZE = 16;
    static final int WORLD_HEIGHT = 64;
    static final int BIOME_REGION_SIZE = 80;
    static final int POND_CELL_SIZE = 72;
    static final int POND_WATER_LEVEL = 14;
    static final int PIXEL_SCALE = 2;
    static final int UI_PIXEL_SCALE = 3;
    static final int OPTIONS_VERSION = 2;
    static final float PIXEL_TEXT_SCALE = 1.18f;
    static final int RENDER_DISTANCE = 6;
    static final int CHUNK_MESH_BUILD_BUDGET = 2;
    static final float VIEW_DISTANCE = 64.0f;
    static final double DAY_LENGTH_SECONDS = 300.0;
    static final int LUNAR_CYCLE_DAYS = 8;

    static final float PLAYER_WIDTH = 0.60f;
    static final float PLAYER_HEIGHT = 1.80f;
    static final float PLAYER_EYE = 1.62f;
    static final float GRAVITY = 24.0f;
    static final float JUMP_VEL = 8.5f;
    static final float FLIGHT_SPEED = 8.0f;
    static final double DOUBLE_TAP_SECONDS = 0.35;
    static final float MOVE_SPEED = 6.0f;
    static final float RUN_SPEED = 9.5f;
    static final float FORWARD_BOOST_MULTIPLIER = 5.0f;
    static final float COLLISION_STEP_DISTANCE = 0.25f;
    static final float MOUSE_SENSITIVITY = 0.0010f;
    static final double LEFT_HOLD_BREAK_SECONDS = 0.40;
    static final double LEFT_BREAK_REPEAT_SECONDS = 0.20;
    static final double AUTOSAVE_INTERVAL_SECONDS = 60.0;
    static final double WORLD_DOUBLE_CLICK_SECONDS = 0.35;
    static final int[] MENU_INPUT_KEYS = {
            GLFW_KEY_UP, GLFW_KEY_DOWN, GLFW_KEY_LEFT, GLFW_KEY_RIGHT,
            GLFW_KEY_TAB, GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER, GLFW_KEY_BACKSPACE
    };
    static final float REACH_DISTANCE = 6.0f;
    static final int SAVE_MAGIC = 0x4D574C44;
    static final int SAVE_VERSION = 3;
    static final Path PREFERRED_SAVE_DIRECTORY = Path.of(System.getProperty("user.home"),
            ".mineworld-game-project");
    static Path SAVE_DIRECTORY = PREFERRED_SAVE_DIRECTORY;
    static Path SAVE_PATH = SAVE_DIRECTORY.resolve("world.dat");
    static Path SAVES_DIRECTORY = SAVE_DIRECTORY.resolve("saves");
    static Path OPTIONS_PATH = SAVE_DIRECTORY.resolve("options.properties");

    enum BlockType {
        AIR, GRASS, DIRT, STONE, WOOD, LEAF, WATER, TORCH, COBBLE, SAND, DOOR, BED,
        PLANKS, GLASS, BRICKS, BOOKSHELF, CRAFTING_TABLE, FURNACE, CHEST, WOOL, TNT,
        LADDER, FLOWER, MUSHROOM, CACTUS, CLAY, OBSIDIAN, ICE, SNOW, PUMPKIN, MELON,
        FENCE, STONE_BRICKS, GLOWSTONE, NETHERRACK, SOUL_SAND, LAPIS_BLOCK, GOLD_BLOCK,
        IRON_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK, REDSTONE_BLOCK, HAY_BALE, QUARTZ_BLOCK,
        RAIL, GLASS_PANE, FENCE_GATE, LEVER, REDSTONE_LAMP, SANDSTONE, END_STONE
    }

    enum ItemCategory { BLOCKS, MATERIALS, TOOLS, FOOD, MISC }

    enum ItemType {
        GRASS(BlockType.GRASS, ItemCategory.BLOCKS), DIRT(BlockType.DIRT, ItemCategory.BLOCKS),
        STONE(BlockType.STONE, ItemCategory.BLOCKS), OAK_LOG(BlockType.WOOD, ItemCategory.BLOCKS),
        LEAVES(BlockType.LEAF, ItemCategory.BLOCKS), WATER(BlockType.WATER, ItemCategory.BLOCKS),
        TORCH(BlockType.TORCH, ItemCategory.BLOCKS), COBBLESTONE(BlockType.COBBLE, ItemCategory.BLOCKS),
        SAND(BlockType.SAND, ItemCategory.BLOCKS), DOOR(BlockType.DOOR, ItemCategory.BLOCKS),
        BED(BlockType.BED, ItemCategory.BLOCKS), PLANKS(BlockType.PLANKS, ItemCategory.BLOCKS),
        GLASS(BlockType.GLASS, ItemCategory.BLOCKS), BRICK_BLOCK(BlockType.BRICKS, ItemCategory.BLOCKS),
        BOOKSHELF(BlockType.BOOKSHELF, ItemCategory.BLOCKS),
        CRAFTING_TABLE(BlockType.CRAFTING_TABLE, ItemCategory.BLOCKS),
        FURNACE(BlockType.FURNACE, ItemCategory.BLOCKS), CHEST(BlockType.CHEST, ItemCategory.BLOCKS),
        WOOL(BlockType.WOOL, ItemCategory.BLOCKS), TNT(BlockType.TNT, ItemCategory.BLOCKS),
        LADDER(BlockType.LADDER, ItemCategory.BLOCKS), FLOWER(BlockType.FLOWER, ItemCategory.BLOCKS),
        MUSHROOM(BlockType.MUSHROOM, ItemCategory.BLOCKS), CACTUS(BlockType.CACTUS, ItemCategory.BLOCKS),
        CLAY(BlockType.CLAY, ItemCategory.BLOCKS), OBSIDIAN(BlockType.OBSIDIAN, ItemCategory.BLOCKS),
        ICE(BlockType.ICE, ItemCategory.BLOCKS), SNOW(BlockType.SNOW, ItemCategory.BLOCKS),
        PUMPKIN(BlockType.PUMPKIN, ItemCategory.BLOCKS), MELON(BlockType.MELON, ItemCategory.BLOCKS),
        FENCE(BlockType.FENCE, ItemCategory.BLOCKS), STONE_BRICKS(BlockType.STONE_BRICKS, ItemCategory.BLOCKS),
        GLOWSTONE(BlockType.GLOWSTONE, ItemCategory.BLOCKS), NETHERRACK(BlockType.NETHERRACK, ItemCategory.BLOCKS),
        SOUL_SAND(BlockType.SOUL_SAND, ItemCategory.BLOCKS), LAPIS_BLOCK(BlockType.LAPIS_BLOCK, ItemCategory.BLOCKS),
        GOLD_BLOCK(BlockType.GOLD_BLOCK, ItemCategory.BLOCKS), IRON_BLOCK(BlockType.IRON_BLOCK, ItemCategory.BLOCKS),
        DIAMOND_BLOCK(BlockType.DIAMOND_BLOCK, ItemCategory.BLOCKS),
        EMERALD_BLOCK(BlockType.EMERALD_BLOCK, ItemCategory.BLOCKS),
        REDSTONE_BLOCK(BlockType.REDSTONE_BLOCK, ItemCategory.BLOCKS), HAY_BALE(BlockType.HAY_BALE, ItemCategory.BLOCKS),
        QUARTZ_BLOCK(BlockType.QUARTZ_BLOCK, ItemCategory.BLOCKS), RAIL(BlockType.RAIL, ItemCategory.BLOCKS),
        GLASS_PANE(BlockType.GLASS_PANE, ItemCategory.BLOCKS), FENCE_GATE(BlockType.FENCE_GATE, ItemCategory.BLOCKS),
        LEVER(BlockType.LEVER, ItemCategory.BLOCKS), REDSTONE_LAMP(BlockType.REDSTONE_LAMP, ItemCategory.BLOCKS),
        SANDSTONE(BlockType.SANDSTONE, ItemCategory.BLOCKS), END_STONE(BlockType.END_STONE, ItemCategory.BLOCKS),
        STICK(null, ItemCategory.MATERIALS), COAL(null, ItemCategory.MATERIALS),
        CHARCOAL(null, ItemCategory.MATERIALS), IRON_INGOT(null, ItemCategory.MATERIALS),
        GOLD_INGOT(null, ItemCategory.MATERIALS), DIAMOND(null, ItemCategory.MATERIALS),
        EMERALD(null, ItemCategory.MATERIALS), LAPIS_LAZULI(null, ItemCategory.MATERIALS),
        REDSTONE_DUST(null, ItemCategory.MATERIALS), STRING(null, ItemCategory.MATERIALS),
        FEATHER(null, ItemCategory.MATERIALS), LEATHER(null, ItemCategory.MATERIALS),
        IRON_ORE(null, ItemCategory.MATERIALS), GOLD_ORE(null, ItemCategory.MATERIALS),
        WHEAT(null, ItemCategory.MATERIALS), WHEAT_SEEDS(null, ItemCategory.MATERIALS),
        SUGAR_CANE(null, ItemCategory.MATERIALS), PAPER(null, ItemCategory.MATERIALS),
        BOOK(null, ItemCategory.MATERIALS), BONE(null, ItemCategory.MATERIALS),
        FLINT(null, ItemCategory.MATERIALS), CLAY_BALL(null, ItemCategory.MATERIALS),
        RED_DYE(null, ItemCategory.MATERIALS), BLUE_DYE(null, ItemCategory.MATERIALS),
        YELLOW_DYE(null, ItemCategory.MATERIALS), GREEN_DYE(null, ItemCategory.MATERIALS),
        WHITE_DYE(null, ItemCategory.MATERIALS), BROWN_DYE(null, ItemCategory.MATERIALS),
        WOODEN_PICKAXE(null, ItemCategory.TOOLS), STONE_PICKAXE(null, ItemCategory.TOOLS),
        IRON_PICKAXE(null, ItemCategory.TOOLS), DIAMOND_PICKAXE(null, ItemCategory.TOOLS),
        WOODEN_AXE(null, ItemCategory.TOOLS), STONE_AXE(null, ItemCategory.TOOLS),
        IRON_AXE(null, ItemCategory.TOOLS), DIAMOND_AXE(null, ItemCategory.TOOLS),
        WOODEN_SHOVEL(null, ItemCategory.TOOLS), STONE_SHOVEL(null, ItemCategory.TOOLS),
        IRON_SHOVEL(null, ItemCategory.TOOLS), DIAMOND_SHOVEL(null, ItemCategory.TOOLS),
        WOODEN_SWORD(null, ItemCategory.TOOLS), STONE_SWORD(null, ItemCategory.TOOLS),
        IRON_SWORD(null, ItemCategory.TOOLS), DIAMOND_SWORD(null, ItemCategory.TOOLS),
        WOODEN_HOE(null, ItemCategory.TOOLS), STONE_HOE(null, ItemCategory.TOOLS),
        IRON_HOE(null, ItemCategory.TOOLS), DIAMOND_HOE(null, ItemCategory.TOOLS),
        BOW(null, ItemCategory.TOOLS), ARROW(null, ItemCategory.MATERIALS),
        BUCKET(null, ItemCategory.TOOLS), WATER_BUCKET(null, ItemCategory.TOOLS),
        FLINT_AND_STEEL(null, ItemCategory.TOOLS), SHEARS(null, ItemCategory.TOOLS),
        FISHING_ROD(null, ItemCategory.TOOLS), MINECART(null, ItemCategory.MISC),
        COMPASS(null, ItemCategory.MISC), CLOCK(null, ItemCategory.MISC), MAP(null, ItemCategory.MISC),
        APPLE(null, ItemCategory.FOOD), BREAD(null, ItemCategory.FOOD), RAW_BEEF(null, ItemCategory.FOOD),
        COOKED_BEEF(null, ItemCategory.FOOD), PORKCHOP(null, ItemCategory.FOOD),
        COOKED_PORKCHOP(null, ItemCategory.FOOD), RAW_FISH(null, ItemCategory.FOOD),
        COOKED_FISH(null, ItemCategory.FOOD), COOKIE(null, ItemCategory.FOOD),
        CAKE(null, ItemCategory.FOOD), PUMPKIN_PIE(null, ItemCategory.FOOD),
        EGG(null, ItemCategory.FOOD), MUSHROOM_STEW(null, ItemCategory.FOOD),
        GOLDEN_APPLE(null, ItemCategory.FOOD);
        private final BlockType block;
        private final ItemCategory category;

        ItemType(BlockType block, ItemCategory category) {
            this.block = block;
            this.category = category;
        }

        String label() { return name().replace('_', ' '); }
        BlockType block() { return block; }
        ItemCategory category() { return category; }
    }

    record Ingredient(ItemType item, int count) {}
    record Recipe(ItemType result, int resultCount, boolean requiresTable,
                  List<Ingredient> ingredients) {}
    record InventorySlotHit(float x, float y, float size, int index) {}
    record CraftingRecipeHit(MenuButton area, Recipe recipe) {}
    record CatalogItemHit(MenuButton area, ItemType item) {}

    static final List<Recipe> RECIPES = List.of(
            recipe(ItemType.PLANKS, 4, false, ing(ItemType.OAK_LOG, 1)),
            recipe(ItemType.STICK, 4, false, ing(ItemType.PLANKS, 2)),
            recipe(ItemType.CRAFTING_TABLE, 1, false, ing(ItemType.PLANKS, 4)),
            recipe(ItemType.TORCH, 4, false, ing(ItemType.COAL, 1), ing(ItemType.STICK, 1)),
            recipe(ItemType.DOOR, 3, false, ing(ItemType.PLANKS, 6)),
            recipe(ItemType.BREAD, 1, false, ing(ItemType.WHEAT, 3)),
            recipe(ItemType.WOODEN_PICKAXE, 1, true, ing(ItemType.PLANKS, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.WOODEN_AXE, 1, true, ing(ItemType.PLANKS, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.WOODEN_SHOVEL, 1, true, ing(ItemType.PLANKS, 1), ing(ItemType.STICK, 2)),
            recipe(ItemType.WOODEN_SWORD, 1, true, ing(ItemType.PLANKS, 2), ing(ItemType.STICK, 1)),
            recipe(ItemType.STONE_PICKAXE, 1, true, ing(ItemType.COBBLESTONE, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.STONE_AXE, 1, true, ing(ItemType.COBBLESTONE, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.STONE_SHOVEL, 1, true, ing(ItemType.COBBLESTONE, 1), ing(ItemType.STICK, 2)),
            recipe(ItemType.STONE_SWORD, 1, true, ing(ItemType.COBBLESTONE, 2), ing(ItemType.STICK, 1)),
            recipe(ItemType.IRON_PICKAXE, 1, true, ing(ItemType.IRON_INGOT, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.IRON_AXE, 1, true, ing(ItemType.IRON_INGOT, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.IRON_SHOVEL, 1, true, ing(ItemType.IRON_INGOT, 1), ing(ItemType.STICK, 2)),
            recipe(ItemType.IRON_SWORD, 1, true, ing(ItemType.IRON_INGOT, 2), ing(ItemType.STICK, 1)),
            recipe(ItemType.DIAMOND_PICKAXE, 1, true, ing(ItemType.DIAMOND, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.DIAMOND_AXE, 1, true, ing(ItemType.DIAMOND, 3), ing(ItemType.STICK, 2)),
            recipe(ItemType.DIAMOND_SHOVEL, 1, true, ing(ItemType.DIAMOND, 1), ing(ItemType.STICK, 2)),
            recipe(ItemType.DIAMOND_SWORD, 1, true, ing(ItemType.DIAMOND, 2), ing(ItemType.STICK, 1)),
            recipe(ItemType.CHEST, 1, true, ing(ItemType.PLANKS, 8)),
            recipe(ItemType.FURNACE, 1, true, ing(ItemType.COBBLESTONE, 8)),
            recipe(ItemType.BED, 1, true, ing(ItemType.WOOL, 3), ing(ItemType.PLANKS, 3)),
            recipe(ItemType.LADDER, 3, true, ing(ItemType.STICK, 7)),
            recipe(ItemType.FENCE, 3, true, ing(ItemType.PLANKS, 4), ing(ItemType.STICK, 2)),
            recipe(ItemType.STONE_BRICKS, 4, true, ing(ItemType.STONE, 4)),
            recipe(ItemType.BRICK_BLOCK, 1, true, ing(ItemType.CLAY_BALL, 4)),
            recipe(ItemType.PAPER, 3, false, ing(ItemType.SUGAR_CANE, 3)),
            recipe(ItemType.BOOK, 1, false, ing(ItemType.PAPER, 3), ing(ItemType.LEATHER, 1)),
            recipe(ItemType.BOOKSHELF, 1, true, ing(ItemType.PLANKS, 6), ing(ItemType.BOOK, 3)),
            recipe(ItemType.RAIL, 16, true, ing(ItemType.IRON_INGOT, 6), ing(ItemType.STICK, 1)),
            recipe(ItemType.GLASS_PANE, 16, true, ing(ItemType.GLASS, 6)),
            recipe(ItemType.GOLD_BLOCK, 1, true, ing(ItemType.GOLD_INGOT, 9)),
            recipe(ItemType.IRON_BLOCK, 1, true, ing(ItemType.IRON_INGOT, 9)),
            recipe(ItemType.DIAMOND_BLOCK, 1, true, ing(ItemType.DIAMOND, 9)),
            recipe(ItemType.EMERALD_BLOCK, 1, true, ing(ItemType.EMERALD, 9)),
            recipe(ItemType.REDSTONE_BLOCK, 1, true, ing(ItemType.REDSTONE_DUST, 9)),
            recipe(ItemType.GOLD_INGOT, 9, true, ing(ItemType.GOLD_BLOCK, 1)),
            recipe(ItemType.IRON_INGOT, 9, true, ing(ItemType.IRON_BLOCK, 1)),
            recipe(ItemType.DIAMOND, 9, true, ing(ItemType.DIAMOND_BLOCK, 1)),
            recipe(ItemType.EMERALD, 9, true, ing(ItemType.EMERALD_BLOCK, 1)),
            recipe(ItemType.REDSTONE_DUST, 9, true, ing(ItemType.REDSTONE_BLOCK, 1))
    );

    static Ingredient ing(ItemType item, int count) { return new Ingredient(item, count); }

    static Recipe recipe(ItemType result, int count, boolean requiresTable, Ingredient... ingredients) {
        return new Recipe(result, count, requiresTable, List.of(ingredients));
    }

    enum Biome {
        PLAINS, MOUNTAINS, BEACH, DESERT
    }

    enum Screen {
        TITLE, WORLD_SELECT, CREATE_WORLD, OPTIONS, GAME, PAUSED, INVENTORY, CRAFTING
    }

    static final class Chunk {
        final int cx, cz;
        boolean generated;
        int displayList;
        int transparentDisplayList;
        boolean meshDirty = true;
        final byte[] blocks = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
        final byte[] blockStates = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
        final byte[] sunlight = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
        final byte[] torchlight = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];

        Chunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        int idx(int x, int y, int z) {
            return (y * CHUNK_SIZE + x) * CHUNK_SIZE + z;
        }
    }

    static final class World {
        final Map<Long, Chunk> chunks = new HashMap<>();
    }

    static final class Player {
        float x = 8.0f;
        float y = 28.0f;
        float z = 8.0f;
        float vx, vy, vz;
        float yaw = 0.0f;
        float pitch = 0.0f;
        boolean onGround = false;
    }

    static final class Inventory {
        static final int SLOT_COUNT = 36;
        static final int HOTBAR_SIZE = 9;
        static final int MAX_STACK = 64;
        final ItemType[] items = new ItemType[SLOT_COUNT];
        final int[] counts = new int[SLOT_COUNT];
        int selected = 0;
        ItemType carriedItem;
        int carriedCount;

        Inventory() {
            setSlot(0, ItemType.GRASS, 64);
            setSlot(1, ItemType.DIRT, 64);
            setSlot(2, ItemType.STONE, 64);
            setSlot(3, ItemType.OAK_LOG, 32);
            setSlot(4, ItemType.PLANKS, 64);
            setSlot(5, ItemType.TORCH, 32);
            setSlot(6, ItemType.COBBLESTONE, 64);
            setSlot(7, ItemType.CRAFTING_TABLE, 2);
            setSlot(8, ItemType.DOOR, 4);
            setSlot(9, ItemType.BED, 2);
            addItem(ItemType.SAND, 64);
            addItem(ItemType.WATER, 32);
            addItem(ItemType.STICK, 32);
            addItem(ItemType.COAL, 16);
            addItem(ItemType.WOOL, 16);
            addItem(ItemType.WHEAT, 12);
            addItem(ItemType.IRON_INGOT, 8);
            addItem(ItemType.DIAMOND, 4);
            addItem(ItemType.REDSTONE_DUST, 16);
        }

        Inventory(boolean empty) {
            if (!empty) {
                setSlot(0, ItemType.GRASS, 64);
                setSlot(1, ItemType.DIRT, 64);
                setSlot(2, ItemType.STONE, 64);
                setSlot(3, ItemType.OAK_LOG, 32);
                setSlot(4, ItemType.PLANKS, 64);
                setSlot(5, ItemType.TORCH, 32);
                setSlot(6, ItemType.COBBLESTONE, 64);
                setSlot(7, ItemType.CRAFTING_TABLE, 2);
                setSlot(8, ItemType.DOOR, 4);
                setSlot(9, ItemType.BED, 2);
                addItem(ItemType.SAND, 64);
                addItem(ItemType.WATER, 32);
                addItem(ItemType.STICK, 32);
                addItem(ItemType.COAL, 16);
                addItem(ItemType.WOOL, 16);
                addItem(ItemType.WHEAT, 12);
                addItem(ItemType.IRON_INGOT, 8);
                addItem(ItemType.DIAMOND, 4);
                addItem(ItemType.REDSTONE_DUST, 16);
            }
        }

        void setSlot(int index, ItemType item, int count) {
            items[index] = item;
            counts[index] = count;
        }

        ItemType selectedItem() {
            return items[Math.max(0, Math.min(HOTBAR_SIZE - 1, selected))];
        }

        int countOf(ItemType item) {
            int total = 0;
            for (int i = 0; i < SLOT_COUNT; i++) if (items[i] == item) total += counts[i];
            return total;
        }

        boolean hasSpace(ItemType item, int amount) {
            int free = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (items[i] == null) free += MAX_STACK;
                else if (items[i] == item) free += MAX_STACK - counts[i];
            }
            return free >= amount;
        }

        boolean hasSpaceAfter(Recipe recipe) {
            ItemType[] simulatedItems = items.clone();
            int[] simulatedCounts = counts.clone();
            for (Ingredient ingredient : recipe.ingredients()) {
                int remaining = ingredient.count();
                for (int i = SLOT_COUNT - 1; i >= 0 && remaining > 0; i--) {
                    if (simulatedItems[i] != ingredient.item()) continue;
                    int removed = Math.min(remaining, simulatedCounts[i]);
                    simulatedCounts[i] -= removed;
                    remaining -= removed;
                    if (simulatedCounts[i] == 0) simulatedItems[i] = null;
                }
                if (remaining > 0) return false;
            }
            int free = 0;
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (simulatedItems[i] == null) free += MAX_STACK;
                else if (simulatedItems[i] == recipe.result()) free += MAX_STACK - simulatedCounts[i];
            }
            return free >= recipe.resultCount();
        }

        boolean addItem(ItemType item, int amount) {
            if (item == null || amount <= 0 || !hasSpace(item, amount)) return false;
            for (int i = 0; i < SLOT_COUNT && amount > 0; i++) {
                if (items[i] != item) continue;
                int added = Math.min(amount, MAX_STACK - counts[i]);
                counts[i] += added;
                amount -= added;
            }
            for (int i = 0; i < SLOT_COUNT && amount > 0; i++) {
                if (items[i] != null) continue;
                int added = Math.min(amount, MAX_STACK);
                items[i] = item;
                counts[i] = added;
                amount -= added;
            }
            return true;
        }

        boolean removeItem(ItemType item, int amount) {
            if (countOf(item) < amount) return false;
            for (int i = SLOT_COUNT - 1; i >= 0 && amount > 0; i--) {
                if (items[i] != item) continue;
                int removed = Math.min(amount, counts[i]);
                counts[i] -= removed;
                amount -= removed;
                if (counts[i] == 0) items[i] = null;
            }
            return true;
        }

        boolean consumeSelected() {
            int index = Math.max(0, Math.min(HOTBAR_SIZE - 1, selected));
            if (items[index] == null || counts[index] <= 0) return false;
            counts[index]--;
            if (counts[index] == 0) items[index] = null;
            return true;
        }
    }

    record BlockHit(int x, int y, int z, int previousX, int previousY, int previousZ) {}
    record SaveEntry(String name, Path path, long modifiedMillis) {}
    record MenuButton(String label, String action, float x, float y, float width, float height) {}

    static long window;
    static World world;
    static Player player;
    static Inventory inventory;

    static int fpsCounter = 0;
    static double fpsTimer = 0.0;
    static int displayedFps = 0;
    static String statusMessage = "";
    static double statusTimer = 0.0;
    static boolean leftMouseWasDown;
    static boolean rightMouseWasDown;
    static boolean leftMouseLongPressTriggered;
    static boolean suppressMouseActionsUntilReleased;
    static double leftMousePressedAt;
    static double nextLeftBreakAt;
    static boolean saveKeyWasDown;
    static boolean loadKeyWasDown;
    static boolean spaceKeyWasDown;
    static boolean forwardKeyWasDown;
    static boolean forwardBoost;
    static boolean flightMode;
    static double lastSpacePressTime = Double.NEGATIVE_INFINITY;
    static double lastForwardPressTime = Double.NEGATIVE_INFINITY;
    static double mouseDeltaX;
    static double mouseDeltaY;
    static double lastMouseX;
    static double lastMouseY;
    static boolean mousePositionInitialized;
    static boolean mouseCaptureRequested = true;
    static double recaptureAfterResizeAt = Double.NEGATIVE_INFINITY;
    static final boolean[] menuKeyWasDown = new boolean[MENU_INPUT_KEYS.length];
    static boolean menuLeftMouseWasDown;
    static boolean inventoryLeftMouseWasDown;
    static boolean inventoryRightMouseWasDown;
    static boolean inventoryUpKeyWasDown;
    static boolean inventoryDownKeyWasDown;
    static boolean inventoryTabKeyWasDown;
    static int craftingRecipeScroll;
    static int craftingRecipeSelection;
    static int catalogItemScroll;
    static ItemCategory catalogCategory = ItemCategory.BLOCKS;
    static boolean catalogMode;
    static Screen screen = Screen.TITLE;
    static Screen optionsReturnScreen = Screen.TITLE;
    static int menuSelection;
    static int selectedSaveIndex = -1;
    static int menuViewportWidth = SCREEN_W / UI_PIXEL_SCALE;
    static int menuViewportHeight = SCREEN_H / UI_PIXEL_SCALE;
    static int pixelScale = PIXEL_SCALE;
    static float mouseSensitivity = MOUSE_SENSITIVITY;
    static float spawnX = 8.5f;
    static float spawnY = 28.0f;
    static float spawnZ = 8.5f;
    static String newWorldName = "New World";
    static String activeWorldName = "";
    static Path activeSavePath;
    static final ArrayList<SaveEntry> saveEntries = new ArrayList<>();
    static final ArrayList<MenuButton> menuButtons = new ArrayList<>();
    static final ArrayList<MenuButton> saveRows = new ArrayList<>();
    static final ArrayList<InventorySlotHit> inventorySlotHits = new ArrayList<>();
    static final ArrayList<CraftingRecipeHit> craftingRecipeHits = new ArrayList<>();
    static final ArrayList<CatalogItemHit> catalogItemHits = new ArrayList<>();
    static final ArrayList<MenuButton> catalogCategoryHits = new ArrayList<>();
    static int saveListOffset;
    static int lastWorldRowClickIndex = -1;
    static double lastWorldRowClickTime = Double.NEGATIVE_INFINITY;
    static double autosaveTimer;
    static double hotbarScrollRemainder;
    static double worldClockSeconds = DAY_LENGTH_SECONDS * 0.25;
    static int daylightBucket = -1;
    static int pixelArtTexture;
    static int pixelArtTextureWidth;
    static int pixelArtTextureHeight;

    public static void main(String[] args) {
        GLFWErrorCallback.createPrint(System.err).set();

        initializeSaveStorage();
        loadOptions();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }

        // fixed pipeline 쓰기 위한 2.1 컨텍스트
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(SCREEN_W, SCREEN_H, "Mineworld", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Window creation failed");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        mouseCaptureRequested = false;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        glfwSetCursorPosCallback(window, (handle, x, y) -> {
            if (glfwGetInputMode(handle, GLFW_CURSOR) != GLFW_CURSOR_DISABLED) {
                mousePositionInitialized = false;
                return;
            }
            if (mousePositionInitialized) {
                mouseDeltaX += x - lastMouseX;
                mouseDeltaY += y - lastMouseY;
            }
            lastMouseX = x;
            lastMouseY = y;
            mousePositionInitialized = true;
        });
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_ESCAPE) {
                handleEscapeKey();
            } else if (key == GLFW_KEY_E && screen == Screen.GAME) {
                openInventory();
            } else if (key == GLFW_KEY_E
                    && (screen == Screen.INVENTORY || screen == Screen.CRAFTING)) {
                closeInventory();
            } else if (key == GLFW_KEY_F3 && screen == Screen.GAME) {
                setMouseCaptured(true);
                setStatus("Mouse captured");
            }
        });
        glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (screen != Screen.GAME) {
                return;
            }
            if ((button == GLFW_MOUSE_BUTTON_LEFT || button == GLFW_MOUSE_BUTTON_RIGHT)
                    && (!mouseCaptureRequested
                    || glfwGetInputMode(handle, GLFW_CURSOR) != GLFW_CURSOR_DISABLED)) {
                setMouseCaptured(true);
                suppressMouseActionsUntilReleased = true;
                setStatus("Mouse captured");
            }
        });
        glfwSetWindowFocusCallback(window, (handle, focused) -> {
            mouseDeltaX = 0.0;
            mouseDeltaY = 0.0;
            mousePositionInitialized = false;
            if (!focused) {
                setMouseCaptured(false);
                recaptureAfterResizeAt = Double.NEGATIVE_INFINITY;
                suppressMouseActionsUntilReleased = true;
                leftMouseWasDown = false;
                rightMouseWasDown = false;
            } else if (screen == Screen.GAME) {
                setStatus("Click the game to capture the mouse");
            }
        });
        glfwSetWindowSizeCallback(window, (handle, width, height) -> {
            mouseDeltaX = 0.0;
            mouseDeltaY = 0.0;
            if (glfwGetInputMode(handle, GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
                double[] cursorX = new double[1];
                double[] cursorY = new double[1];
                glfwGetCursorPos(handle, cursorX, cursorY);
                lastMouseX = cursorX[0];
                lastMouseY = cursorY[0];
                mousePositionInitialized = true;
            } else {
                mousePositionInitialized = false;
                if (screen == Screen.GAME && mouseCaptureRequested
                        && glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE) {
                    recaptureAfterResizeAt = glfwGetTime() + 0.25;
                }
            }
        });
        glfwSetCharCallback(window, (handle, codepoint) -> {
            if (screen != Screen.CREATE_WORLD || newWorldName.length() >= 24) return;
            if (codepoint >= 32 && codepoint <= 126) {
                char character = Character.toUpperCase((char)codepoint);
                if (Character.isLetterOrDigit(character) || character == ' '
                        || character == '-' || character == '_') {
                    newWorldName += character;
                }
            }
        });
        double[] initialMouseX = new double[1];
        double[] initialMouseY = new double[1];
        glfwGetCursorPos(window, initialMouseX, initialMouseY);
        lastMouseX = initialMouseX[0];
        lastMouseY = initialMouseY[0];
        mousePositionInitialized = true;

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.53f, 0.72f, 0.83f, 1.0f);

        world = new World();
        player = new Player();
        worldClockSeconds = DAY_LENGTH_SECONDS * 0.25;
        player.y = Math.max(terrainHeight((int)player.x, (int)player.z), 10);
        spawnX = player.x;
        spawnY = player.y;
        spawnZ = player.z;
        inventory = new Inventory();
        glfwSetScrollCallback(window, (handle, xOffset, yOffset) -> {
            if ((screen == Screen.INVENTORY || screen == Screen.CRAFTING) && catalogMode) {
                catalogItemScroll = Math.max(0, catalogItemScroll - (int)Math.signum(yOffset) * 9);
            } else if (screen == Screen.INVENTORY || screen == Screen.CRAFTING) {
                craftingRecipeScroll = Math.max(0, craftingRecipeScroll - (int)Math.signum(yOffset));
            } else {
                scrollHotbar(yOffset);
            }
        });
        updateDaylightMeshes();

        ensureWorldAround(player.x, player.z);

        loop();

        disposeChunkMeshes(world);
        if (pixelArtTexture != 0) glDeleteTextures(pixelArtTexture);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    static void loop() {
        double last = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float)(now - last);
            last = now;
            if (dt > 0.05f) dt = 0.05f;

            fpsTimer += dt;
            fpsCounter++;
            if (fpsTimer >= 1.0) {
                displayedFps = fpsCounter;
                fpsTimer = 0.0;
                fpsCounter = 0;
                glfwSetWindowTitle(window, statusTimer > 0.0
                        ? "Mineworld | " + skyPhaseName() + " | " + displayedFps
                        + " FPS | " + statusMessage
                        : "Mineworld | " + skyPhaseName() + " | Moon "
                        + (moonPhase() + 1) + "/8 | " + displayedFps + " FPS");
            }

            update(dt);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        if (activeSavePath != null) {
            try {
                saveWorld(activeSavePath);
            } catch (IOException e) {
                reportPersistenceError("Could not save world", e);
            }
        }
    }

    static void update(float dt) {
        statusTimer = Math.max(0.0, statusTimer - dt);
        if (screen == Screen.GAME || screen == Screen.TITLE) {
            worldClockSeconds += dt;
        }
        updateDaylightMeshes();
        if (recaptureAfterResizeAt != Double.NEGATIVE_INFINITY
                && glfwGetTime() >= recaptureAfterResizeAt
                && screen == Screen.GAME && mouseCaptureRequested
                && glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE) {
            recaptureAfterResizeAt = Double.NEGATIVE_INFINITY;
            setMouseCaptured(true);
        }
        if (screen == Screen.INVENTORY || screen == Screen.CRAFTING) updateInventoryInput();
        else if (screen != Screen.GAME) updateMenuInput();
        if (screen != Screen.GAME) {
            player.vx = 0.0f;
            player.vy = 0.0f;
            player.vz = 0.0f;
            return;
        }

        if (activeSavePath != null) {
            autosaveTimer += dt;
            if (autosaveTimer >= AUTOSAVE_INTERVAL_SECONDS) {
                autosaveTimer = 0.0;
                try {
                    saveWorld(activeSavePath);
                    setStatus("World auto-saved");
                } catch (IOException e) {
                    reportPersistenceError("Could not auto-save world", e);
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            int key = GLFW_KEY_1 + i;
            if (glfwGetKey(window, key) == GLFW_PRESS) {
                inventory.selected = i;
            }
        }
        if (glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
            player.yaw = (float)Math.IEEEremainder(
                    player.yaw + mouseDeltaX * mouseSensitivity, Math.PI * 2.0);
            player.pitch += (float)mouseDeltaY * mouseSensitivity;
            player.pitch = clamp(player.pitch, -1.50f, 1.50f);
            mouseDeltaX = 0.0;
            mouseDeltaY = 0.0;
        }

        float yaw = player.yaw;
        float pitch = player.pitch;

        float cosPitch = (float)Math.cos(pitch);
        float fx = cosPitch * (float)Math.sin(yaw);
        float fy = -(float)(Math.sin(pitch));
        float fz = -cosPitch * (float)Math.cos(yaw);

        boolean forwardKeyDown = isKeyDown(GLFW_KEY_W);
        if (forwardKeyDown && !forwardKeyWasDown) {
            double now = glfwGetTime();
            if (now - lastForwardPressTime <= DOUBLE_TAP_SECONDS) {
                forwardBoost = true;
                lastForwardPressTime = Double.NEGATIVE_INFINITY;
                setStatus("Forward boost enabled");
            } else {
                lastForwardPressTime = now;
            }
        }
        if (!forwardKeyDown) forwardBoost = false;
        forwardKeyWasDown = forwardKeyDown;

        boolean forward = isKeyDown(GLFW_KEY_W) || isKeyDown(GLFW_KEY_UP);
        boolean backward = isKeyDown(GLFW_KEY_S) || isKeyDown(GLFW_KEY_DOWN);
        boolean left = isKeyDown(GLFW_KEY_A) || isKeyDown(GLFW_KEY_LEFT);
        boolean right = isKeyDown(GLFW_KEY_D) || isKeyDown(GLFW_KEY_RIGHT);
        float speed = (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS)
                ? RUN_SPEED : MOVE_SPEED;
        if (forwardBoost && forward && !backward) {
            speed = MOVE_SPEED * FORWARD_BOOST_MULTIPLIER;
        }
        float[] movement = movementDirection(yaw, forward, backward, left, right);
        player.vx = movement[0] * speed;
        player.vz = movement[1] * speed;

        boolean spaceDown = isKeyDown(GLFW_KEY_SPACE);
        if (spaceDown && !spaceKeyWasDown) {
            handleSpacePress(glfwGetTime());
        }
        spaceKeyWasDown = spaceDown;

        if (flightMode) {
            player.vy = flightVerticalVelocity(spaceDown, isKeyDown(GLFW_KEY_LEFT_SHIFT));
        } else if (spaceDown && player.onGround) {
            player.vy = JUMP_VEL;
        }

        if (!flightMode) {
            player.vy -= GRAVITY * dt;
        }

        ensureWorldAround(player.x, player.z);

        moveWithCollision(dt);
        if (player.y < -32.0f) {
            player.x = spawnX;
            player.y = spawnY;
            player.z = spawnZ;
            player.vx = 0.0f;
            player.vy = 0.0f;
            player.vz = 0.0f;
            ensureWorldAround(player.x, player.z);
            setStatus("Respawned at your spawn point");
        }

        boolean leftMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rightMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        double inputTime = glfwGetTime();
        if (suppressMouseActionsUntilReleased) {
            if (!leftMouseDown && !rightMouseDown) {
                suppressMouseActionsUntilReleased = false;
            }
            leftMouseWasDown = leftMouseDown;
            rightMouseWasDown = rightMouseDown;
        } else {
            if (leftMouseDown && !leftMouseWasDown) {
                leftMousePressedAt = inputTime;
                leftMouseLongPressTriggered = false;
            }
            if (leftMouseDown && !leftMouseLongPressTriggered
                    && inputTime - leftMousePressedAt >= LEFT_HOLD_BREAK_SECONDS) {
                raycastBreak(fx, fy, fz);
                leftMouseLongPressTriggered = true;
                nextLeftBreakAt = inputTime + LEFT_BREAK_REPEAT_SECONDS;
            } else if (leftMouseDown && leftMouseLongPressTriggered
                    && inputTime >= nextLeftBreakAt) {
                raycastBreak(fx, fy, fz);
                nextLeftBreakAt = inputTime + LEFT_BREAK_REPEAT_SECONDS;
            }
            if (!leftMouseDown && leftMouseWasDown && !leftMouseLongPressTriggered) {
                raycastPlace(fx, fy, fz);
            }
            if (rightMouseDown && !rightMouseWasDown) {
                if (!raycastUse(fx, fy, fz)) raycastPlace(fx, fy, fz);
            }
            leftMouseWasDown = leftMouseDown;
            rightMouseWasDown = rightMouseDown;
        }

        boolean saveDown = glfwGetKey(window, GLFW_KEY_F5) == GLFW_PRESS;
        boolean loadDown = glfwGetKey(window, GLFW_KEY_F9) == GLFW_PRESS;
        if (saveDown && !saveKeyWasDown) {
            try {
                saveWorld();
                autosaveTimer = 0.0;
                setStatus("World saved");
            } catch (IOException e) {
                reportPersistenceError("Could not save world", e);
            }
        }
        if (loadDown && !loadKeyWasDown) {
            try {
                loadWorld();
                ensureWorldAround(player.x, player.z);
                setStatus("World loaded");
            } catch (IOException e) {
                reportPersistenceError("Could not load world", e);
            }
        }
        saveKeyWasDown = saveDown;
        loadKeyWasDown = loadDown;
    }

    static void setMouseCaptured(boolean captured) {
        mouseCaptureRequested = captured;
        if (captured) recaptureAfterResizeAt = Double.NEGATIVE_INFINITY;
        boolean rawMouseSupported = glfwRawMouseMotionSupported();
        if (!captured && rawMouseSupported) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_FALSE);
        }

        glfwSetInputMode(window, GLFW_CURSOR,
                captured ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);

        if (captured && rawMouseSupported) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        mouseDeltaX = 0.0;
        mouseDeltaY = 0.0;
        mousePositionInitialized = captured;
        if (captured) {
            double[] cursorX = new double[1];
            double[] cursorY = new double[1];
            glfwGetCursorPos(window, cursorX, cursorY);
            lastMouseX = cursorX[0];
            lastMouseY = cursorY[0];
        }
    }

    static void openInventory() {
        if (screen != Screen.GAME) return;
        screen = Screen.INVENTORY;
        craftingRecipeScroll = 0;
        catalogMode = false;
        inventoryLeftMouseWasDown = false;
        inventoryRightMouseWasDown = false;
        setMouseCaptured(false);
    }

    static void openCraftingTable() {
        screen = Screen.CRAFTING;
        craftingRecipeScroll = 0;
        catalogMode = false;
        inventoryLeftMouseWasDown = false;
        inventoryRightMouseWasDown = false;
        setMouseCaptured(false);
        setStatus("Crafting table opened");
    }

    static void closeInventory() {
        if (screen != Screen.INVENTORY && screen != Screen.CRAFTING) return;
        screen = Screen.GAME;
        suppressMouseActionsUntilReleased = true;
        setMouseCaptured(true);
    }

    static void updateInventoryInput() {
        boolean leftMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rightMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        if (leftMouseDown && !inventoryLeftMouseWasDown) handleInventoryClick(window, false);
        if (rightMouseDown && !inventoryRightMouseWasDown) handleInventoryClick(window, true);
        inventoryLeftMouseWasDown = leftMouseDown;
        inventoryRightMouseWasDown = rightMouseDown;
        boolean upDown = glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS;
        boolean downDown = glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS;
        boolean tabDown = glfwGetKey(window, GLFW_KEY_TAB) == GLFW_PRESS;
        if (upDown && !inventoryUpKeyWasDown) {
            if (catalogMode) catalogItemScroll = Math.max(0, catalogItemScroll - 9);
            else craftingRecipeScroll = Math.max(0, craftingRecipeScroll - 1);
        }
        if (downDown && !inventoryDownKeyWasDown) {
            if (catalogMode) catalogItemScroll += 9;
            else craftingRecipeScroll++;
        }
        if (tabDown && !inventoryTabKeyWasDown) {
            catalogMode = !catalogMode;
            inventorySlotHits.clear();
            craftingRecipeHits.clear();
        }
        inventoryUpKeyWasDown = upDown;
        inventoryDownKeyWasDown = downDown;
        inventoryTabKeyWasDown = tabDown;
        if (catalogMode) return;
    }

    static void handleInventoryClick(long handle, boolean singleItem) {
        float[] point = menuCursorPosition(handle);
        if (catalogMode) {
            for (int i = 0; i < catalogCategoryHits.size(); i++) {
                if (!menuButtonContains(catalogCategoryHits.get(i), point)) continue;
                catalogCategory = ItemCategory.values()[i];
                catalogItemScroll = 0;
                return;
            }
            for (CatalogItemHit hit : catalogItemHits) {
                if (!menuButtonContains(hit.area(), point)) continue;
                if (inventory.carriedItem == null || inventory.carriedItem == hit.item()) {
                    inventory.carriedItem = hit.item();
                    inventory.carriedCount = Inventory.MAX_STACK;
                    setStatus("Picked " + hit.item().label() + " from catalog");
                }
                return;
            }
            return;
        }
        for (InventorySlotHit hit : inventorySlotHits) {
            if (point[0] >= hit.x() && point[0] <= hit.x() + hit.size()
                    && point[1] >= hit.y() && point[1] <= hit.y() + hit.size()) {
                moveInventoryStack(hit.index(), singleItem);
                return;
            }
        }
        for (CraftingRecipeHit hit : craftingRecipeHits) {
            if (menuButtonContains(hit.area(), point)) {
                craftRecipe(hit.recipe());
                return;
            }
        }
    }

    static void moveInventoryStack(int slot, boolean singleItem) {
        ItemType slotItem = inventory.items[slot];
        int slotCount = inventory.counts[slot];
        if (inventory.carriedItem == null) {
            if (slotItem == null) return;
            int taken = singleItem ? (slotCount + 1) / 2 : slotCount;
            inventory.carriedItem = slotItem;
            inventory.carriedCount = taken;
            inventory.counts[slot] -= taken;
            if (inventory.counts[slot] == 0) inventory.items[slot] = null;
            return;
        }
        if (slotItem == null) {
            int placed = singleItem ? 1 : inventory.carriedCount;
            inventory.items[slot] = inventory.carriedItem;
            inventory.counts[slot] = placed;
            inventory.carriedCount -= placed;
        } else if (slotItem == inventory.carriedItem) {
            int placed = Math.min(singleItem ? 1 : inventory.carriedCount,
                    Inventory.MAX_STACK - slotCount);
            inventory.counts[slot] += placed;
            inventory.carriedCount -= placed;
        } else if (!singleItem) {
            inventory.items[slot] = inventory.carriedItem;
            inventory.counts[slot] = inventory.carriedCount;
            inventory.carriedItem = slotItem;
            inventory.carriedCount = slotCount;
        }
        if (inventory.carriedCount <= 0) {
            inventory.carriedItem = null;
            inventory.carriedCount = 0;
        }
    }

    static List<Recipe> visibleRecipes() {
        boolean tableOpen = screen == Screen.CRAFTING;
        return RECIPES.stream().filter(recipe -> tableOpen || !recipe.requiresTable()).toList();
    }

    static boolean canCraft(Recipe recipe) {
        if (recipe.requiresTable() && screen != Screen.CRAFTING) return false;
        for (Ingredient ingredient : recipe.ingredients()) {
            if (inventory.countOf(ingredient.item()) < ingredient.count()) return false;
        }
        return inventory.hasSpaceAfter(recipe);
    }

    static void craftRecipe(Recipe recipe) {
        if (!canCraft(recipe)) {
            setStatus("Missing ingredients or inventory full");
            return;
        }
        for (Ingredient ingredient : recipe.ingredients()) {
            inventory.removeItem(ingredient.item(), ingredient.count());
        }
        inventory.addItem(recipe.result(), recipe.resultCount());
        setStatus("Crafted " + recipe.result().label());
    }

    static boolean isKeyDown(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }

    static void updateMenuInput() {
        boolean leftMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        if (leftMouseDown && !menuLeftMouseWasDown) handleMenuClick(window);
        menuLeftMouseWasDown = leftMouseDown;

        int pressedKey = 0;
        for (int index = 0; index < MENU_INPUT_KEYS.length; index++) {
            boolean keyDown = glfwGetKey(window, MENU_INPUT_KEYS[index]) == GLFW_PRESS;
            if (keyDown && !menuKeyWasDown[index] && pressedKey == 0) {
                pressedKey = MENU_INPUT_KEYS[index];
            }
            menuKeyWasDown[index] = keyDown;
        }
        if (pressedKey != 0) handleMenuKey(pressedKey);
    }

    static boolean handleSpacePress(double now) {
        if (now - lastSpacePressTime <= DOUBLE_TAP_SECONDS) {
            flightMode = !flightMode;
            lastSpacePressTime = Double.NEGATIVE_INFINITY;
            player.vy = 0.0f;
            setStatus(flightMode ? "Flight enabled" : "Flight disabled");
            return true;
        }
        lastSpacePressTime = now;
        return false;
    }

    static float flightVerticalVelocity(boolean ascend, boolean descend) {
        if (ascend == descend) return 0.0f;
        return ascend ? FLIGHT_SPEED : -FLIGHT_SPEED;
    }

    static float[] movementDirection(float yaw, boolean forward, boolean backward,
                                     boolean left, boolean right) {
        float forwardX = (float)Math.sin(yaw);
        float forwardZ = (float)-Math.cos(yaw);
        float rightX = (float)Math.cos(yaw);
        float rightZ = (float)Math.sin(yaw);
        float x = (forward ? forwardX : 0.0f) - (backward ? forwardX : 0.0f)
                - (left ? rightX : 0.0f) + (right ? rightX : 0.0f);
        float z = (forward ? forwardZ : 0.0f) - (backward ? forwardZ : 0.0f)
                - (left ? rightZ : 0.0f) + (right ? rightZ : 0.0f);
        float length = (float)Math.sqrt(x * x + z * z);
        if (length > 0.0f) {
            x /= length;
            z /= length;
        }
        return new float[]{x, z};
    }

    static void scrollHotbar(double yOffset) {
        if (inventory == null || yOffset == 0.0) return;
        hotbarScrollRemainder += yOffset;
        int steps = (int)hotbarScrollRemainder;
        if (steps == 0) return;
        inventory.selected = floorMod(inventory.selected - steps, Inventory.HOTBAR_SIZE);
        hotbarScrollRemainder -= steps;
    }

    static void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];
        glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
        int width = Math.max(1, framebufferWidth[0]);
        int height = Math.max(1, framebufferHeight[0]);
        int renderWidth = Math.max(1, (width + pixelScale - 1) / pixelScale);
        int renderHeight = Math.max(1, (height + pixelScale - 1) / pixelScale);
        int uiWidth = Math.max(1, (width + UI_PIXEL_SCALE - 1) / UI_PIXEL_SCALE);
        int uiHeight = Math.max(1, (height + UI_PIXEL_SCALE - 1) / UI_PIXEL_SCALE);
        menuViewportWidth = uiWidth;
        menuViewportHeight = uiHeight;
        glViewport(0, 0, renderWidth, renderHeight);
        float aspect = (float) width / (float) height;
        setPerspective(70.0f, aspect, 0.1f, 1000.0f);
        drawSkyBackground(renderWidth, renderHeight);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glRotatef((float)Math.toDegrees(player.pitch), 1, 0, 0);
        glRotatef((float)Math.toDegrees(player.yaw), 0, 1, 0);
        drawCelestialBodies();
        glTranslatef(-player.x, -(player.y + PLAYER_EYE), -player.z);

        drawWorld(aspect);
        if (screen == Screen.GAME || screen == Screen.PAUSED
                || screen == Screen.INVENTORY || screen == Screen.CRAFTING) {
            drawHeldItem();
        }
        drawPixelArtFrame(width, height, renderWidth, renderHeight);

        // Keep the world pixelated, but draw labels and menus at framebuffer resolution.
        glViewport(0, 0, width, height);
        if (screen == Screen.GAME || screen == Screen.PAUSED
                || screen == Screen.INVENTORY || screen == Screen.CRAFTING) {
            drawHud(uiWidth, uiHeight);
        }
        if (screen != Screen.GAME) {
            drawMenuUi(uiWidth, uiHeight);
        }
    }

    static void drawPixelArtFrame(int width, int height, int renderWidth, int renderHeight) {
        if (pixelArtTexture == 0) {
            pixelArtTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, pixelArtTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            glBindTexture(GL_TEXTURE_2D, pixelArtTexture);
        }
        if (pixelArtTextureWidth != renderWidth || pixelArtTextureHeight != renderHeight) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, renderWidth, renderHeight,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
            pixelArtTextureWidth = renderWidth;
            pixelArtTextureHeight = renderHeight;
        }
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderWidth, renderHeight);

        glViewport(0, 0, width, height);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, pixelArtTexture);
        glColor3f(1.0f, 1.0f, 1.0f);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glBegin(GL_QUADS);
        glTexCoord2f(0.0f, 1.0f); glVertex2f(0.0f, 0.0f);
        glTexCoord2f(1.0f, 1.0f); glVertex2f(width, 0.0f);
        glTexCoord2f(1.0f, 0.0f); glVertex2f(width, height);
        glTexCoord2f(0.0f, 0.0f); glVertex2f(0.0f, height);
        glEnd();
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    static double dayProgress() {
        return (worldClockSeconds % DAY_LENGTH_SECONDS) / DAY_LENGTH_SECONDS;
    }

    static float sunElevation() {
        return (float)Math.sin(dayProgress() * Math.PI * 2.0);
    }

    static float daylightLevel() {
        return 0.42f + 0.58f * Math.max(0.0f, sunElevation());
    }

    static int moonPhase() {
        long day = (long)(worldClockSeconds / DAY_LENGTH_SECONDS);
        return (int)(day % LUNAR_CYCLE_DAYS);
    }

    static float moonIllumination(int phase) {
        return (1.0f + (float)Math.cos(phase * Math.PI / 4.0)) * 0.5f;
    }

    static String skyPhaseName() {
        double progress = dayProgress();
        if (progress > 0.08 && progress < 0.42) return "Day";
        if (progress >= 0.42 && progress < 0.58) return "Sunset";
        return "Dawn";
    }

    static void updateDaylightMeshes() {
        int newBucket = Math.round(daylightLevel() * 8.0f);
        if (newBucket == daylightBucket) return;
        daylightBucket = newBucket;
        if (world != null) {
            for (Chunk chunk : world.chunks.values()) {
                if (chunk.generated) invalidateChunkMesh(chunk);
            }
        }
    }

    static void drawSkyBackground(int width, int height) {
        float elevation = sunElevation();
        float twilight = 1.0f - smoothStep(0.02f, 0.62f, Math.abs(elevation));
        float[] dayZenith = {0.25f, 0.52f, 0.76f};
        float[] dayHorizon = {0.66f, 0.82f, 0.91f};
        float[] warmZenith = {0.30f, 0.35f, 0.50f};
        float[] warmHorizon = {1.00f, 0.48f, 0.25f};

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glBegin(GL_QUADS);
        setSkyColor(dayZenith, warmZenith, twilight);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        setSkyColor(dayHorizon, warmHorizon, twilight);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    static void setSkyColor(float[] dayColor, float[] twilightColor, float twilight) {
        glColor3f(lerp(dayColor[0], twilightColor[0], twilight),
                lerp(dayColor[1], twilightColor[1], twilight),
                lerp(dayColor[2], twilightColor[2], twilight));
    }

    static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    static void drawCelestialBodies() {
        double progress = dayProgress();
        double angle = progress * Math.PI * 2.0;
        float elevation = (float)Math.sin(angle);
        float horizontalX = (float)Math.cos(angle);
        float horizontalZ = (float)-Math.sin(angle);
        float twilight = 1.0f - smoothStep(0.02f, 0.62f, Math.abs(elevation));
        float starAlpha = twilight * 0.82f;

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glPointSize(2.0f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 120; i++) {
            float azimuth = hash2(i * 73 + 31, i * 131 + 17) * (float)(Math.PI * 2.0);
            float height = 0.08f + hash2(i * 197 + 7, i * 43 + 53) * 0.88f;
            float horizontal = (float)Math.sqrt(1.0f - height * height);
            float twinkle = 0.72f + 0.28f * (float)Math.sin(glfwGetTime() * 2.0 + i * 4.1);
            glColor4f(0.78f, 0.88f, 1.0f, starAlpha * twinkle);
            glVertex3f((float)Math.cos(azimuth) * horizontal * 72.0f,
                    height * 72.0f, (float)Math.sin(azimuth) * horizontal * 72.0f);
        }
        glEnd();

        if (elevation > -0.08f) {
            float[] sunDirection = {horizontalX, Math.max(0.04f, elevation), horizontalZ};
            normalize(sunDirection);
            float distance = 72.0f;
            float centerX = sunDirection[0] * distance;
            float centerY = sunDirection[1] * distance;
            float centerZ = sunDirection[2] * distance;
            float[] cameraRight = {(float)Math.cos(player.yaw), 0.0f,
                    (float)Math.sin(player.yaw)};
            float[] cameraUp = {
                    (float)(Math.sin(player.pitch) * Math.sin(player.yaw)),
                    (float)Math.cos(player.pitch),
                    (float)(-Math.sin(player.pitch) * Math.cos(player.yaw))
            };
            float sunGlow = 1.0f - smoothStep(-0.08f, 0.32f, elevation);
            drawDisc(centerX, centerY, centerZ, cameraRight, cameraUp, 5.0f,
                    1.0f, 0.83f + 0.12f * sunGlow, 0.57f + 0.15f * sunGlow, 1.0f);
        }

        if (elevation < 0.28f) {
            float[] moonDirection = {-horizontalX, Math.max(0.08f, -elevation), -horizontalZ};
            normalize(moonDirection);
            float distance = 70.0f;
            float centerX = moonDirection[0] * distance;
            float centerY = moonDirection[1] * distance;
            float centerZ = moonDirection[2] * distance;
            float[] cameraRight = {(float)Math.cos(player.yaw), 0.0f,
                    (float)Math.sin(player.yaw)};
            float[] cameraUp = {
                    (float)(Math.sin(player.pitch) * Math.sin(player.yaw)),
                    (float)Math.cos(player.pitch),
                    (float)(-Math.sin(player.pitch) * Math.cos(player.yaw))
            };
            drawMoon(centerX, centerY, centerZ, cameraRight, cameraUp);
        }

        drawShootingStar();
        glDisable(GL_BLEND);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    static void normalize(float[] vector) {
        float length = (float)Math.sqrt(vector[0] * vector[0]
                + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length == 0.0f) return;
        vector[0] /= length;
        vector[1] /= length;
        vector[2] /= length;
    }

    static void drawDisc(float centerX, float centerY, float centerZ,
                         float[] right, float[] up, float radius,
                         float red, float green, float blue, float alpha) {
        glColor4f(red, green, blue, alpha);
        glBegin(GL_TRIANGLE_FAN);
        glVertex3f(centerX, centerY, centerZ);
        for (int i = 0; i <= 32; i++) {
            double angle = i * Math.PI * 2.0 / 32.0;
            float offsetX = (float)Math.cos(angle) * radius;
            float offsetY = (float)Math.sin(angle) * radius;
            glVertex3f(centerX + right[0] * offsetX + up[0] * offsetY,
                    centerY + right[1] * offsetX + up[1] * offsetY,
                    centerZ + right[2] * offsetX + up[2] * offsetY);
        }
        glEnd();
    }

    static void drawMoon(float centerX, float centerY, float centerZ,
                         float[] right, float[] up) {
        float radius = 3.0f;
        int phase = moonPhase();
        float phaseAngle = phase * (float)Math.PI / 4.0f;
        float terminator = -(float)Math.cos(phaseAngle);
        boolean waxing = phase <= LUNAR_CYCLE_DAYS / 2;
        float illumination = moonIllumination(phase);
        drawDisc(centerX, centerY, centerZ, right, up, radius,
                0.20f + illumination * 0.12f,
                0.25f + illumination * 0.13f,
                0.34f + illumination * 0.14f, 0.96f);

        float pixelStep = 0.60f;
        float halfPixel = 0.22f;
        glBegin(GL_QUADS);
        for (int row = -4; row <= 4; row++) {
            for (int column = -4; column <= 4; column++) {
                float offsetX = column * pixelStep;
                float offsetY = row * pixelStep;
                float farX = Math.abs(offsetX) + halfPixel;
                float farY = Math.abs(offsetY) + halfPixel;
                if (farX * farX + farY * farY >= radius * radius) continue;

                float normalizedX = offsetX / radius;
                boolean lit = waxing ? normalizedX >= terminator : normalizedX <= -terminator;
                if (lit) {
                    float variation = ((row * 7 + column * 11) & 3) * 0.015f;
                    glColor4f(0.78f + variation, 0.84f + variation,
                            0.92f + variation, 0.96f);
                } else {
                    glColor4f(0.27f, 0.32f, 0.42f, 0.96f);
                }
                drawBillboardQuad(centerX, centerY, centerZ, right, up,
                        offsetX, offsetY, halfPixel);
            }
        }
        glEnd();

        drawBillboardPixel(centerX, centerY, centerZ, right, up,
                -0.85f, 0.65f, 0.16f, 0.53f, 0.60f, 0.71f);
        drawBillboardPixel(centerX, centerY, centerZ, right, up,
                0.95f, 0.15f, 0.19f, 0.57f, 0.64f, 0.74f);
        drawBillboardPixel(centerX, centerY, centerZ, right, up,
                0.10f, -1.05f, 0.23f, 0.59f, 0.66f, 0.76f);
    }

    static void drawBillboardPixel(float centerX, float centerY, float centerZ,
                                   float[] right, float[] up,
                                   float offsetX, float offsetY, float halfSize,
                                   float red, float green, float blue) {
        glColor4f(red, green, blue, 0.95f);
        glBegin(GL_QUADS);
        drawBillboardQuad(centerX, centerY, centerZ, right, up,
                offsetX, offsetY, halfSize);
        glEnd();
    }

    static void drawBillboardQuad(float centerX, float centerY, float centerZ,
                                  float[] right, float[] up,
                                  float offsetX, float offsetY, float halfSize) {
        float pixelCenterX = centerX + right[0] * offsetX + up[0] * offsetY;
        float pixelCenterY = centerY + right[1] * offsetX + up[1] * offsetY;
        float pixelCenterZ = centerZ + right[2] * offsetX + up[2] * offsetY;
        glVertex3f(pixelCenterX - right[0] * halfSize - up[0] * halfSize,
                pixelCenterY - right[1] * halfSize - up[1] * halfSize,
                pixelCenterZ - right[2] * halfSize - up[2] * halfSize);
        glVertex3f(pixelCenterX + right[0] * halfSize - up[0] * halfSize,
                pixelCenterY + right[1] * halfSize - up[1] * halfSize,
                pixelCenterZ + right[2] * halfSize - up[2] * halfSize);
        glVertex3f(pixelCenterX + right[0] * halfSize + up[0] * halfSize,
                pixelCenterY + right[1] * halfSize + up[1] * halfSize,
                pixelCenterZ + right[2] * halfSize + up[2] * halfSize);
        glVertex3f(pixelCenterX - right[0] * halfSize + up[0] * halfSize,
                pixelCenterY - right[1] * halfSize + up[1] * halfSize,
                pixelCenterZ - right[2] * halfSize + up[2] * halfSize);
    }

    static void drawShootingStar() {
        double starTime = worldClockSeconds % DAY_LENGTH_SECONDS
                - DAY_LENGTH_SECONDS * 0.52;
        if (starTime > 1.1) return;
        if (starTime < 0.0) return;
        float progress = (float)(starTime / 1.1);
        float x = 48.0f - progress * 24.0f;
        float y = 34.0f + progress * 20.0f;
        float z = -48.0f;
        float alpha = (1.0f - progress) * 0.95f;
        glLineWidth(2.0f);
        glBegin(GL_LINES);
        glColor4f(0.55f, 0.72f, 1.0f, alpha * 0.3f);
        glVertex3f(x + 5.0f, y - 4.0f, z);
        glColor4f(0.92f, 0.97f, 1.0f, alpha);
        glVertex3f(x, y, z);
        glEnd();
        glLineWidth(1.0f);
    }

    static void drawWorld(float aspect) {
        int pcx = (int)Math.floor(player.x / CHUNK_SIZE);
        int pcz = (int)Math.floor(player.z / CHUNK_SIZE);
        ArrayList<Chunk> visibleChunks = new ArrayList<>();

        for (int cz = pcz - RENDER_DISTANCE; cz <= pcz + RENDER_DISTANCE; cz++) {
            for (int cx = pcx - RENDER_DISTANCE; cx <= pcx + RENDER_DISTANCE; cx++) {
                Chunk c = getChunk(cx, cz, false);
                if (c == null || !c.generated) continue;
                if (!isChunkInView(c, aspect)) continue;
                visibleChunks.add(c);
            }
        }
        visibleChunks.sort(Comparator.comparingDouble(chunk -> {
            float dx = (chunk.cx + 0.5f) * CHUNK_SIZE - player.x;
            float dz = (chunk.cz + 0.5f) * CHUNK_SIZE - player.z;
            return dx * dx + dz * dz;
        }));

        int meshBuilds = 0;
        for (Chunk chunk : visibleChunks) {
            if ((chunk.displayList == 0 || chunk.meshDirty)
                    && meshBuilds >= CHUNK_MESH_BUILD_BUDGET) continue;
            if (chunk.displayList == 0 || chunk.meshDirty) meshBuilds++;
            compileChunkMesh(chunk);
            glCallList(chunk.displayList);
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (int i = visibleChunks.size() - 1; i >= 0; i--) {
            Chunk chunk = visibleChunks.get(i);
            if (chunk.displayList != 0 && !chunk.meshDirty) {
                glCallList(chunk.transparentDisplayList);
            }
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    static void drawHeldItem() {
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        drawCuboid(0.34f, -0.72f, -1.16f,
                0.60f, -0.34f, -0.86f, 0.22f, 0.34f, 0.56f);
        drawCuboid(0.41f, -0.50f, -1.25f,
                0.57f, -0.34f, -1.10f, 0.82f, 0.61f, 0.43f);

        ItemType held = inventory.selectedItem();
        if (held == ItemType.TORCH) {
            drawCuboid(0.455f, -0.43f, -1.43f,
                    0.505f, -0.18f, -1.32f, 0.42f, 0.26f, 0.12f);
            drawCuboid(0.44f, -0.21f, -1.42f,
                    0.52f, -0.14f, -1.32f, 1.0f, 0.42f, 0.08f);
            drawCuboid(0.46f, -0.15f, -1.41f,
                    0.50f, -0.10f, -1.33f, 1.0f, 0.86f, 0.40f);
        } else {
            float[] color = held == null ? new float[]{0.5f, 0.5f, 0.5f, 1.0f} : itemColor(held);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(color[0], color[1], color[2],
                    held == ItemType.WATER ? 0.68f : 1.0f);
            drawHeldCube(0.43f, -0.43f, -1.44f,
                    0.59f, -0.27f, -1.28f);
            glDisable(GL_BLEND);
        }

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    static void drawHeldCube(float x0, float y0, float z0,
                             float x1, float y1, float z1) {
        glBegin(GL_QUADS);
        glVertex3f(x0, y0, z1);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x1, y1, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y0, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z1);
        glEnd();
    }

    static boolean isChunkInView(Chunk chunk, float aspect) {
        float centerX = (chunk.cx + 0.5f) * CHUNK_SIZE;
        float centerY = WORLD_HEIGHT * 0.5f;
        float centerZ = (chunk.cz + 0.5f) * CHUNK_SIZE;
        float dx = centerX - player.x;
        float dy = centerY - (player.y + PLAYER_EYE);
        float dz = centerZ - player.z;

        float halfX = CHUNK_SIZE * 0.5f;
        float halfY = WORLD_HEIGHT * 0.5f;
        float halfZ = CHUNK_SIZE * 0.5f;
        float radius = (float)Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
        float nearestX = Math.max(0.0f, Math.abs(dx) - halfX);
        float nearestZ = Math.max(0.0f, Math.abs(dz) - halfZ);
        if (nearestX * nearestX + nearestZ * nearestZ > VIEW_DISTANCE * VIEW_DISTANCE) return false;

        float yaw = player.yaw;
        float pitch = player.pitch;
        float sinYaw = (float)Math.sin(yaw);
        float cosYaw = (float)Math.cos(yaw);
        float sinPitch = (float)Math.sin(pitch);
        float cosPitch = (float)Math.cos(pitch);
        float forwardX = cosPitch * sinYaw;
        float forwardY = -sinPitch;
        float forwardZ = -cosPitch * cosYaw;
        float rightX = cosYaw;
        float rightZ = sinYaw;
        float upX = sinPitch * sinYaw;
        float upY = cosPitch;
        float upZ = -sinPitch * cosYaw;
        float tanHalfVertical = (float)Math.tan(Math.toRadians(70.0) * 0.5);
        float tanHalfHorizontal = tanHalfVertical * aspect;

        return !outsideFrustumPlane(forwardX, forwardY, forwardZ, dx, dy, dz,
                        halfX, halfY, halfZ)
                && !outsideFrustumPlane(forwardX * tanHalfHorizontal + rightX,
                        forwardY * tanHalfHorizontal, forwardZ * tanHalfHorizontal + rightZ,
                        dx, dy, dz, halfX, halfY, halfZ)
                && !outsideFrustumPlane(forwardX * tanHalfHorizontal - rightX,
                        forwardY * tanHalfHorizontal, forwardZ * tanHalfHorizontal - rightZ,
                        dx, dy, dz, halfX, halfY, halfZ)
                && !outsideFrustumPlane(forwardX * tanHalfVertical + upX,
                        forwardY * tanHalfVertical + upY, forwardZ * tanHalfVertical + upZ,
                        dx, dy, dz, halfX, halfY, halfZ)
                && !outsideFrustumPlane(forwardX * tanHalfVertical - upX,
                        forwardY * tanHalfVertical - upY, forwardZ * tanHalfVertical - upZ,
                        dx, dy, dz, halfX, halfY, halfZ);
    }

    static boolean outsideFrustumPlane(float normalX, float normalY, float normalZ,
                                       float dx, float dy, float dz,
                                       float halfX, float halfY, float halfZ) {
        float distance = normalX * dx + normalY * dy + normalZ * dz;
        float radius = Math.abs(normalX) * halfX
                + Math.abs(normalY) * halfY
                + Math.abs(normalZ) * halfZ;
        return distance + radius < 0.0f;
    }

    static void compileChunkMesh(Chunk chunk) {
        if (chunk.displayList == 0 || chunk.transparentDisplayList == 0) {
            chunk.displayList = glGenLists(2);
            if (chunk.displayList == 0) {
                throw new IllegalStateException("Could not allocate chunk display list");
            }
            chunk.transparentDisplayList = chunk.displayList + 1;
            chunk.meshDirty = true;
        }
        if (chunk.meshDirty) {
            rebuildChunkLighting(chunk);
            glNewList(chunk.displayList, GL_COMPILE);
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        BlockType block = getBlock(chunk, x, y, z);
                        if (block != BlockType.AIR && block != BlockType.WATER) {
                            int index = chunk.idx(x, y, z);
                            drawBlock(chunk.cx * CHUNK_SIZE + x, y,
                                    chunk.cz * CHUNK_SIZE + z, block,
                                    chunk.sunlight[index] & 0xff, chunk.torchlight[index] & 0xff,
                                    chunk.blockStates[index] & 0xff);
                        }
                    }
                }
            }
            glEndList();

            glNewList(chunk.transparentDisplayList, GL_COMPILE);
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        if (getBlock(chunk, x, y, z) == BlockType.WATER) {
                            drawWaterBlock(chunk.cx * CHUNK_SIZE + x, y,
                                    chunk.cz * CHUNK_SIZE + z);
                        }
                    }
                }
            }
            glEndList();
            chunk.meshDirty = false;
        }
    }

    static void invalidateChunkMesh(Chunk chunk) {
        if (chunk == null) return;
        chunk.meshDirty = true;
        if (chunk.displayList != 0) {
            glDeleteLists(chunk.displayList, 2);
            chunk.displayList = 0;
            chunk.transparentDisplayList = 0;
        }
    }

    static void invalidateChunkAndNeighbors(Chunk chunk, int localX, int localZ) {
        invalidateChunkMesh(chunk);
        if (localX == 0) invalidateChunkMesh(getChunk(chunk.cx - 1, chunk.cz, false));
        if (localX == CHUNK_SIZE - 1) invalidateChunkMesh(getChunk(chunk.cx + 1, chunk.cz, false));
        if (localZ == 0) invalidateChunkMesh(getChunk(chunk.cx, chunk.cz - 1, false));
        if (localZ == CHUNK_SIZE - 1) invalidateChunkMesh(getChunk(chunk.cx, chunk.cz + 1, false));
    }

    static void invalidateChunkBorders(Chunk chunk) {
        invalidateChunkMesh(chunk);
        invalidateChunkMesh(getChunk(chunk.cx - 1, chunk.cz, false));
        invalidateChunkMesh(getChunk(chunk.cx + 1, chunk.cz, false));
        invalidateChunkMesh(getChunk(chunk.cx, chunk.cz - 1, false));
        invalidateChunkMesh(getChunk(chunk.cx, chunk.cz + 1, false));
    }

    static void invalidateTorchLightingAround(Chunk center) {
        for (int cz = center.cz - 1; cz <= center.cz + 1; cz++) {
            for (int cx = center.cx - 1; cx <= center.cx + 1; cx++) {
                invalidateChunkMesh(getChunk(cx, cz, false));
            }
        }
    }

    static void rebuildChunkLighting(Chunk chunk) {
        java.util.Arrays.fill(chunk.sunlight, (byte)0);
        java.util.Arrays.fill(chunk.torchlight, (byte)0);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int skyLevel = 15;
                for (int y = WORLD_HEIGHT - 1; y >= 0; y--) {
                    BlockType block = getBlock(chunk, x, y, z);
                    int index = chunk.idx(x, y, z);
                    chunk.sunlight[index] = (byte)skyLevel;
                    if (block == BlockType.LEAF) {
                        skyLevel = Math.max(0, skyLevel - 3);
                    } else if (block != BlockType.AIR && block != BlockType.WATER
                            && block != BlockType.TORCH) {
                        skyLevel = 0;
                    }
                }
            }
        }

        for (int cz = chunk.cz - 1; cz <= chunk.cz + 1; cz++) {
            for (int cx = chunk.cx - 1; cx <= chunk.cx + 1; cx++) {
                Chunk sourceChunk = getChunk(cx, cz, false);
                if (sourceChunk == null || !sourceChunk.generated) continue;
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int y = 0; y < WORLD_HEIGHT; y++) {
                        for (int z = 0; z < CHUNK_SIZE; z++) {
                            if (getBlock(sourceChunk, x, y, z) != BlockType.TORCH) continue;
                            spreadTorchLight(chunk,
                                    sourceChunk.cx * CHUNK_SIZE + x, y,
                                    sourceChunk.cz * CHUNK_SIZE + z);
                        }
                    }
                }
            }
        }
    }

    static void spreadTorchLight(Chunk target, int sourceX, int sourceY, int sourceZ) {
        int radius = 8;
        for (int dx = -radius; dx <= radius; dx++) {
            int worldX = sourceX + dx;
            int localX = worldX - target.cx * CHUNK_SIZE;
            if (localX < 0 || localX >= CHUNK_SIZE) continue;
            for (int dy = -radius; dy <= radius; dy++) {
                int y = sourceY + dy;
                if (y < 0 || y >= WORLD_HEIGHT) continue;
                for (int dz = -radius; dz <= radius; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance > radius) continue;
                    int localZ = sourceZ + dz - target.cz * CHUNK_SIZE;
                    if (localZ < 0 || localZ >= CHUNK_SIZE) continue;
                    int lightLevel = 14 - distance;
                    int index = target.idx(localX, y, localZ);
                    if ((target.torchlight[index] & 0xff) < lightLevel) {
                        target.torchlight[index] = (byte)lightLevel;
                    }
                }
            }
        }
    }

    static void disposeChunkMeshes(World targetWorld) {
        for (Chunk chunk : targetWorld.chunks.values()) {
            if (chunk.displayList != 0) {
                glDeleteLists(chunk.displayList, 2);
                chunk.displayList = 0;
                chunk.transparentDisplayList = 0;
                chunk.meshDirty = true;
            }
        }
    }

    static void drawBlock(int x, int y, int z, BlockType t, int sunlight, int torchlight,
                          int state) {
        if (t == BlockType.TORCH) {
            drawTorch(x, y, z, state);
            return;
        }
        if (t == BlockType.DOOR) {
            drawDoor(x, y, z, state);
            return;
        }
        if (t == BlockType.BED) {
            drawBed(x, y, z, state);
            return;
        }
        if (t == BlockType.CRAFTING_TABLE) {
            drawCraftingTable(x, y, z);
            return;
        }

        boolean frontVisible = isFaceVisible(x, y, z + 1);
        boolean backVisible = isFaceVisible(x, y, z - 1);
        boolean leftVisible = isFaceVisible(x - 1, y, z);
        boolean rightVisible = isFaceVisible(x + 1, y, z);
        boolean topVisible = isFaceVisible(x, y + 1, z);
        boolean bottomVisible = isFaceVisible(x, y - 1, z);
        if (!frontVisible && !backVisible && !leftVisible && !rightVisible
                && !topVisible && !bottomVisible) return;

        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1;
        float y1 = y + 1;
        float z1 = z + 1;

        float[] color = colorOf(t);
        float illumination = Math.min(1.0f, 0.20f + daylightLevel() * sunlight * (0.68f / 15.0f)
                + torchlight * (0.82f / 15.0f));
        glBegin(GL_QUADS);
        if (frontVisible) {
            setBlockFaceColor(color, illumination * 0.82f);
            glVertex3f(x0, y0, z1);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x0, y1, z1);
        }
        if (backVisible) {
            setBlockFaceColor(color, illumination * 0.68f);
            glVertex3f(x1, y0, z0);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y1, z0);
            glVertex3f(x1, y1, z0);
        }
        if (leftVisible) {
            setBlockFaceColor(color, illumination * 0.76f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y0, z1);
            glVertex3f(x0, y1, z1);
            glVertex3f(x0, y1, z0);
        }
        if (rightVisible) {
            setBlockFaceColor(color, illumination * 0.88f);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y1, z0);
            glVertex3f(x1, y1, z1);
        }
        if (topVisible) {
            setBlockFaceColor(color, illumination);
            glVertex3f(x0, y1, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x1, y1, z0);
            glVertex3f(x0, y1, z0);
        }
        if (bottomVisible) {
            setBlockFaceColor(color, illumination * 0.55f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y0, z1);
            glVertex3f(x0, y0, z1);
        }
        glEnd();
    }

    static void drawTorch(int x, int y, int z, int state) {
        float baseX = x + 0.5f;
        float baseZ = z + 0.5f;
        float tipX = baseX;
        float tipZ = baseZ;
        if (state == 1) tipX += 0.22f;
        else if (state == 2) tipX -= 0.22f;
        else if (state == 3) tipZ += 0.22f;
        else if (state == 4) tipZ -= 0.22f;
        drawAngledRod(baseX, y + 0.12f, baseZ, tipX, y + 0.76f, tipZ,
                0.055f, 0.34f, 0.22f, 0.10f);
        drawCuboid(tipX - 0.12f, y + 0.66f, tipZ - 0.12f,
                tipX + 0.12f, y + 0.83f, tipZ + 0.12f, 1.0f, 0.38f, 0.06f);
        drawCuboid(tipX - 0.075f, y + 0.81f, tipZ - 0.075f,
                tipX + 0.075f, y + 0.94f, tipZ + 0.075f, 1.0f, 0.82f, 0.28f);
    }

    static void drawAngledRod(float x0, float y0, float z0, float x1, float y1, float z1,
                              float halfWidth, float red, float green, float blue) {
        glPushMatrix();
        glTranslatef(x0, y0, z0);
        float dx = x1 - x0;
        float dz = z1 - z0;
        float angle = (float)Math.toDegrees(Math.atan2(Math.sqrt(dx * dx + dz * dz), y1 - y0));
        float horizontalAngle = (float)Math.toDegrees(Math.atan2(dx, dz));
        glRotatef(horizontalAngle, 0.0f, 1.0f, 0.0f);
        glRotatef(angle, 1.0f, 0.0f, 0.0f);
        drawCuboid(-halfWidth, 0.0f, -halfWidth, halfWidth, y1 - y0, halfWidth,
                red, green, blue);
        glPopMatrix();
    }

    static void drawDoor(int x, int y, int z, int state) {
        boolean open = (state & 4) != 0;
        boolean wideAlongX = (state & 1) == 0;
        float x0;
        float x1;
        float z0;
        float z1;
        if (wideAlongX == open) {
            x0 = x + 0.43f;
            x1 = x + 0.57f;
            z0 = z + 0.06f;
            z1 = z + 0.94f;
        } else {
            x0 = x + 0.06f;
            x1 = x + 0.94f;
            z0 = z + 0.43f;
            z1 = z + 0.57f;
        }
        drawCuboid(x0, y + 0.02f, z0, x1, y + 0.98f, z1,
                0.43f, 0.26f, 0.12f);
        if (wideAlongX == open) {
            drawCuboid(x + 0.47f, y + 0.15f, z + 0.22f,
                    x + 0.53f, y + 0.25f, z + 0.78f, 0.28f, 0.16f, 0.08f);
        } else {
            drawCuboid(x + 0.22f, y + 0.15f, z + 0.47f,
                    x + 0.78f, y + 0.25f, z + 0.53f, 0.28f, 0.16f, 0.08f);
        }
        float knobX = x + 0.5f;
        float knobZ = z + 0.5f;
        if (wideAlongX == open) knobZ = z + 0.50f;
        else knobX = x + 0.50f;
        drawCuboid(knobX - 0.04f, y + 0.48f, knobZ - 0.04f,
                knobX + 0.04f, y + 0.58f, knobZ + 0.04f, 0.88f, 0.68f, 0.22f);
    }

    static void drawBed(int x, int y, int z, int state) {
        int facing = state & 3;
        boolean head = (state & 4) != 0;
        drawCuboid(x + 0.08f, y + 0.10f, z + 0.08f,
                x + 0.92f, y + 0.32f, z + 0.92f, 0.42f, 0.25f, 0.12f);
        drawCuboid(x + 0.04f, y + 0.32f, z + 0.04f,
                x + 0.96f, y + 0.58f, z + 0.96f, 0.72f, 0.10f, 0.12f);
        if (!head) return;
        switch (facing) {
            case 0 -> drawCuboid(x + 0.12f, y + 0.39f, z + 0.06f,
                    x + 0.88f, y + 0.54f, z + 0.34f, 0.92f, 0.88f, 0.76f);
            case 1 -> drawCuboid(x + 0.66f, y + 0.39f, z + 0.12f,
                    x + 0.94f, y + 0.54f, z + 0.88f, 0.92f, 0.88f, 0.76f);
            case 2 -> drawCuboid(x + 0.12f, y + 0.39f, z + 0.66f,
                    x + 0.88f, y + 0.54f, z + 0.94f, 0.92f, 0.88f, 0.76f);
            default -> drawCuboid(x + 0.06f, y + 0.39f, z + 0.12f,
                    x + 0.34f, y + 0.54f, z + 0.88f, 0.92f, 0.88f, 0.76f);
        }
    }

    static void drawCraftingTable(int x, int y, int z) {
        drawCuboid(x + 0.05f, y + 0.03f, z + 0.05f,
                x + 0.95f, y + 0.90f, z + 0.95f, 0.48f, 0.31f, 0.16f);
        drawCuboid(x + 0.03f, y + 0.88f, z + 0.03f,
                x + 0.97f, y + 0.98f, z + 0.97f, 0.69f, 0.48f, 0.25f);
        drawCuboid(x + 0.18f, y + 0.91f, z + 0.18f,
                x + 0.25f, y + 0.98f, z + 0.82f, 0.30f, 0.20f, 0.11f);
        drawCuboid(x + 0.43f, y + 0.91f, z + 0.18f,
                x + 0.50f, y + 0.98f, z + 0.82f, 0.30f, 0.20f, 0.11f);
        drawCuboid(x + 0.68f, y + 0.91f, z + 0.18f,
                x + 0.75f, y + 0.98f, z + 0.82f, 0.30f, 0.20f, 0.11f);
        drawCuboid(x + 0.18f, y + 0.91f, z + 0.18f,
                x + 0.82f, y + 0.98f, z + 0.25f, 0.30f, 0.20f, 0.11f);
        drawCuboid(x + 0.18f, y + 0.91f, z + 0.43f,
                x + 0.82f, y + 0.98f, z + 0.50f, 0.30f, 0.20f, 0.11f);
        drawCuboid(x + 0.18f, y + 0.91f, z + 0.68f,
                x + 0.82f, y + 0.98f, z + 0.75f, 0.30f, 0.20f, 0.11f);
    }

    static void drawCuboid(float x0, float y0, float z0,
                           float x1, float y1, float z1,
                           float red, float green, float blue) {
        glBegin(GL_QUADS);
        glColor3f(red * 0.82f, green * 0.82f, blue * 0.82f);
        glVertex3f(x0, y0, z1);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z1);
        glColor3f(red * 0.68f, green * 0.68f, blue * 0.68f);
        glVertex3f(x1, y0, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x1, y1, z0);
        glColor3f(red * 0.76f, green * 0.76f, blue * 0.76f);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y0, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z0);
        glColor3f(red * 0.88f, green * 0.88f, blue * 0.88f);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z1);
        glColor3f(red, green, blue);
        glVertex3f(x0, y1, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z0);
        glVertex3f(x0, y1, z0);
        glColor3f(red * 0.55f, green * 0.55f, blue * 0.55f);
        glVertex3f(x0, y0, z0);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z1);
        glEnd();
    }

    static void drawWaterBlock(int x, int y, int z) {
        float x0 = x;
        float x1 = x + 1.0f;
        float y0 = y;
        float y1 = y + 0.88f;
        float z0 = z;
        float z1 = z + 1.0f;
        glBegin(GL_QUADS);
        if (isWaterFaceVisible(x, y, z + 1)) {
            glColor4f(0.18f, 0.48f, 0.78f, 0.50f);
            glVertex3f(x0, y0, z1);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x0, y1, z1);
        }
        if (isWaterFaceVisible(x, y, z - 1)) {
            glColor4f(0.14f, 0.40f, 0.70f, 0.50f);
            glVertex3f(x1, y0, z0);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y1, z0);
            glVertex3f(x1, y1, z0);
        }
        if (isWaterFaceVisible(x - 1, y, z)) {
            glColor4f(0.16f, 0.44f, 0.74f, 0.50f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y0, z1);
            glVertex3f(x0, y1, z1);
            glVertex3f(x0, y1, z0);
        }
        if (isWaterFaceVisible(x + 1, y, z)) {
            glColor4f(0.20f, 0.50f, 0.80f, 0.50f);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y1, z0);
            glVertex3f(x1, y1, z1);
        }
        if (isWaterFaceVisible(x, y + 1, z)) {
            glColor4f(0.34f, 0.66f, 0.90f, 0.68f);
            glVertex3f(x0, y1, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x1, y1, z0);
            glVertex3f(x0, y1, z0);
        }
        if (isWaterFaceVisible(x, y - 1, z)) {
            glColor4f(0.10f, 0.32f, 0.62f, 0.50f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y0, z1);
            glVertex3f(x0, y0, z1);
        }
        glEnd();
    }

    static boolean isWaterFaceVisible(int x, int y, int z) {
        BlockType neighbor = getBlock(x, y, z);
        return neighbor == BlockType.AIR || neighbor == BlockType.LEAF
                || neighbor == BlockType.TORCH || neighbor == BlockType.DOOR
                || neighbor == BlockType.BED;
    }

    static void setBlockFaceColor(float[] color, float brightness) {
        glColor4f(color[0] * brightness, color[1] * brightness,
                color[2] * brightness, color[3]);
    }

    static void drawHud(int width, int height) {
        float scale = Math.min(width / (float)SCREEN_W, height / (float)SCREEN_H);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float crosshairThin = Math.max(1.0f, Math.round(1.5f * scale));
        float crosshairHalf = Math.max(3.0f, Math.round(8.0f * scale));
        glColor4f(0.05f, 0.05f, 0.05f, 0.75f);
        glBegin(GL_QUADS);
        glVertex2f(centerX - crosshairThin, centerY - crosshairHalf);
        glVertex2f(centerX + crosshairThin, centerY - crosshairHalf);
        glVertex2f(centerX + crosshairThin, centerY + crosshairHalf);
        glVertex2f(centerX - crosshairThin, centerY + crosshairHalf);
        glVertex2f(centerX - crosshairHalf, centerY - crosshairThin);
        glVertex2f(centerX + crosshairHalf, centerY - crosshairThin);
        glVertex2f(centerX + crosshairHalf, centerY + crosshairThin);
        glVertex2f(centerX - crosshairHalf, centerY + crosshairThin);
        glEnd();

        int slotSize = Math.max(10, Math.round(44.0f * scale));
        int gap = Math.max(1, Math.round(3.0f * scale));
        int barWidth = slotSize * Inventory.HOTBAR_SIZE + gap * (Inventory.HOTBAR_SIZE - 1);
        int startX = (width - barWidth) / 2;
        int top = height - slotSize - Math.max(5, Math.round(18.0f * scale));
        int iconOffset = Math.round(13.0f * scale);
        int iconSize = Math.max(4, Math.round(18.0f * scale));
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int x = startX + i * (slotSize + gap);
            glColor4f(0.04f, 0.04f, 0.04f, 0.78f);
            drawHudRect(x, top, slotSize, slotSize);
            glColor4f(i == inventory.selected ? 1.0f : 0.55f,
                    i == inventory.selected ? 0.85f : 0.55f,
                    i == inventory.selected ? 0.25f : 0.55f, 0.95f);
            drawHudOutline(x, top, slotSize, slotSize,
                    Math.max(1, Math.round((i == inventory.selected ? 3.0f : 1.0f) * scale)));

            ItemType item = inventory.items[i];
            if (item != null) {
                float[] color = itemColor(item);
                glColor4f(color[0], color[1], color[2], color[3]);
                drawHudRect(x + iconOffset, top + Math.round(11.0f * scale), iconSize, iconSize);
                int count = inventory.counts[i];
                if (count > 1) {
                    drawPixelText(Integer.toString(count), x + slotSize - 12.0f * scale,
                            top + slotSize - 10.0f * scale, Math.max(0.48f, 0.70f * scale),
                            1.0f, 1.0f, 0.88f, 1.0f);
                }
            }
        }

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    static void drawHudRect(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }

    static void drawHudOutline(float x, float y, float width, float height, float thickness) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + thickness);
        glVertex2f(x, y + thickness);
        glVertex2f(x, y + height - thickness);
        glVertex2f(x + width, y + height - thickness);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glVertex2f(x, y + thickness);
        glVertex2f(x + thickness, y + thickness);
        glVertex2f(x + thickness, y + height - thickness);
        glVertex2f(x, y + height - thickness);
        glVertex2f(x + width - thickness, y + thickness);
        glVertex2f(x + width, y + thickness);
        glVertex2f(x + width, y + height - thickness);
        glVertex2f(x + width - thickness, y + height - thickness);
        glEnd();
    }

    static void drawMenuUi(int width, int height) {
        menuViewportWidth = width;
        menuViewportHeight = height;
        menuButtons.clear();
        saveRows.clear();

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.02f, 0.04f, 0.08f, screen == Screen.PAUSED ? 0.48f : 0.56f);
        drawHudRect(0, 0, width, height);

        if (screen == Screen.INVENTORY || screen == Screen.CRAFTING) {
            drawInventoryUi(width, height, screen == Screen.CRAFTING);
            glDisable(GL_BLEND);
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glPopMatrix();
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            return;
        }

        float panelWidth = Math.min(width - 12.0f, 292.0f);
        float panelHeight = Math.min(height - 12.0f, 224.0f);
        panelWidth = Math.max(120.0f, panelWidth);
        panelHeight = Math.max(120.0f, panelHeight);
        float panelX = (width - panelWidth) * 0.5f;
        float panelY = (height - panelHeight) * 0.5f;
        glColor4f(0.05f, 0.08f, 0.12f, 0.90f);
        drawHudRect(panelX, panelY, panelWidth, panelHeight);
        glColor4f(0.74f, 0.82f, 0.91f, 0.95f);
        drawHudOutline(panelX, panelY, panelWidth, panelHeight, 2.0f);

        switch (screen) {
            case TITLE -> drawTitleMenu(width, panelX, panelY, panelWidth, panelHeight);
            case WORLD_SELECT -> drawWorldSelectMenu(width, panelX, panelY, panelWidth, panelHeight);
            case CREATE_WORLD -> drawCreateWorldMenu(width, panelX, panelY, panelWidth, panelHeight);
            case OPTIONS -> drawOptionsMenu(width, panelX, panelY, panelWidth, panelHeight);
            case PAUSED -> drawPauseMenu(width, panelX, panelY, panelWidth, panelHeight);
            case GAME, INVENTORY, CRAFTING -> { }
        }

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    static void drawTitleMenu(int width, float panelX, float panelY,
                              float panelWidth, float panelHeight) {
        drawCenteredPixelText("MINEWORLD", width * 0.5f, panelY + 20.0f,
                2.4f, 0.96f, 0.87f, 0.55f, 1.0f);
        drawCenteredPixelText("BLOCK SANDBOX", width * 0.5f, panelY + 44.0f,
                0.95f, 0.78f, 0.86f, 0.94f, 1.0f);
        float buttonY = panelY + 72.0f;
        drawMenuButton(width, "SINGLEPLAYER", "worlds", buttonY);
        drawMenuButton(width, "OPTIONS", "options", buttonY + 30.0f);
        drawMenuButton(width, "QUIT GAME", "quit", buttonY + 60.0f);
    }

    static void drawWorldSelectMenu(int width, float panelX, float panelY,
                                    float panelWidth, float panelHeight) {
        String heading = saveEntries.isEmpty() ? "SELECT WORLD" : "SELECT WORLD  "
                + (selectedSaveIndex + 1) + "/" + saveEntries.size();
        drawCenteredPixelText(heading, width * 0.5f, panelY + 12.0f,
                1.55f, 0.96f, 0.87f, 0.55f, 1.0f);

        float rowX = panelX + 9.0f;
        float rowWidth = panelWidth - 18.0f;
        float rowHeight = 17.0f;
        float rowY = panelY + 34.0f;
        int visibleRows = Math.min(5, Math.max(0, (int)((panelHeight - 112.0f) / rowHeight)));
        if (selectedSaveIndex >= 0) {
            if (selectedSaveIndex < saveListOffset) saveListOffset = selectedSaveIndex;
            if (selectedSaveIndex >= saveListOffset + Math.max(1, visibleRows)) {
                saveListOffset = selectedSaveIndex - Math.max(1, visibleRows) + 1;
            }
        }
        int end = Math.min(saveEntries.size(), saveListOffset + visibleRows);
        for (int index = saveListOffset; index < end; index++) {
            SaveEntry entry = saveEntries.get(index);
            MenuButton row = new MenuButton(entry.name(), "", rowX,
                    rowY + (index - saveListOffset) * rowHeight, rowWidth, rowHeight - 1.0f);
            saveRows.add(row);
            boolean hovered = menuButtonContains(row, menuCursorPosition(window));
            if (index == selectedSaveIndex || hovered) {
                glColor4f(0.29f, 0.43f, 0.30f, 0.90f);
            } else {
                glColor4f(0.10f, 0.15f, 0.18f, 0.86f);
            }
            drawHudRect(row.x(), row.y(), row.width(), row.height());
            glColor4f(0.43f, 0.56f, 0.50f, 0.95f);
            drawHudOutline(row.x(), row.y(), row.width(), row.height(), 1.0f);
            drawPixelText(entry.name(), row.x() + 8.0f, row.y() + 5.0f,
                    0.92f, 0.93f, 0.96f, 0.90f, 1.0f);
        }
        if (saveEntries.isEmpty()) {
            drawCenteredPixelText("NO SAVES YET", width * 0.5f, rowY + 14.0f,
                    1.0f, 0.70f, 0.76f, 0.82f, 1.0f);
        }

        float buttonY = panelY + panelHeight - 73.0f;
        drawMenuButton(width, "CREATE NEW WORLD", "create", buttonY);
        drawMenuButton(width, "PLAY SELECTED", "play", buttonY + 25.0f);
        drawMenuButton(width, "BACK", "back", buttonY + 50.0f);
        drawPixelText("WORLD SAVE FILES", panelX + 9.0f,
                panelY + panelHeight - 7.0f, 0.54f, 0.62f, 0.70f, 0.76f, 1.0f);
    }

    static void drawCreateWorldMenu(int width, float panelX, float panelY,
                                    float panelWidth, float panelHeight) {
        drawCenteredPixelText("CREATE NEW WORLD", width * 0.5f, panelY + 25.0f,
                1.5f, 0.96f, 0.87f, 0.55f, 1.0f);
        drawPixelText("WORLD NAME", panelX + 18.0f, panelY + 67.0f,
                0.95f, 0.82f, 0.88f, 0.93f, 1.0f);
        float fieldX = panelX + 14.0f;
        float fieldY = panelY + 83.0f;
        float fieldWidth = panelWidth - 28.0f;
        glColor4f(0.02f, 0.04f, 0.05f, 0.94f);
        drawHudRect(fieldX, fieldY, fieldWidth, 24.0f);
        glColor4f(0.88f, 0.78f, 0.47f, 1.0f);
        drawHudOutline(fieldX, fieldY, fieldWidth, 24.0f, 1.0f);
        drawPixelText(newWorldName + "_", fieldX + 7.0f, fieldY + 8.0f,
                1.05f, 0.96f, 0.96f, 0.88f, 1.0f);
        drawMenuButton(width, "CREATE", "create_world", panelY + 128.0f);
        drawMenuButton(width, "BACK", "back", panelY + 158.0f);
        drawCenteredPixelText("LETTERS NUMBERS SPACE - _", width * 0.5f,
                panelY + panelHeight - 14.0f, 0.62f, 0.62f, 0.70f, 0.76f, 1.0f);
    }

    static void drawOptionsMenu(int width, float panelX, float panelY,
                                float panelWidth, float panelHeight) {
        drawCenteredPixelText("OPTIONS", width * 0.5f, panelY + 23.0f,
                1.8f, 0.96f, 0.87f, 0.55f, 1.0f);
        float sensitivityMultiplier = mouseSensitivity / MOUSE_SENSITIVITY;
        drawMenuButton(width, "MOUSE SENSITIVITY  "
                + String.format("%.2fX", sensitivityMultiplier), "option_mouse",
                panelY + 65.0f);
        drawMenuButton(width, "WORLD DETAIL  " + pixelScale + "X", "option_pixel",
                panelY + 97.0f);
        drawMenuButton(width, "BACK", "back", panelY + 139.0f);
        drawCenteredPixelText("LEFT/RIGHT: LOWER = SHARPER", width * 0.5f,
                panelY + panelHeight - 17.0f, 0.62f, 0.62f, 0.70f, 0.76f, 1.0f);
    }

    static void drawPauseMenu(int width, float panelX, float panelY,
                              float panelWidth, float panelHeight) {
        drawCenteredPixelText("GAME MENU", width * 0.5f, panelY + 20.0f,
                1.8f, 0.96f, 0.87f, 0.55f, 1.0f);
        float buttonY = panelY + 56.0f;
        drawMenuButton(width, "BACK TO GAME", "resume", buttonY);
        drawMenuButton(width, "OPTIONS", "options", buttonY + 30.0f);
        drawMenuButton(width, "SAVE AND QUIT TO TITLE", "title", buttonY + 60.0f);
        drawMenuButton(width, "SAVE AND QUIT GAME", "quit_game", buttonY + 90.0f);
    }

    static void drawInventoryUi(int width, int height, boolean tableMode) {
        if (catalogMode) {
            drawItemCatalogUi(width, height);
            return;
        }
        inventorySlotHits.clear();
        craftingRecipeHits.clear();
        catalogItemHits.clear();
        catalogCategoryHits.clear();
        menuViewportWidth = width;
        menuViewportHeight = height;

        float panelWidth = Math.min(width - 10.0f, 390.0f);
        float panelHeight = Math.min(height - 10.0f, 220.0f);
        float panelX = (width - panelWidth) * 0.5f;
        float panelY = (height - panelHeight) * 0.5f;
        glColor4f(0.055f, 0.065f, 0.075f, 0.97f);
        drawHudRect(panelX, panelY, panelWidth, panelHeight);
        glColor4f(0.72f, 0.77f, 0.78f, 1.0f);
        drawHudOutline(panelX, panelY, panelWidth, panelHeight, 2.0f);
        drawPixelText("INVENTORY", panelX + 12.0f, panelY + 10.0f,
                1.15f, 0.96f, 0.88f, 0.58f, 1.0f);
        drawPixelText(tableMode ? "CRAFTING TABLE" : "2 X 2 CRAFTING",
                panelX + 220.0f, panelY + 10.0f, 0.82f,
                0.84f, 0.88f, 0.90f, 1.0f);

        float slotSize = 19.0f;
        float gap = 3.0f;
        float gridX = panelX + 12.0f;
        float gridY = panelY + 44.0f;
        drawPixelText("ITEM SLOTS", gridX, gridY - 11.0f,
                0.62f, 0.70f, 0.77f, 0.80f, 1.0f);
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = row == 3 ? column : 9 + row * 9 + column;
                float x = gridX + column * (slotSize + gap);
                float y = gridY + row * (slotSize + gap);
                drawInventorySlot(slot, x, y, slotSize);
            }
        }
        float dividerX = panelX + 214.0f;
        glColor4f(0.24f, 0.29f, 0.31f, 1.0f);
        drawHudRect(dividerX, panelY + 35.0f, 1.0f, panelHeight - 48.0f);

        float recipePanelX = panelX + 224.0f;
        float recipePanelWidth = panelWidth - 234.0f;
        float listY = panelY + 97.0f;
        List<Recipe> recipes = visibleRecipes();
        craftingRecipeScroll = clamp(craftingRecipeScroll, 0, Math.max(0, recipes.size() - 8));
        float[] recipeCursor = menuCursorPosition(window);
        craftingRecipeSelection = 0;
        if (recipeCursor[0] >= recipePanelX && recipeCursor[0] <= recipePanelX + recipePanelWidth
                && recipeCursor[1] >= listY && recipeCursor[1] < listY + 88.0f) {
            craftingRecipeSelection = clamp((int)((recipeCursor[1] - listY) / 11.0f),
                    0, Math.max(0, Math.min(7, recipes.size() - craftingRecipeScroll - 1)));
        }
        Recipe selectedRecipe = recipes.isEmpty() ? null
                : recipes.get(Math.min(recipes.size() - 1,
                craftingRecipeScroll + craftingRecipeSelection));
        float recipeGridX = recipePanelX + 3.0f;
        float recipeGridY = panelY + 42.0f;
        float recipeSlot = tableMode ? 12.0f : 16.0f;
        float recipeGap = 2.0f;
        int gridSide = tableMode ? 3 : 2;
        for (int i = 0; i < gridSide * gridSide; i++) {
            float x = recipeGridX + (i % gridSide) * (recipeSlot + recipeGap);
            float y = recipeGridY + (i / gridSide) * (recipeSlot + recipeGap);
            drawCraftingCell(x, y, recipeSlot, null, 0);
            if (selectedRecipe != null && i < selectedRecipe.ingredients().size()) {
                Ingredient ingredient = selectedRecipe.ingredients().get(i);
                drawCraftingCell(x, y, recipeSlot, ingredient.item(), ingredient.count());
            }
        }
        float outputX = recipePanelX + 71.0f;
        float outputY = recipeGridY + (tableMode ? 9.0f : 8.0f);
        drawPixelText(">", recipePanelX + 54.0f, outputY + 4.0f,
                1.1f, 0.85f, 0.85f, 0.80f, 1.0f);
        drawCraftingCell(outputX, outputY, 21.0f,
                selectedRecipe == null ? null : selectedRecipe.result(),
                selectedRecipe == null ? 0 : selectedRecipe.resultCount());

        drawPixelText(tableMode ? "RECIPES - CLICK TO CRAFT" : "QUICK RECIPES",
                recipePanelX, listY - 11.0f, 0.60f,
                0.70f, 0.77f, 0.80f, 1.0f);
        int visibleRows = Math.min(8, Math.max(0, recipes.size() - craftingRecipeScroll));
        for (int row = 0; row < visibleRows; row++) {
            int recipeIndex = craftingRecipeScroll + row;
            Recipe recipe = recipes.get(recipeIndex);
            float y = listY + row * 11.0f;
            MenuButton rowHit = new MenuButton(recipe.result().label(), "craft", recipePanelX,
                    y, recipePanelWidth, 10.0f);
            craftingRecipeHits.add(new CraftingRecipeHit(rowHit, recipe));
            boolean ready = canCraft(recipe);
            boolean hovered = menuButtonContains(rowHit, menuCursorPosition(window));
            glColor4f(hovered ? 0.28f : 0.12f, hovered ? 0.36f : 0.15f,
                    hovered ? 0.31f : 0.17f, 0.95f);
            drawHudRect(rowHit.x(), rowHit.y(), rowHit.width(), rowHit.height());
            String label = recipe.result().label();
            float labelScale = 0.57f;
            while (pixelTextWidth(label, labelScale) > recipePanelWidth - 34.0f
                    && label.length() > 3) label = label.substring(0, label.length() - 1);
            drawPixelText(label, rowHit.x() + 3.0f, y + 2.0f, labelScale,
                    ready ? 0.91f : 0.55f, ready ? 0.94f : 0.59f,
                    ready ? 0.72f : 0.62f, 1.0f);
            drawPixelText(ready ? "OK" : "--", rowHit.x() + recipePanelWidth - 16.0f,
                    y + 2.0f, 0.55f, ready ? 0.64f : 0.48f,
                    ready ? 0.92f : 0.53f, ready ? 0.66f : 0.53f, 1.0f);
        }

        float[] cursor = menuCursorPosition(window);
        String description = inventory.carriedItem == null ? "CLICK SLOT TO MOVE ITEMS"
                : "CARRY " + inventory.carriedItem.label() + " X" + inventory.carriedCount;
        for (InventorySlotHit hit : inventorySlotHits) {
            if (cursor[0] >= hit.x() && cursor[0] <= hit.x() + hit.size()
                    && cursor[1] >= hit.y() && cursor[1] <= hit.y() + hit.size()) {
                ItemType item = inventory.items[hit.index()];
                if (item != null) description = item.label() + " X" + inventory.counts[hit.index()];
            }
        }
        drawPixelText(description, panelX + 12.0f, panelY + panelHeight - 16.0f,
                0.62f, 0.83f, 0.87f, 0.88f, 1.0f);
        drawPixelText("E OR ESC TO CLOSE", panelX + panelWidth - 105.0f,
                panelY + panelHeight - 16.0f, 0.58f, 0.68f, 0.73f, 0.75f, 1.0f);
    }

    static void drawItemCatalogUi(int width, int height) {
        inventorySlotHits.clear();
        craftingRecipeHits.clear();
        catalogItemHits.clear();
        catalogCategoryHits.clear();
        menuViewportWidth = width;
        menuViewportHeight = height;
        float panelWidth = Math.min(width - 10.0f, 390.0f);
        float panelHeight = Math.min(height - 10.0f, 220.0f);
        float panelX = (width - panelWidth) * 0.5f;
        float panelY = (height - panelHeight) * 0.5f;
        glColor4f(0.055f, 0.065f, 0.075f, 0.97f);
        drawHudRect(panelX, panelY, panelWidth, panelHeight);
        glColor4f(0.72f, 0.77f, 0.78f, 1.0f);
        drawHudOutline(panelX, panelY, panelWidth, panelHeight, 2.0f);
        drawPixelText("ITEM CATALOG", panelX + 12.0f, panelY + 9.0f,
                1.1f, 0.96f, 0.88f, 0.58f, 1.0f);

        float tabX = panelX + 12.0f;
        float tabY = panelY + 25.0f;
        float tabWidth = Math.min(70.0f, (panelWidth - 24.0f) / ItemCategory.values().length);
        for (int i = 0; i < ItemCategory.values().length; i++) {
            ItemCategory category = ItemCategory.values()[i];
            String label = category.name();
            float x = tabX + i * (tabWidth + 2.0f);
            MenuButton tab = new MenuButton(label, "", x, tabY, tabWidth, 13.0f);
            catalogCategoryHits.add(tab);
            glColor4f(category == catalogCategory ? 0.30f : 0.12f,
                    category == catalogCategory ? 0.38f : 0.16f,
                    category == catalogCategory ? 0.31f : 0.18f, 0.98f);
            drawHudRect(x, tabY, tabWidth, tab.height());
            float labelScale = 0.53f;
            drawPixelText(label, x + (tabWidth - pixelTextWidth(label, labelScale)) * 0.5f,
                    tabY + 4.0f, labelScale, 0.91f, 0.91f, 0.84f, 1.0f);
        }

        List<ItemType> catalog = java.util.Arrays.stream(ItemType.values())
                .filter(item -> item.category() == catalogCategory).toList();
        int maxScroll = Math.max(0, catalog.size() - 36);
        catalogItemScroll = clamp(catalogItemScroll, 0, maxScroll);
        catalogItemScroll = (catalogItemScroll / 9) * 9;
        int pages = Math.max(1, (catalog.size() + 35) / 36);
        drawPixelText("PAGE " + (catalogItemScroll / 36 + 1) + " / " + pages,
                panelX + panelWidth - 75.0f, panelY + 10.0f,
                0.55f, 0.72f, 0.76f, 0.77f, 1.0f);

        float slotSize = 19.0f;
        float gap = 3.0f;
        float gridX = panelX + 12.0f;
        float gridY = panelY + 45.0f;
        float[] cursor = menuCursorPosition(window);
        ItemType hovered = null;
        for (int i = catalogItemScroll; i < Math.min(catalog.size(), catalogItemScroll + 36); i++) {
            int visibleIndex = i - catalogItemScroll;
            int column = visibleIndex % 9;
            int row = visibleIndex / 9;
            float x = gridX + column * (slotSize + gap);
            float y = gridY + row * (slotSize + gap);
            MenuButton area = new MenuButton(catalog.get(i).label(), "catalog_item",
                    x, y, slotSize, slotSize);
            catalogItemHits.add(new CatalogItemHit(area, catalog.get(i)));
            glColor4f(0.015f, 0.02f, 0.025f, 0.96f);
            drawHudRect(x, y, slotSize, slotSize);
            glColor4f(0.35f, 0.40f, 0.42f, 1.0f);
            drawHudOutline(x, y, slotSize, slotSize, 1.0f);
            float[] color = itemColor(catalog.get(i));
            glColor4f(color[0], color[1], color[2], color[3]);
            drawHudRect(x + 4.0f, y + 4.0f, slotSize - 8.0f, slotSize - 8.0f);
            if (cursor[0] >= x && cursor[0] <= x + slotSize
                    && cursor[1] >= y && cursor[1] <= y + slotSize) hovered = catalog.get(i);
        }
        String description = hovered == null
                ? "CLICK ITEM FOR 64   WHEEL OR ARROWS TO SCROLL"
                : hovered.label() + "  " + hovered.category().name();
        drawPixelText(description, panelX + 12.0f, panelY + panelHeight - 16.0f,
                0.62f, 0.83f, 0.87f, 0.88f, 1.0f);
        drawPixelText("TAB BACK  |  E OR ESC CLOSE", panelX + panelWidth - 145.0f,
                panelY + panelHeight - 16.0f, 0.58f, 0.68f, 0.73f, 0.75f, 1.0f);
    }

    static void drawInventorySlot(int slot, float x, float y, float size) {
        glColor4f(0.015f, 0.02f, 0.025f, 0.96f);
        drawHudRect(x, y, size, size);
        glColor4f(slot < Inventory.HOTBAR_SIZE ? 0.40f : 0.29f,
                slot < Inventory.HOTBAR_SIZE ? 0.44f : 0.32f,
                slot < Inventory.HOTBAR_SIZE ? 0.45f : 0.35f, 1.0f);
        drawHudOutline(x, y, size, size, 1.0f);
        inventorySlotHits.add(new InventorySlotHit(x, y, size, slot));
        ItemType item = inventory.items[slot];
        if (item == null) return;
        float[] color = itemColor(item);
        glColor4f(color[0], color[1], color[2], color[3]);
        drawHudRect(x + 4.0f, y + 3.0f, size - 8.0f, size - 8.0f);
        if (inventory.counts[slot] > 1) {
            String count = Integer.toString(inventory.counts[slot]);
            drawPixelText(count, x + size - count.length() * 3.2f - 1.0f,
                    y + size - 6.0f, 0.46f, 1.0f, 1.0f, 0.93f, 1.0f);
        }
    }

    static void drawCraftingCell(float x, float y, float size, ItemType item, int count) {
        glColor4f(0.02f, 0.025f, 0.03f, 0.98f);
        drawHudRect(x, y, size, size);
        glColor4f(0.40f, 0.44f, 0.46f, 1.0f);
        drawHudOutline(x, y, size, size, 1.0f);
        if (item == null) return;
        float[] color = itemColor(item);
        glColor4f(color[0], color[1], color[2], color[3]);
        float inset = Math.max(3.0f, size * 0.22f);
        drawHudRect(x + inset, y + inset, size - inset * 2.0f, size - inset * 2.0f);
        if (count > 1) {
            drawPixelText(Integer.toString(count), x + size - 6.0f,
                    y + size - 6.0f, 0.45f, 1.0f, 1.0f, 0.92f, 1.0f);
        }
    }

    static void drawMenuButton(int width, String label, String action, float y) {
        float buttonWidth = Math.min(238.0f, Math.max(142.0f, width * 0.66f));
        buttonWidth = Math.min(buttonWidth, width - 18.0f);
        float buttonHeight = 23.0f;
        float x = (width - buttonWidth) * 0.5f;
        MenuButton button = new MenuButton(label, action, x, y, buttonWidth, buttonHeight);
        int index = menuButtons.size();
        menuButtons.add(button);
        boolean selected = index == menuSelection;
        boolean hovered = menuButtonContains(button, menuCursorPosition(window));
        if (selected || hovered) {
            glColor4f(0.32f, 0.48f, 0.34f, 0.98f);
        } else {
            glColor4f(0.14f, 0.22f, 0.25f, 0.96f);
        }
        drawHudRect(x, y, buttonWidth, buttonHeight);
        glColor4f(selected || hovered ? 1.0f : 0.54f,
                selected || hovered ? 0.87f : 0.67f,
                selected || hovered ? 0.43f : 0.72f, 1.0f);
        drawHudOutline(x, y, buttonWidth, buttonHeight, 1.0f);
        float textScale = 1.02f;
        float textWidth = pixelTextWidth(label, textScale);
        drawPixelText(label, x + (buttonWidth - textWidth) * 0.5f,
                y + (buttonHeight - 7.0f * textScale) * 0.5f,
                textScale, 0.97f, 0.98f, 0.91f, 1.0f);
    }

    static float pixelTextWidth(String text, float scale) {
        float readableScale = scale * PIXEL_TEXT_SCALE;
        return Math.max(0.0f, text.length() * 6.0f * readableScale - readableScale);
    }

    static void drawCenteredPixelText(String text, float centerX, float y,
                                      float scale, float red, float green, float blue, float alpha) {
        drawPixelText(text, centerX - pixelTextWidth(text, scale) * 0.5f,
                y, scale, red, green, blue, alpha);
    }

    static void drawPixelText(String text, float x, float y, float scale,
                              float red, float green, float blue, float alpha) {
        float glyphScale = scale * PIXEL_TEXT_SCALE;
        drawPixelTextGlyphs(text, x + 0.55f, y + 0.55f, glyphScale,
                0.015f, 0.02f, 0.025f, alpha * 0.88f);
        drawPixelTextGlyphs(text, x, y, glyphScale, red, green, blue, alpha);
    }

    static void drawPixelTextGlyphs(String text, float x, float y, float scale,
                                   float red, float green, float blue, float alpha) {
        glColor4f(red, green, blue, alpha);
        glBegin(GL_QUADS);
        for (int index = 0; index < text.length(); index++) {
            int[] rows = pixelGlyph(text.charAt(index));
            float characterX = x + index * 6.0f * scale;
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < 5; column++) {
                    if ((rows[row] & (1 << (4 - column))) == 0) continue;
                    float left = characterX + column * scale;
                    float top = y + row * scale;
                    glVertex2f(left, top);
                    glVertex2f(left + scale, top);
                    glVertex2f(left + scale, top + scale);
                    glVertex2f(left, top + scale);
                }
            }
        }
        glEnd();
    }

    static int[] pixelGlyph(char character) {
        char c = Character.toUpperCase(character);
        return switch (c) {
            case 'A' -> new int[]{14, 17, 17, 31, 17, 17, 17};
            case 'B' -> new int[]{30, 17, 17, 30, 17, 17, 30};
            case 'C' -> new int[]{14, 17, 16, 16, 16, 17, 14};
            case 'D' -> new int[]{30, 17, 17, 17, 17, 17, 30};
            case 'E' -> new int[]{31, 16, 16, 30, 16, 16, 31};
            case 'F' -> new int[]{31, 16, 16, 30, 16, 16, 16};
            case 'G' -> new int[]{14, 17, 16, 23, 17, 17, 15};
            case 'H' -> new int[]{17, 17, 17, 31, 17, 17, 17};
            case 'I' -> new int[]{14, 4, 4, 4, 4, 4, 14};
            case 'J' -> new int[]{7, 2, 2, 2, 18, 18, 12};
            case 'K' -> new int[]{17, 18, 20, 24, 20, 18, 17};
            case 'L' -> new int[]{16, 16, 16, 16, 16, 16, 31};
            case 'M' -> new int[]{17, 27, 21, 21, 17, 17, 17};
            case 'N' -> new int[]{17, 25, 25, 21, 19, 19, 17};
            case 'O' -> new int[]{14, 17, 17, 17, 17, 17, 14};
            case 'P' -> new int[]{30, 17, 17, 30, 16, 16, 16};
            case 'Q' -> new int[]{14, 17, 17, 17, 21, 18, 13};
            case 'R' -> new int[]{30, 17, 17, 30, 20, 18, 17};
            case 'S' -> new int[]{15, 16, 16, 14, 1, 1, 30};
            case 'T' -> new int[]{31, 4, 4, 4, 4, 4, 4};
            case 'U' -> new int[]{17, 17, 17, 17, 17, 17, 14};
            case 'V' -> new int[]{17, 17, 17, 17, 17, 10, 4};
            case 'W' -> new int[]{17, 17, 17, 21, 21, 21, 10};
            case 'X' -> new int[]{17, 17, 10, 4, 10, 17, 17};
            case 'Y' -> new int[]{17, 17, 10, 4, 4, 4, 4};
            case 'Z' -> new int[]{31, 1, 2, 4, 8, 16, 31};
            case '0' -> new int[]{14, 17, 19, 21, 25, 17, 14};
            case '1' -> new int[]{4, 12, 4, 4, 4, 4, 14};
            case '2' -> new int[]{14, 17, 1, 2, 4, 8, 31};
            case '3' -> new int[]{30, 1, 1, 14, 1, 1, 30};
            case '4' -> new int[]{2, 6, 10, 18, 31, 2, 2};
            case '5' -> new int[]{31, 16, 16, 30, 1, 1, 30};
            case '6' -> new int[]{14, 16, 16, 30, 17, 17, 14};
            case '7' -> new int[]{31, 1, 2, 4, 8, 8, 8};
            case '8' -> new int[]{14, 17, 17, 14, 17, 17, 14};
            case '9' -> new int[]{14, 17, 17, 15, 1, 1, 14};
            case ':' -> new int[]{0, 4, 4, 0, 4, 4, 0};
            case '.' -> new int[]{0, 0, 0, 0, 0, 4, 4};
            case '-' -> new int[]{0, 0, 0, 31, 0, 0, 0};
            case '_' -> new int[]{0, 0, 0, 0, 0, 0, 31};
            case '/' -> new int[]{1, 2, 2, 4, 8, 8, 16};
            case '>' -> new int[]{16, 8, 4, 2, 4, 8, 16};
            case '<' -> new int[]{1, 2, 4, 8, 4, 2, 1};
            case ' ' -> new int[]{0, 0, 0, 0, 0, 0, 0};
            default -> new int[]{14, 17, 1, 2, 4, 0, 4};
        };
    }

    static float[] menuCursorPosition(long handle) {
        double[] cursorX = new double[1];
        double[] cursorY = new double[1];
        int[] windowWidth = new int[1];
        int[] windowHeight = new int[1];
        glfwGetCursorPos(handle, cursorX, cursorY);
        glfwGetWindowSize(handle, windowWidth, windowHeight);
        return new float[]{
                (float)(cursorX[0] * menuViewportWidth / Math.max(1, windowWidth[0])),
                (float)(cursorY[0] * menuViewportHeight / Math.max(1, windowHeight[0]))
        };
    }

    static boolean menuButtonContains(MenuButton button, float[] point) {
        return point[0] >= button.x() && point[0] <= button.x() + button.width()
                && point[1] >= button.y() && point[1] <= button.y() + button.height();
    }

    static void handleMenuClick(long handle) {
        float[] point = menuCursorPosition(handle);
        if (screen == Screen.WORLD_SELECT) {
            for (int index = 0; index < saveRows.size(); index++) {
                if (menuButtonContains(saveRows.get(index), point)) {
                    int clickedIndex = saveListOffset + index;
                    double now = glfwGetTime();
                    boolean doubleClicked = clickedIndex == lastWorldRowClickIndex
                            && now - lastWorldRowClickTime <= WORLD_DOUBLE_CLICK_SECONDS;
                    selectedSaveIndex = clickedIndex;
                    lastWorldRowClickIndex = clickedIndex;
                    lastWorldRowClickTime = now;
                    if (doubleClicked) playSelectedWorld();
                    return;
                }
            }
        }
        for (int index = 0; index < menuButtons.size(); index++) {
            MenuButton button = menuButtons.get(index);
            if (menuButtonContains(button, point)) {
                menuSelection = index;
                activateMenuAction(button.action());
                return;
            }
        }
    }

    static void handleEscapeKey() {
        switch (screen) {
            case GAME -> {
                screen = Screen.PAUSED;
                menuSelection = 0;
                setMouseCaptured(false);
            }
            case PAUSED -> {
                screen = Screen.GAME;
                suppressMouseActionsUntilReleased = true;
                setMouseCaptured(true);
            }
            case INVENTORY, CRAFTING -> closeInventory();
            case OPTIONS -> {
                screen = optionsReturnScreen;
                setMouseCaptured(false);
            }
            case WORLD_SELECT -> screen = Screen.TITLE;
            case CREATE_WORLD -> screen = Screen.WORLD_SELECT;
            case TITLE -> glfwSetWindowShouldClose(window, true);
        }
    }

    static void handleMenuKey(int key) {
        if (screen == Screen.CREATE_WORLD) {
            if (key == GLFW_KEY_BACKSPACE && !newWorldName.isEmpty()) {
                newWorldName = newWorldName.substring(0, newWorldName.length() - 1);
            } else if (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER) {
                createWorld();
            }
            return;
        }
        if (screen == Screen.WORLD_SELECT && !saveEntries.isEmpty()) {
            if (key == GLFW_KEY_UP) {
                selectedSaveIndex = floorMod(selectedSaveIndex - 1, saveEntries.size());
                return;
            }
            if (key == GLFW_KEY_DOWN) {
                selectedSaveIndex = floorMod(selectedSaveIndex + 1, saveEntries.size());
                return;
            }
        }
        if (screen == Screen.OPTIONS && (key == GLFW_KEY_LEFT || key == GLFW_KEY_RIGHT)) {
            int direction = key == GLFW_KEY_RIGHT ? 1 : -1;
            if (menuSelection == 0) adjustMouseSensitivity(direction);
            else if (menuSelection == 1) adjustPixelScale(direction);
            return;
        }
        if (key == GLFW_KEY_UP || key == GLFW_KEY_LEFT) {
            if (!menuButtons.isEmpty()) menuSelection = floorMod(menuSelection - 1, menuButtons.size());
        } else if (key == GLFW_KEY_DOWN || key == GLFW_KEY_RIGHT) {
            if (!menuButtons.isEmpty()) menuSelection = floorMod(menuSelection + 1, menuButtons.size());
        } else if (key == GLFW_KEY_TAB) {
            if (!menuButtons.isEmpty()) menuSelection = floorMod(menuSelection + 1, menuButtons.size());
        } else if ((key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)
                && !menuButtons.isEmpty()) {
            activateMenuAction(menuButtons.get(menuSelection).action());
        }
    }

    static void activateMenuAction(String action) {
        switch (action) {
            case "worlds" -> openWorldSelect();
            case "options" -> openOptions();
            case "quit" -> glfwSetWindowShouldClose(window, true);
            case "create" -> openCreateWorld();
            case "create_world" -> createWorld();
            case "play" -> playSelectedWorld();
            case "back" -> {
                if (screen == Screen.OPTIONS) {
                    screen = optionsReturnScreen;
                    setMouseCaptured(false);
                } else if (screen == Screen.CREATE_WORLD) {
                    screen = Screen.WORLD_SELECT;
                } else {
                    screen = Screen.TITLE;
                }
            }
            case "resume" -> {
                screen = Screen.GAME;
                suppressMouseActionsUntilReleased = true;
                setMouseCaptured(true);
            }
            case "title" -> saveAndReturnToTitle();
            case "quit_game" -> saveAndQuit();
            case "option_mouse" -> adjustMouseSensitivity(1);
            case "option_pixel" -> adjustPixelScale(1);
            default -> { }
        }
    }

    static void adjustMouseSensitivity(int direction) {
        mouseSensitivity = clamp(mouseSensitivity + direction * 0.00025f,
                0.00025f, 0.0030f);
        saveOptions();
    }

    static void adjustPixelScale(int direction) {
        pixelScale = clamp(pixelScale + direction, 1, 5);
        saveOptions();
    }

    static void loadOptions() {
        if (!Files.isRegularFile(OPTIONS_PATH)) return;
        Properties options = new Properties();
        boolean migrateOptions = false;
        try (var input = Files.newInputStream(OPTIONS_PATH)) {
            options.load(input);
            mouseSensitivity = clamp(Float.parseFloat(
                    options.getProperty("mouseSensitivity", Float.toString(MOUSE_SENSITIVITY))),
                    0.00025f, 0.0030f);
            int savedPixelScale = Integer.parseInt(options.getProperty("pixelScale",
                    Integer.toString(PIXEL_SCALE)));
            int optionsVersion = Integer.parseInt(options.getProperty("optionsVersion", "1"));
            pixelScale = clamp(savedPixelScale, 1, 5);
            if (optionsVersion < OPTIONS_VERSION) {
                // Version 1 used 3X as the default; move that default to the sharper 2X mode.
                if (savedPixelScale == 3) pixelScale = PIXEL_SCALE;
                migrateOptions = true;
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not load options: " + e.getMessage());
            mouseSensitivity = MOUSE_SENSITIVITY;
            pixelScale = PIXEL_SCALE;
        }
        if (migrateOptions) saveOptions();
    }

    static void initializeSaveStorage() {
        try {
            Files.createDirectories(SAVES_DIRECTORY);
        } catch (IOException preferredPathError) {
            SAVE_DIRECTORY = Path.of(System.getProperty("user.dir"), ".mineworld-game-project");
            SAVE_PATH = SAVE_DIRECTORY.resolve("world.dat");
            SAVES_DIRECTORY = SAVE_DIRECTORY.resolve("saves");
            OPTIONS_PATH = SAVE_DIRECTORY.resolve("options.properties");
            try {
                Files.createDirectories(SAVES_DIRECTORY);
                System.err.println("Default save folder is not writable; using " + SAVE_DIRECTORY);
            } catch (IOException fallbackError) {
                fallbackError.addSuppressed(preferredPathError);
                throw new IllegalStateException("Could not create a world save folder", fallbackError);
            }
        }
    }

    static void saveOptions() {
        Properties options = new Properties();
        options.setProperty("optionsVersion", Integer.toString(OPTIONS_VERSION));
        options.setProperty("mouseSensitivity", Float.toString(mouseSensitivity));
        options.setProperty("pixelScale", Integer.toString(pixelScale));
        try {
            Files.createDirectories(SAVE_DIRECTORY);
            try (var output = Files.newOutputStream(OPTIONS_PATH)) {
                options.store(output, "Mineworld options");
            }
        } catch (IOException e) {
            reportPersistenceError("Could not save options", e);
        }
    }

    static void raycastBreak(float fx, float fy, float fz) {
        BlockHit hit = raycastFrom(player.x, player.y + PLAYER_EYE, player.z, fx, fy, fz);
        if (hit != null) {
            BlockType block = getBlock(hit.x(), hit.y(), hit.z());
            if (breakBlock(hit)) setStatus(block + " removed");
        }
    }

    static void raycastPlace(float fx, float fy, float fz) {
        BlockHit hit = raycastFrom(player.x, player.y + PLAYER_EYE, player.z, fx, fy, fz);
        if (hit == null) {
            setStatus("No block in reach");
            return;
        }
        ItemType selected = inventory.selectedItem();
        if (placeBlock(hit, selected)) {
            setStatus(selected.label() + " placed");
        } else {
            setStatus(selected == null || selected.block() == null
                    ? "Select a placeable block" : "Cannot place " + selected.label());
        }
    }

    static boolean raycastUse(float fx, float fy, float fz) {
        BlockHit hit = raycastFrom(player.x, player.y + PLAYER_EYE, player.z, fx, fy, fz);
        if (hit == null) return false;
        BlockType target = getBlock(hit.x(), hit.y(), hit.z());
        if (target == BlockType.CRAFTING_TABLE) {
            openCraftingTable();
            return true;
        }
        if (target == BlockType.DOOR) {
            int state = getBlockState(hit.x(), hit.y(), hit.z());
            int lowerY = (state & 8) != 0 ? hit.y() - 1 : hit.y();
            int lowerState = getBlockState(hit.x(), lowerY, hit.z());
            boolean open = (lowerState & 4) == 0;
            setWorldBlockState(hit.x(), lowerY, hit.z(), (lowerState & 3) | (open ? 4 : 0));
            setWorldBlockState(hit.x(), lowerY + 1, hit.z(),
                    (lowerState & 3) | (open ? 12 : 8));
            setStatus(open ? "Door opened" : "Door closed");
            return true;
        }
        if (target == BlockType.BED) {
            int state = getBlockState(hit.x(), hit.y(), hit.z());
            int facing = state & 3;
            int footX = hit.x() - ((state & 4) != 0 ? facingDx(facing) : 0);
            int footZ = hit.z() - ((state & 4) != 0 ? facingDz(facing) : 0);
            spawnX = footX + 0.5f;
            spawnY = hit.y() + 0.58f;
            spawnZ = footZ + 0.5f;
            setStatus("Spawn point set at bed");
            return true;
        }
        return false;
    }

    static BlockHit raycastFrom(float originX, float originY, float originZ,
                                float directionX, float directionY, float directionZ) {
        float directionLength = (float)Math.sqrt(directionX * directionX
                + directionY * directionY + directionZ * directionZ);
        if (directionLength == 0.0f) return null;
        directionX /= directionLength;
        directionY /= directionLength;
        directionZ /= directionLength;

        int x = (int)Math.floor(originX);
        int y = (int)Math.floor(originY);
        int z = (int)Math.floor(originZ);
        if (getBlock(x, y, z) != BlockType.AIR) {
            return new BlockHit(x, y, z, x, y, z);
        }

        int stepX = directionX > 0.0f ? 1 : directionX < 0.0f ? -1 : 0;
        int stepY = directionY > 0.0f ? 1 : directionY < 0.0f ? -1 : 0;
        int stepZ = directionZ > 0.0f ? 1 : directionZ < 0.0f ? -1 : 0;
        float deltaX = stepX == 0 ? Float.POSITIVE_INFINITY : Math.abs(1.0f / directionX);
        float deltaY = stepY == 0 ? Float.POSITIVE_INFINITY : Math.abs(1.0f / directionY);
        float deltaZ = stepZ == 0 ? Float.POSITIVE_INFINITY : Math.abs(1.0f / directionZ);
        float maxX = stepX == 0 ? Float.POSITIVE_INFINITY
                : (stepX > 0 ? x + 1.0f - originX : originX - x) * deltaX;
        float maxY = stepY == 0 ? Float.POSITIVE_INFINITY
                : (stepY > 0 ? y + 1.0f - originY : originY - y) * deltaY;
        float maxZ = stepZ == 0 ? Float.POSITIVE_INFINITY
                : (stepZ > 0 ? z + 1.0f - originZ : originZ - z) * deltaZ;

        int previousX = x;
        int previousY = y;
        int previousZ = z;
        while (true) {
            float distance;
            if (maxX <= maxY && maxX <= maxZ) {
                distance = maxX;
                previousX = x;
                previousY = y;
                previousZ = z;
                x += stepX;
                maxX += deltaX;
            } else if (maxY <= maxZ) {
                distance = maxY;
                previousX = x;
                previousY = y;
                previousZ = z;
                y += stepY;
                maxY += deltaY;
            } else {
                distance = maxZ;
                previousX = x;
                previousY = y;
                previousZ = z;
                z += stepZ;
                maxZ += deltaZ;
            }
            if (distance > REACH_DISTANCE) return null;
            if (getBlock(x, y, z) != BlockType.AIR) {
                return new BlockHit(x, y, z, previousX, previousY, previousZ);
            }
        }
    }

    static boolean breakBlock(BlockHit hit) {
        BlockType block = getBlock(hit.x(), hit.y(), hit.z());
        if (block == BlockType.AIR) return false;
        int state = getBlockState(hit.x(), hit.y(), hit.z());
        if (block == BlockType.DOOR) {
            int lowerY = (state & 8) != 0 ? hit.y() - 1 : hit.y();
            setWorldBlock(hit.x(), lowerY, hit.z(), BlockType.AIR);
            setWorldBlock(hit.x(), lowerY + 1, hit.z(), BlockType.AIR);
        } else if (block == BlockType.BED) {
            int facing = state & 3;
            int footX = hit.x() - ((state & 4) != 0 ? facingDx(facing) : 0);
            int footZ = hit.z() - ((state & 4) != 0 ? facingDz(facing) : 0);
            setWorldBlock(footX, hit.y(), footZ, BlockType.AIR);
            setWorldBlock(footX + facingDx(facing), hit.y(),
                    footZ + facingDz(facing), BlockType.AIR);
        } else {
            setWorldBlock(hit.x(), hit.y(), hit.z(), BlockType.AIR);
        }
        if (inventory != null && block != BlockType.WATER) {
            ItemType drop = itemForBlock(block);
            if (drop != null && !inventory.addItem(drop, 1)) {
                setStatus("Inventory full; block removed without a drop");
            }
        }
        return true;
    }

    static boolean placeBlock(BlockHit hit, ItemType item) {
        if (item == null || item.block() == null) return false;
        BlockType block = item.block();
        if (block == BlockType.AIR) return false;
        if (getBlock(hit.x(), hit.y(), hit.z()) == BlockType.AIR) return false;
        int placeX = hit.previousX();
        int placeY = hit.previousY();
        int placeZ = hit.previousZ();
        if (getBlock(placeX, placeY, placeZ) != BlockType.AIR) return false;
        if (inventory != null && (inventory.selectedItem() != item
                || inventory.counts[inventory.selected] <= 0)) return false;
        if (intersectsPlayerBlock(placeX, placeY, placeZ)) return false;

        if (block == BlockType.TORCH) {
            int state = torchMountState(hit, placeX, placeY, placeZ);
            if (state < 0 || !isSolidAt(hit.x(), hit.y(), hit.z())) return false;
            setWorldBlock(placeX, placeY, placeZ, block);
            setWorldBlockState(placeX, placeY, placeZ, state);
        } else if (block == BlockType.DOOR) {
            if (placeY + 1 >= WORLD_HEIGHT || getBlock(placeX, placeY + 1, placeZ) != BlockType.AIR
                    || !isSolidAt(placeX, placeY - 1, placeZ)
                    || intersectsPlayerBlock(placeX, placeY + 1, placeZ)) return false;
            int facing = horizontalFacingFromYaw();
            setWorldBlock(placeX, placeY, placeZ, block);
            setWorldBlockState(placeX, placeY, placeZ, facing);
            setWorldBlock(placeX, placeY + 1, placeZ, block);
            setWorldBlockState(placeX, placeY + 1, placeZ, facing | 8);
        } else if (block == BlockType.BED) {
            int facing = horizontalFacingFromYaw();
            int headX = placeX + facingDx(facing);
            int headZ = placeZ + facingDz(facing);
            if (getBlock(headX, placeY, headZ) != BlockType.AIR
                    || !isSolidAt(placeX, placeY - 1, placeZ)
                    || !isSolidAt(headX, placeY - 1, headZ)
                    || intersectsPlayerBlock(headX, placeY, headZ)) return false;
            setWorldBlock(placeX, placeY, placeZ, block);
            setWorldBlockState(placeX, placeY, placeZ, facing);
            setWorldBlock(headX, placeY, headZ, block);
            setWorldBlockState(headX, placeY, headZ, facing | 4);
            spawnX = placeX + 0.5f;
            spawnY = placeY + 0.58f;
            spawnZ = placeZ + 0.5f;
        } else {
            setWorldBlock(placeX, placeY, placeZ, block);
        }
        if (inventory != null) inventory.consumeSelected();
        return true;
    }

    static int torchMountState(BlockHit hit, int placeX, int placeY, int placeZ) {
        int supportDx = hit.x() - placeX;
        int supportDy = hit.y() - placeY;
        int supportDz = hit.z() - placeZ;
        if (supportDy == -1) return 0;
        if (supportDx == 1) return 1;
        if (supportDx == -1) return 2;
        if (supportDz == 1) return 3;
        if (supportDz == -1) return 4;
        return -1;
    }

    static int horizontalFacingFromYaw() {
        return floorMod((int)Math.floor(player.yaw / (Math.PI * 0.5) + 0.5), 4);
    }

    static int facingDx(int facing) {
        return switch (facing & 3) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    static int facingDz(int facing) {
        return switch (facing & 3) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }

    static boolean intersectsPlayerBlock(int x, int y, int z) {
        float playerMinX = player.x - PLAYER_WIDTH * 0.5f;
        float playerMaxX = player.x + PLAYER_WIDTH * 0.5f;
        float playerMinY = player.y;
        float playerMaxY = player.y + PLAYER_HEIGHT;
        float playerMinZ = player.z - PLAYER_WIDTH * 0.5f;
        float playerMaxZ = player.z + PLAYER_WIDTH * 0.5f;
        return x + 1.0f > playerMinX && x < playerMaxX
                && y + 1.0f > playerMinY && y < playerMaxY
                && z + 1.0f > playerMinZ && z < playerMaxZ;
    }

    static boolean isFaceVisible(int x, int y, int z) {
        BlockType neighbor = getBlock(x, y, z);
        return neighbor == BlockType.AIR || neighbor == BlockType.WATER
                || neighbor == BlockType.TORCH || neighbor == BlockType.DOOR
                || neighbor == BlockType.BED;
    }

    static void setWorldBlock(int x, int y, int z, BlockType block) {
        if (y < 0 || y >= WORLD_HEIGHT) return;
        int cx = floorDiv(x, CHUNK_SIZE);
        int cz = floorDiv(z, CHUNK_SIZE);
        Chunk chunk = getChunk(cx, cz, true);
        if (!chunk.generated) generateChunk(chunk);
        int localX = floorMod(x, CHUNK_SIZE);
        int localZ = floorMod(z, CHUNK_SIZE);
        int index = chunk.idx(localX, y, localZ);
        BlockType previous = getBlock(chunk, localX, y, localZ);
        setBlock(chunk, localX, y, localZ, block);
        chunk.blockStates[index] = 0;
        invalidateChunkAndNeighbors(chunk, localX, localZ);
        if (previous == BlockType.TORCH || block == BlockType.TORCH) {
            invalidateTorchLightingAround(chunk);
        }
    }

    static int getBlockState(int wx, int wy, int wz) {
        if (wy < 0 || wy >= WORLD_HEIGHT) return 0;
        Chunk chunk = getChunk(floorDiv(wx, CHUNK_SIZE), floorDiv(wz, CHUNK_SIZE), false);
        if (chunk == null) return 0;
        return chunk.blockStates[chunk.idx(floorMod(wx, CHUNK_SIZE), wy,
                floorMod(wz, CHUNK_SIZE))] & 0xff;
    }

    static void setWorldBlockState(int wx, int wy, int wz, int state) {
        if (wy < 0 || wy >= WORLD_HEIGHT) return;
        Chunk chunk = getChunk(floorDiv(wx, CHUNK_SIZE), floorDiv(wz, CHUNK_SIZE), true);
        if (!chunk.generated) generateChunk(chunk);
        int localX = floorMod(wx, CHUNK_SIZE);
        int localZ = floorMod(wz, CHUNK_SIZE);
        chunk.blockStates[chunk.idx(localX, wy, localZ)] = (byte)(state & 0xff);
        invalidateChunkAndNeighbors(chunk, localX, localZ);
    }

    static void saveWorld() throws IOException {
        saveWorld(activeSavePath == null ? SAVE_PATH : activeSavePath);
    }

    static void saveWorld(Path destination) throws IOException {
        Path saveFile = destination.toAbsolutePath();
        Path parent = saveFile.getParent();
        Files.createDirectories(parent);
        Path temporaryFile = Files.createTempFile(parent, "world-", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporaryFile))) {
                output.writeInt(SAVE_MAGIC);
                output.writeInt(SAVE_VERSION);
                ArrayList<Chunk> chunks = new ArrayList<>();
                for (Chunk chunk : world.chunks.values()) {
                    if (chunk.generated) chunks.add(chunk);
                }
                chunks.sort(Comparator.comparingInt((Chunk chunk) -> chunk.cx)
                        .thenComparingInt(chunk -> chunk.cz));
                output.writeInt(chunks.size());
                for (Chunk chunk : chunks) {
                    output.writeInt(chunk.cx);
                    output.writeInt(chunk.cz);
                    output.writeInt(chunk.blocks.length);
                    output.write(chunk.blocks);
                output.writeInt(chunk.blockStates.length);
                output.write(chunk.blockStates);
                }
                output.writeFloat(player.x);
                output.writeFloat(player.y);
                output.writeFloat(player.z);
                output.writeFloat(player.yaw);
                output.writeFloat(player.pitch);
                output.writeFloat(spawnX);
                output.writeFloat(spawnY);
                output.writeFloat(spawnZ);
                output.writeInt(inventory.selected);
                output.writeInt(inventory.items.length);
                for (int i = 0; i < inventory.items.length; i++) {
                    output.writeInt(inventory.items[i] == null ? -1 : inventory.items[i].ordinal());
                    output.writeInt(inventory.counts[i]);
                }
                output.writeInt(inventory.carriedItem == null ? -1 : inventory.carriedItem.ordinal());
                output.writeInt(inventory.carriedCount);
            }
            try {
                Files.move(temporaryFile, saveFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, saveFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    static void loadWorld() throws IOException {
        loadWorld(activeSavePath == null ? SAVE_PATH : activeSavePath);
    }

    static void loadWorld(Path source) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(source))) {
            if (input.readInt() != SAVE_MAGIC) throw new IOException("Unrecognized save file");
            int version = input.readInt();
            if (version < 1 || version > SAVE_VERSION) {
                throw new IOException("Unsupported save version: " + version);
            }
            int chunkCount = input.readInt();
            if (chunkCount < 0 || chunkCount > 16_384) throw new IOException("Invalid chunk count");

            World loadedWorld = new World();
            int expectedBlockLength = CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE;
            for (int i = 0; i < chunkCount; i++) {
                int cx = input.readInt();
                int cz = input.readInt();
                int blockLength = input.readInt();
                if (Math.abs((long)cx) > 1_000_000 || Math.abs((long)cz) > 1_000_000
                        || blockLength != expectedBlockLength) {
                    throw new IOException("Invalid chunk data");
                }
                Chunk chunk = new Chunk(cx, cz);
                input.readFully(chunk.blocks);
                if (version >= 2) {
                    int stateLength = input.readInt();
                    if (stateLength != chunk.blockStates.length) {
                        throw new IOException("Invalid block state data");
                    }
                    input.readFully(chunk.blockStates);
                }
                for (byte block : chunk.blocks) {
                    if ((block & 0xff) >= BlockType.values().length) {
                        throw new IOException("Invalid block type in save file");
                    }
                }
                for (byte state : chunk.blockStates) {
                    if ((state & 0xff) > 15) throw new IOException("Invalid block state in save file");
                }
                chunk.generated = true;
                if (loadedWorld.chunks.putIfAbsent(key(cx, cz), chunk) != null) {
                    throw new IOException("Duplicate chunk in save file");
                }
            }

            Player loadedPlayer = new Player();
            loadedPlayer.x = input.readFloat();
            loadedPlayer.y = input.readFloat();
            loadedPlayer.z = input.readFloat();
            loadedPlayer.yaw = input.readFloat();
            loadedPlayer.pitch = input.readFloat();
            float loadedSpawnX = loadedPlayer.x;
            float loadedSpawnY = loadedPlayer.y;
            float loadedSpawnZ = loadedPlayer.z;
            if (version >= 2) {
                loadedSpawnX = input.readFloat();
                loadedSpawnY = input.readFloat();
                loadedSpawnZ = input.readFloat();
            }
            if (!Float.isFinite(loadedPlayer.x) || !Float.isFinite(loadedPlayer.y)
                    || !Float.isFinite(loadedPlayer.z) || !Float.isFinite(loadedPlayer.yaw)
                    || !Float.isFinite(loadedPlayer.pitch) || Math.abs(loadedPlayer.x) > 16_000_000.0f
                    || Math.abs(loadedPlayer.z) > 16_000_000.0f || loadedPlayer.y < 0.0f
                    || loadedPlayer.y > 1_000_000.0f) {
                throw new IOException("Invalid player data in save file");
            }
            if (!Float.isFinite(loadedSpawnX) || !Float.isFinite(loadedSpawnY)
                    || !Float.isFinite(loadedSpawnZ) || Math.abs(loadedSpawnX) > 16_000_000.0f
                    || Math.abs(loadedSpawnZ) > 16_000_000.0f || loadedSpawnY < 0.0f
                    || loadedSpawnY > 1_000_000.0f) {
                throw new IOException("Invalid spawn point in save file");
            }
            loadedPlayer.pitch = clamp(loadedPlayer.pitch, -1.50f, 1.50f);
            int selected = input.readInt();
            Inventory loadedInventory = new Inventory(true);
            if (version >= 3) {
                int slotLength = input.readInt();
                if (selected < 0 || selected >= Inventory.HOTBAR_SIZE
                        || slotLength != Inventory.SLOT_COUNT) {
                    throw new IOException("Invalid inventory data");
                }
                loadedInventory.selected = selected;
                for (int i = 0; i < slotLength; i++) {
                    int itemId = input.readInt();
                    int count = input.readInt();
                    if (itemId < -1 || itemId >= ItemType.values().length
                            || count < 0 || count > Inventory.MAX_STACK
                            || (itemId == -1) != (count == 0)) {
                        throw new IOException("Invalid inventory stack");
                    }
                    loadedInventory.items[i] = itemId < 0 ? null : ItemType.values()[itemId];
                    loadedInventory.counts[i] = count;
                }
                int carriedItemId = input.readInt();
                int carriedCount = input.readInt();
                if (carriedItemId < -1 || carriedItemId >= ItemType.values().length
                        || carriedCount < 0 || carriedCount > Inventory.MAX_STACK
                        || (carriedItemId == -1) != (carriedCount == 0)) {
                    throw new IOException("Invalid carried item");
                }
                loadedInventory.carriedItem = carriedItemId < 0
                        ? null : ItemType.values()[carriedItemId];
                loadedInventory.carriedCount = carriedCount;
            } else {
                int countLength = input.readInt();
                if (selected < 0 || selected > 10 || countLength < BlockType.SAND.ordinal() + 1
                        || countLength > 12) {
                    throw new IOException("Invalid legacy inventory data");
                }
                for (int i = 0; i < countLength; i++) {
                    int count = input.readInt();
                    if (count < 0 || count > 9999) throw new IOException("Invalid inventory count");
                    if (i >= BlockType.values().length || count == 0) continue;
                    ItemType legacyItem = itemForBlock(BlockType.values()[i]);
                    if (legacyItem != null && !loadedInventory.addItem(legacyItem, count)) {
                        throw new IOException("Legacy inventory does not fit in the new inventory");
                    }
                }
                ItemType oldSelection = switch (selected) {
                    case 0 -> ItemType.GRASS;
                    case 1 -> ItemType.DIRT;
                    case 2 -> ItemType.STONE;
                    case 3 -> ItemType.OAK_LOG;
                    case 4 -> ItemType.LEAVES;
                    case 5 -> ItemType.TORCH;
                    case 6 -> ItemType.COBBLESTONE;
                    case 7 -> ItemType.SAND;
                    case 8 -> ItemType.WATER;
                    case 9 -> ItemType.DOOR;
                    default -> ItemType.BED;
                };
                for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                    if (loadedInventory.items[i] == oldSelection) {
                        loadedInventory.selected = i;
                        break;
                    }
                }
            }
            if (input.read() != -1) throw new IOException("Unexpected data after save file");

            disposeChunkMeshes(world);
            world = loadedWorld;
            player = loadedPlayer;
            inventory = loadedInventory;
            spawnX = loadedSpawnX;
            spawnY = loadedSpawnY;
            spawnZ = loadedSpawnZ;
        }
    }

    static void refreshSaveEntries() throws IOException {
        Files.createDirectories(SAVES_DIRECTORY);
        saveEntries.clear();
        if (Files.isRegularFile(SAVE_PATH)) {
            saveEntries.add(new SaveEntry("DEFAULT WORLD", SAVE_PATH,
                    Files.getLastModifiedTime(SAVE_PATH).toMillis()));
        }
        try (var files = Files.list(SAVES_DIRECTORY)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".dat"))
                    .toList()) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - 4)
                        .replace('-', ' ').replace('_', ' ').toUpperCase();
                long modified = 0L;
                try {
                    modified = Files.getLastModifiedTime(file).toMillis();
                } catch (IOException ignored) {
                    // Keep the save visible even if its timestamp cannot be read.
                }
                saveEntries.add(new SaveEntry(name, file, modified));
            }
        }
        saveEntries.sort(Comparator.comparingLong(SaveEntry::modifiedMillis).reversed());
        if (selectedSaveIndex >= saveEntries.size()) selectedSaveIndex = saveEntries.size() - 1;
    }

    static void openWorldSelect() {
        try {
            refreshSaveEntries();
            selectedSaveIndex = saveEntries.isEmpty() ? -1 : 0;
            saveListOffset = 0;
            lastWorldRowClickIndex = -1;
            lastWorldRowClickTime = Double.NEGATIVE_INFINITY;
            screen = Screen.WORLD_SELECT;
            menuSelection = saveEntries.isEmpty() ? 0 : 1;
        } catch (IOException e) {
            reportPersistenceError("Could not list saves", e);
        }
    }

    static void openOptions() {
        optionsReturnScreen = screen;
        screen = Screen.OPTIONS;
        menuSelection = 0;
        setMouseCaptured(false);
    }

    static void openCreateWorld() {
        newWorldName = "NEW WORLD";
        screen = Screen.CREATE_WORLD;
        menuSelection = 0;
    }

    static Path uniqueSavePath(String worldName) throws IOException {
        Files.createDirectories(SAVES_DIRECTORY);
        String base = worldName.trim().toUpperCase()
                .replaceAll("[^A-Z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        if (base.isEmpty()) base = "WORLD";
        Path candidate = SAVES_DIRECTORY.resolve(base + ".dat");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = SAVES_DIRECTORY.resolve(base + "-" + suffix++ + ".dat");
        }
        return candidate;
    }

    static void createWorld() {
        String worldName = newWorldName.trim();
        if (worldName.isEmpty()) worldName = "NEW WORLD";
        try {
            activeSavePath = uniqueSavePath(worldName);
            activeWorldName = worldName;
            disposeChunkMeshes(world);
            world = new World();
            player = new Player();
            inventory = new Inventory();
            worldClockSeconds = DAY_LENGTH_SECONDS * 0.25;
            daylightBucket = -1;
            player.y = Math.max(terrainHeight((int)player.x, (int)player.z), 10);
            spawnX = player.x;
            spawnY = player.y;
            spawnZ = player.z;
            ensureWorldAround(player.x, player.z);
            saveWorld(activeSavePath);
            enterGame("New world created");
        } catch (IOException e) {
            reportPersistenceError("Could not create world", e);
        }
    }

    static void playSelectedWorld() {
        if (selectedSaveIndex < 0 || selectedSaveIndex >= saveEntries.size()) {
            setStatus("Select a world first");
            return;
        }
        SaveEntry selectedSave = saveEntries.get(selectedSaveIndex);
        try {
            loadWorld(selectedSave.path());
            activeSavePath = selectedSave.path();
            activeWorldName = selectedSave.name();
            ensureWorldAround(player.x, player.z);
            enterGame("World loaded");
        } catch (IOException e) {
            reportPersistenceError("Could not load world", e);
        }
    }

    static void enterGame(String message) {
        screen = Screen.GAME;
        autosaveTimer = 0.0;
        suppressMouseActionsUntilReleased = true;
        setMouseCaptured(true);
        setStatus(message);
    }

    static void saveAndReturnToTitle() {
        try {
            if (activeSavePath != null) saveWorld(activeSavePath);
            screen = Screen.TITLE;
            menuSelection = 0;
            setMouseCaptured(false);
            setStatus("Saved " + activeWorldName);
        } catch (IOException e) {
            reportPersistenceError("Could not save world", e);
        }
    }

    static void saveAndQuit() {
        try {
            if (activeSavePath != null) saveWorld(activeSavePath);
            glfwSetWindowShouldClose(window, true);
        } catch (IOException e) {
            reportPersistenceError("Could not save world", e);
        }
    }

    static void setStatus(String message) {
        statusMessage = message;
        statusTimer = 3.0;
    }

    static void reportPersistenceError(String message, IOException error) {
        System.err.println(message + ": " + error.getMessage());
        error.printStackTrace(System.err);
        setStatus(message);
    }

    static void moveWithCollision(float dt) {
        float px = player.x;
        float py = player.y;
        float pz = player.z;

        player.onGround = false;

        float maxSpeed = Math.max(Math.abs(player.vx),
                Math.max(Math.abs(player.vy), Math.abs(player.vz)));
        int steps = Math.max(1, (int)Math.ceil(maxSpeed * dt / COLLISION_STEP_DISTANCE));
        float stepDt = dt / steps;
        for (int i = 0; i < steps; i++) {
            float nextX = px + player.vx * stepDt;
            if (intersectsSolid(nextX, py, pz)) {
                player.vx = 0.0f;
            } else {
                px = nextX;
            }

            float nextY = py + player.vy * stepDt;
            if (intersectsSolid(px, nextY, pz)) {
                if (player.vy < 0.0f) player.onGround = true;
                player.vy = 0.0f;
            } else {
                py = nextY;
            }

            float nextZ = pz + player.vz * stepDt;
            if (intersectsSolid(px, py, nextZ)) {
                player.vz = 0.0f;
            } else {
                pz = nextZ;
            }
        }

        player.x = px;
        player.y = py;
        player.z = pz;
    }

    static boolean intersectsSolid(float px, float py, float pz) {
        int minX = (int)Math.floor(px - PLAYER_WIDTH * 0.5f);
        int maxX = (int)Math.floor(px + PLAYER_WIDTH * 0.5f);
        int minY = (int)Math.floor(py);
        int maxY = (int)Math.floor(py + PLAYER_HEIGHT);
        int minZ = (int)Math.floor(pz - PLAYER_WIDTH * 0.5f);
        int maxZ = (int)Math.floor(pz + PLAYER_WIDTH * 0.5f);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    float blockHeight = blockCollisionHeight(x, y, z);
                    if (blockHeight <= 0.0f) continue;
                    if (px + PLAYER_WIDTH * 0.5f > x && px - PLAYER_WIDTH * 0.5f < x + 1.0f
                            && py + PLAYER_HEIGHT > y && py < y + blockHeight
                            && pz + PLAYER_WIDTH * 0.5f > z && pz - PLAYER_WIDTH * 0.5f < z + 1.0f) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static float blockCollisionHeight(int x, int y, int z) {
        BlockType block = getBlock(x, y, z);
        if (!isSolidAt(x, y, z)) return 0.0f;
        return block == BlockType.BED ? 0.58f : 1.0f;
    }

    static boolean isSolidAt(int x, int y, int z) {
        BlockType block = getBlock(x, y, z);
        if (!isSolid(block)) return false;
        return block != BlockType.DOOR || (getBlockState(x, y, z) & 4) == 0;
    }

    static void ensureWorldAround(float px, float pz) {
        int pcx = (int)Math.floor(px / CHUNK_SIZE);
        int pcz = (int)Math.floor(pz / CHUNK_SIZE);

        for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
            for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
                Chunk c = getChunk(pcx + dx, pcz + dz, true);
                if (!c.generated) {
                    generateChunk(c);
                }
            }
        }
    }

    static void generateChunk(Chunk c) {
        if (c.generated) return;

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int wx = c.cx * CHUNK_SIZE + x;
                int wz = c.cz * CHUNK_SIZE + z;
                Biome biome = biomeAt(wx, wz);
                int h = terrainHeight(wx, wz);
                int pondHeight = pondTerrainHeightAt(wx, wz, biome);
                int waterSurface = h < 10 ? 10 : -1;
                if (pondHeight != Integer.MAX_VALUE) {
                    if (pondHeight < POND_WATER_LEVEL) waterSurface = POND_WATER_LEVEL;
                    h = Math.min(h, pondHeight);
                }

                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    BlockType t = BlockType.AIR;

                    if (y == 0) t = BlockType.STONE;
                    else if (y < h - 4) t = BlockType.STONE;
                    else if (y < h - 1) {
                        t = biome == Biome.DESERT || biome == Biome.BEACH
                                ? BlockType.SAND : BlockType.DIRT;
                    } else if (y == h - 1) {
                        t = biome == Biome.DESERT || biome == Biome.BEACH
                                ? BlockType.SAND : (biome == Biome.MOUNTAINS && h > 38
                                ? BlockType.STONE : BlockType.GRASS);
                    }

                    setBlock(c, x, y, z, t);
                }

                if (waterSurface > h) {
                    for (int y = h; y < waterSurface; y++) {
                        setBlock(c, x, y, z, BlockType.WATER);
                    }
                }

                if (waterSurface <= h && biome == Biome.PLAINS && hash2(wx, wz) > 0.985f
                        && h >= 8 && h < WORLD_HEIGHT - 8) {
                    placeTree(c, x, h, z);
                }
            }
        }

        c.generated = true;
        invalidateChunkBorders(c);
    }

    static void placeTree(Chunk c, int x, int y, int z) {
        int trunk = 4 + (int)(hash2(c.cx * 31 + x, c.cz * 31 + z) * 2.0f);

        for (int i = 0; i < trunk; i++) {
            if (y + i < WORLD_HEIGHT) {
                setBlock(c, x, y + i, z, BlockType.WOOD);
            }
        }

        int top = y + trunk;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    int ax = x + dx;
                    int ay = top + dy;
                    int az = z + dz;
                    if (ax < 0 || ax >= CHUNK_SIZE || az < 0 || az >= CHUNK_SIZE) continue;
                    if (ay < 0 || ay >= WORLD_HEIGHT) continue;

                    float d = dx * dx + dy * dy + dz * dz;
                    if (d <= 6.8f && getBlock(c, ax, ay, az) == BlockType.AIR) {
                        setBlock(c, ax, ay, az, BlockType.LEAF);
                    }
                }
            }
        }
    }

    static int terrainHeight(int wx, int wz) {
        Biome biome = baseBiomeAt(wx, wz);
        int height = baseTerrainHeight(wx, wz, biome);
        if (biome != Biome.MOUNTAINS && height >= 11 && height <= 16
                && hasNearbyOcean(wx, wz)) {
            return clamp(height, 11, 13);
        }
        return height;
    }

    static int pondTerrainHeightAt(int wx, int wz, Biome biome) {
        if (biome != Biome.PLAINS) return Integer.MAX_VALUE;

        int cellX = floorDiv(wx, POND_CELL_SIZE);
        int cellZ = floorDiv(wz, POND_CELL_SIZE);
        int targetHeight = Integer.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int seedX = cellX + dx;
                int seedZ = cellZ + dz;
                if (hash2(seedX * 127 + 53, seedZ * 179 - 31) >= 0.30f) continue;

                float centerX = (seedX + 0.20f
                        + hash2(seedX * 43 + 17, seedZ * 71 - 9) * 0.60f) * POND_CELL_SIZE;
                float centerZ = (seedZ + 0.20f
                        + hash2(seedX * 59 - 23, seedZ * 37 + 41) * 0.60f) * POND_CELL_SIZE;
                if (centerX * centerX + centerZ * centerZ < 48.0f * 48.0f) continue;
                if (baseBiomeAt((int)centerX, (int)centerZ) != Biome.PLAINS
                        || baseTerrainHeight((int)centerX, (int)centerZ, Biome.PLAINS)
                        <= POND_WATER_LEVEL + 2) {
                    continue;
                }

                int radius = 4 + Math.min(3,
                        (int)(hash2(seedX * 83 + 5, seedZ * 97 + 13) * 4.0f));
                float deltaX = wx - centerX;
                float deltaZ = wz - centerZ;
                float distance = (float)Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                if (distance >= radius + 3.0f) continue;

                int candidateHeight;
                if (distance < radius) {
                    int depth = 1 + (int)((1.0f - distance / radius) * 3.0f);
                    candidateHeight = POND_WATER_LEVEL - depth;
                } else {
                    candidateHeight = POND_WATER_LEVEL
                            + (int)Math.ceil((distance - radius) * 1.5f);
                }
                targetHeight = Math.min(targetHeight, candidateHeight);
            }
        }
        return targetHeight;
    }

    static Biome biomeAt(int wx, int wz) {
        Biome base = baseBiomeAt(wx, wz);
        if (base == Biome.MOUNTAINS) return base;
        int height = baseTerrainHeight(wx, wz, base);
        if (height >= 11 && height <= 16 && hasNearbyOcean(wx, wz)) return Biome.BEACH;
        return base;
    }

    static Biome baseBiomeAt(int wx, int wz) {
        if (wx * (long)wx + wz * (long)wz < 24L * 24L) return Biome.PLAINS;

        int cellX = floorDiv(wx, BIOME_REGION_SIZE);
        int cellZ = floorDiv(wz, BIOME_REGION_SIZE);
        int nearestX = cellX;
        int nearestZ = cellZ;
        float nearestDistance = Float.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int candidateX = cellX + dx;
                int candidateZ = cellZ + dz;
                float siteX = (candidateX + 0.2f
                        + hash2(candidateX * 31 + 11, candidateZ * 17 - 5) * 0.6f)
                        * BIOME_REGION_SIZE;
                float siteZ = (candidateZ + 0.2f
                        + hash2(candidateX * 23 - 7, candidateZ * 37 + 13) * 0.6f)
                        * BIOME_REGION_SIZE;
                float deltaX = wx - siteX;
                float deltaZ = wz - siteZ;
                float distance = deltaX * deltaX + deltaZ * deltaZ;
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestX = candidateX;
                    nearestZ = candidateZ;
                }
            }
        }
        if (nearestX == 0 && nearestZ == 0) return Biome.PLAINS;

        float biomeChoice = hash2(nearestX * 73 + 19, nearestZ * 97 - 29);
        if (biomeChoice < 0.24f) return Biome.DESERT;
        if (biomeChoice < 0.52f) return Biome.MOUNTAINS;
        return Biome.PLAINS;
    }

    static int baseTerrainHeight(int wx, int wz, Biome biome) {
        float broad = fbm2d(wx, wz);
        float detail = noise2d(wx * 0.12f, wz * 0.12f);
        float height;
        switch (biome) {
            case MOUNTAINS:
                float ridge = 1.0f - Math.abs(noise2d(wx * 0.018f, wz * 0.018f));
                height = 25.0f + broad * 12.0f + ridge * 15.0f + detail * 2.0f;
                break;
            case DESERT:
                height = 16.0f + broad * 4.5f + detail * 1.4f;
                break;
            default:
                height = 19.0f + broad * 3.0f + detail * 0.9f;
                break;
        }
        return clamp((int)height, 6, WORLD_HEIGHT - 4);
    }

    static boolean hasNearbyOcean(int wx, int wz) {
        return baseTerrainHeight(wx + 5, wz, baseBiomeAt(wx + 5, wz)) <= 10
                || baseTerrainHeight(wx - 5, wz, baseBiomeAt(wx - 5, wz)) <= 10
                || baseTerrainHeight(wx, wz + 5, baseBiomeAt(wx, wz + 5)) <= 10
                || baseTerrainHeight(wx, wz - 5, baseBiomeAt(wx, wz - 5)) <= 10;
    }

    static float fbm2d(float x, float z) {
        float sum = 0.0f;
        float amp = 1.0f;
        float freq = 0.03f;
        for (int i = 0; i < 5; i++) {
            sum += noise2d(x * freq, z * freq) * amp;
            freq *= 2.0f;
            amp *= 0.5f;
        }
        return sum;
    }

    static float noise2d(float x, float z) {
        int xi = (int)Math.floor(x);
        int zi = (int)Math.floor(z);
        float xf = x - (float)Math.floor(x);
        float zf = z - (float)Math.floor(z);

        float v00 = hash2(xi, zi);
        float v10 = hash2(xi + 1, zi);
        float v01 = hash2(xi, zi + 1);
        float v11 = hash2(xi + 1, zi + 1);

        float u = smooth(xf);
        float v = smooth(zf);

        float a = lerp(v00, v10, u);
        float b = lerp(v01, v11, u);
        return lerp(a, b, v) * 2.0f - 1.0f;
    }

    static float hash2(int x, int z) {
        long n = x * 374761393L + z * 668265263L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n ^= (n >> 16);
        return (float)((n & 0x7fffffffL) / 2147483647.0);
    }

    static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static Chunk getChunk(int cx, int cz, boolean create) {
        long key = key(cx, cz);
        Chunk c = world.chunks.get(key);
        if (c == null && create) {
            c = new Chunk(cx, cz);
            world.chunks.put(key, c);
        }
        return c;
    }

    static long key(int cx, int cz) {
        return (((long)cx) << 32) ^ (cz & 0xffffffffL);
    }

    static BlockType getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= WORLD_HEIGHT) return BlockType.AIR;

        int cx = floorDiv(wx, CHUNK_SIZE);
        int cz = floorDiv(wz, CHUNK_SIZE);
        Chunk c = getChunk(cx, cz, false);
        if (c == null) return BlockType.AIR;

        int lx = floorMod(wx, CHUNK_SIZE);
        int lz = floorMod(wz, CHUNK_SIZE);

        return getBlock(c, lx, wy, lz);
    }

    static BlockType getBlock(Chunk c, int x, int y, int z) {
        int idx = c.idx(x, y, z);
        return BlockType.values()[c.blocks[idx]];
    }

    static void setBlock(Chunk c, int x, int y, int z, BlockType t) {
        int idx = c.idx(x, y, z);
        c.blocks[idx] = (byte)t.ordinal();
    }

    static boolean isSolid(BlockType t) {
        return t != BlockType.AIR && t != BlockType.WATER && t != BlockType.TORCH;
    }

    static float[] colorOf(BlockType t) {
        switch (t) {
            case GRASS: return new float[]{0.37f, 0.62f, 0.21f, 1.0f};
            case DIRT:  return new float[]{0.53f, 0.38f, 0.26f, 1.0f};
            case STONE: return new float[]{0.49f, 0.49f, 0.49f, 1.0f};
            case WOOD:  return new float[]{0.40f, 0.28f, 0.17f, 1.0f};
            case LEAF:  return new float[]{0.22f, 0.55f, 0.22f, 1.0f};
            case WATER: return new float[]{0.20f, 0.36f, 0.75f, 0.65f};
            case TORCH: return new float[]{1.0f, 0.86f, 0.47f, 1.0f};
            case COBBLE:return new float[]{0.41f, 0.41f, 0.41f, 1.0f};
            case SAND:  return new float[]{0.84f, 0.78f, 0.52f, 1.0f};
            case DOOR:  return new float[]{0.43f, 0.26f, 0.12f, 1.0f};
            case BED:   return new float[]{0.72f, 0.10f, 0.12f, 1.0f};
            case PLANKS:return new float[]{0.66f, 0.48f, 0.27f, 1.0f};
            case GLASS: return new float[]{0.60f, 0.82f, 0.91f, 0.74f};
            case BRICKS:return new float[]{0.61f, 0.26f, 0.19f, 1.0f};
            case BOOKSHELF:return new float[]{0.42f, 0.26f, 0.14f, 1.0f};
            case CRAFTING_TABLE:return new float[]{0.58f, 0.39f, 0.21f, 1.0f};
            case FURNACE:return new float[]{0.30f, 0.30f, 0.31f, 1.0f};
            case CHEST:return new float[]{0.57f, 0.35f, 0.13f, 1.0f};
            case WOOL:return new float[]{0.83f, 0.81f, 0.76f, 1.0f};
            case TNT:return new float[]{0.79f, 0.18f, 0.13f, 1.0f};
            case LADDER:return new float[]{0.58f, 0.42f, 0.24f, 1.0f};
            case FLOWER:return new float[]{0.87f, 0.22f, 0.40f, 1.0f};
            case MUSHROOM:return new float[]{0.64f, 0.18f, 0.13f, 1.0f};
            case CACTUS:return new float[]{0.19f, 0.49f, 0.20f, 1.0f};
            case CLAY:return new float[]{0.47f, 0.57f, 0.64f, 1.0f};
            case OBSIDIAN:return new float[]{0.17f, 0.13f, 0.25f, 1.0f};
            case ICE:return new float[]{0.55f, 0.78f, 0.92f, 0.82f};
            case SNOW:return new float[]{0.91f, 0.95f, 0.98f, 1.0f};
            case PUMPKIN:return new float[]{0.90f, 0.43f, 0.10f, 1.0f};
            case MELON:return new float[]{0.28f, 0.65f, 0.24f, 1.0f};
            case FENCE:return new float[]{0.48f, 0.33f, 0.18f, 1.0f};
            case STONE_BRICKS:return new float[]{0.48f, 0.48f, 0.48f, 1.0f};
            case GLOWSTONE:return new float[]{0.93f, 0.71f, 0.30f, 1.0f};
            case NETHERRACK:return new float[]{0.39f, 0.15f, 0.16f, 1.0f};
            case SOUL_SAND:return new float[]{0.39f, 0.29f, 0.21f, 1.0f};
            case LAPIS_BLOCK:return new float[]{0.16f, 0.28f, 0.69f, 1.0f};
            case GOLD_BLOCK:return new float[]{0.94f, 0.72f, 0.16f, 1.0f};
            case IRON_BLOCK:return new float[]{0.73f, 0.75f, 0.75f, 1.0f};
            case DIAMOND_BLOCK:return new float[]{0.23f, 0.77f, 0.75f, 1.0f};
            case EMERALD_BLOCK:return new float[]{0.12f, 0.66f, 0.36f, 1.0f};
            case REDSTONE_BLOCK:return new float[]{0.64f, 0.08f, 0.08f, 1.0f};
            case HAY_BALE:return new float[]{0.78f, 0.66f, 0.19f, 1.0f};
            case QUARTZ_BLOCK:return new float[]{0.86f, 0.83f, 0.77f, 1.0f};
            case RAIL:return new float[]{0.57f, 0.47f, 0.32f, 1.0f};
            case GLASS_PANE:return new float[]{0.67f, 0.85f, 0.93f, 0.86f};
            case FENCE_GATE:return new float[]{0.50f, 0.35f, 0.19f, 1.0f};
            case LEVER:return new float[]{0.49f, 0.43f, 0.35f, 1.0f};
            case REDSTONE_LAMP:return new float[]{0.79f, 0.47f, 0.22f, 1.0f};
            case SANDSTONE:return new float[]{0.80f, 0.72f, 0.46f, 1.0f};
            case END_STONE:return new float[]{0.78f, 0.77f, 0.53f, 1.0f};
            default:    return new float[]{0, 0, 0, 0};
        }
    }

    static ItemType itemForBlock(BlockType block) {
        return switch (block) {
            case GRASS -> ItemType.GRASS;
            case DIRT -> ItemType.DIRT;
            case STONE -> ItemType.STONE;
            case WOOD -> ItemType.OAK_LOG;
            case LEAF -> ItemType.LEAVES;
            case WATER -> ItemType.WATER;
            case TORCH -> ItemType.TORCH;
            case COBBLE -> ItemType.COBBLESTONE;
            case SAND -> ItemType.SAND;
            case DOOR -> ItemType.DOOR;
            case BED -> ItemType.BED;
            case PLANKS -> ItemType.PLANKS;
            case GLASS -> ItemType.GLASS;
            case BRICKS -> ItemType.BRICK_BLOCK;
            case BOOKSHELF -> ItemType.BOOKSHELF;
            case CRAFTING_TABLE -> ItemType.CRAFTING_TABLE;
            case FURNACE -> ItemType.FURNACE;
            case CHEST -> ItemType.CHEST;
            case WOOL -> ItemType.WOOL;
            case TNT -> ItemType.TNT;
            case LADDER -> ItemType.LADDER;
            case FLOWER -> ItemType.FLOWER;
            case MUSHROOM -> ItemType.MUSHROOM;
            case CACTUS -> ItemType.CACTUS;
            case CLAY -> ItemType.CLAY;
            case OBSIDIAN -> ItemType.OBSIDIAN;
            case ICE -> ItemType.ICE;
            case SNOW -> ItemType.SNOW;
            case PUMPKIN -> ItemType.PUMPKIN;
            case MELON -> ItemType.MELON;
            case FENCE -> ItemType.FENCE;
            case STONE_BRICKS -> ItemType.STONE_BRICKS;
            case GLOWSTONE -> ItemType.GLOWSTONE;
            case NETHERRACK -> ItemType.NETHERRACK;
            case SOUL_SAND -> ItemType.SOUL_SAND;
            case LAPIS_BLOCK -> ItemType.LAPIS_BLOCK;
            case GOLD_BLOCK -> ItemType.GOLD_BLOCK;
            case IRON_BLOCK -> ItemType.IRON_BLOCK;
            case DIAMOND_BLOCK -> ItemType.DIAMOND_BLOCK;
            case EMERALD_BLOCK -> ItemType.EMERALD_BLOCK;
            case REDSTONE_BLOCK -> ItemType.REDSTONE_BLOCK;
            case HAY_BALE -> ItemType.HAY_BALE;
            case QUARTZ_BLOCK -> ItemType.QUARTZ_BLOCK;
            case RAIL -> ItemType.RAIL;
            case GLASS_PANE -> ItemType.GLASS_PANE;
            case FENCE_GATE -> ItemType.FENCE_GATE;
            case LEVER -> ItemType.LEVER;
            case REDSTONE_LAMP -> ItemType.REDSTONE_LAMP;
            case SANDSTONE -> ItemType.SANDSTONE;
            case END_STONE -> ItemType.END_STONE;
            case AIR -> null;
        };
    }

    static float[] itemColor(ItemType item) {
        if (item == null) return new float[]{0.4f, 0.4f, 0.4f, 1.0f};
        if (item.block() != null) return colorOf(item.block());
        return switch (item) {
            case STICK -> new float[]{0.60f, 0.40f, 0.20f, 1.0f};
            case COAL, CHARCOAL -> new float[]{0.15f, 0.16f, 0.18f, 1.0f};
            case IRON_INGOT, IRON_ORE -> new float[]{0.73f, 0.74f, 0.73f, 1.0f};
            case GOLD_INGOT, GOLD_ORE -> new float[]{0.96f, 0.74f, 0.20f, 1.0f};
            case DIAMOND -> new float[]{0.22f, 0.84f, 0.82f, 1.0f};
            case EMERALD -> new float[]{0.10f, 0.72f, 0.36f, 1.0f};
            case LAPIS_LAZULI, BLUE_DYE -> new float[]{0.18f, 0.31f, 0.78f, 1.0f};
            case REDSTONE_DUST, RED_DYE -> new float[]{0.78f, 0.12f, 0.13f, 1.0f};
            case WHEAT, HAY_BALE -> new float[]{0.80f, 0.70f, 0.25f, 1.0f};
            case GREEN_DYE -> new float[]{0.19f, 0.62f, 0.25f, 1.0f};
            case YELLOW_DYE -> new float[]{0.94f, 0.82f, 0.20f, 1.0f};
            case WHITE_DYE, FEATHER, PAPER, BOOK -> new float[]{0.87f, 0.86f, 0.80f, 1.0f};
            case APPLE, RAW_BEEF, PORKCHOP, RAW_FISH -> new float[]{0.78f, 0.27f, 0.20f, 1.0f};
            case COOKED_BEEF, COOKED_PORKCHOP, COOKED_FISH, BREAD, COOKIE,
                    CAKE, PUMPKIN_PIE -> new float[]{0.72f, 0.49f, 0.25f, 1.0f};
            case BUCKET, WATER_BUCKET, COMPASS, CLOCK, MINECART -> new float[]{0.62f, 0.66f, 0.68f, 1.0f};
            case BOW, FISHING_ROD, WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL,
                    WOODEN_SWORD, WOODEN_HOE -> new float[]{0.56f, 0.38f, 0.21f, 1.0f};
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD, STONE_HOE,
                    FLINT, FLINT_AND_STEEL -> new float[]{0.53f, 0.54f, 0.55f, 1.0f};
            case IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD, IRON_HOE,
                    SHEARS -> new float[]{0.70f, 0.72f, 0.73f, 1.0f};
            case DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD,
                    DIAMOND_HOE -> new float[]{0.24f, 0.79f, 0.77f, 1.0f};
            default -> new float[]{0.72f, 0.60f, 0.36f, 1.0f};
        };
    }

    static void setPerspective(float fovY, float aspect, float zNear, float zFar) {
        float top = (float)(Math.tan(Math.toRadians(fovY) / 2.0) * zNear);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(left, right, bottom, top, zNear, Math.min(zFar, VIEW_DISTANCE + CHUNK_SIZE));
        glMatrixMode(GL_MODELVIEW);
    }

    static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    static int floorMod(int a, int b) {
        int r = a % b;
        if (r < 0) r += b;
        return r;
    }

    static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
}
