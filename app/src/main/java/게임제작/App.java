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
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class App {

    static final int SCREEN_W = 1280;
    static final int SCREEN_H = 720;

    static final int CHUNK_SIZE = 16;
    static final int WORLD_HEIGHT = 64;
    static final int RENDER_DISTANCE = 6;
    static final int CHUNK_MESH_BUILD_BUDGET = 2;
    static final float VIEW_DISTANCE = 64.0f;

    static final float PLAYER_WIDTH = 0.60f;
    static final float PLAYER_HEIGHT = 1.80f;
    static final float PLAYER_EYE = 1.62f;
    static final float GRAVITY = 24.0f;
    static final float JUMP_VEL = 8.5f;
    static final float FLIGHT_SPEED = 8.0f;
    static final double DOUBLE_TAP_SECONDS = 0.35;
    static final float MOVE_SPEED = 6.0f;
    static final float RUN_SPEED = 9.5f;
    static final float REACH_DISTANCE = 6.0f;
    static final int SAVE_MAGIC = 0x4D574C44;
    static final int SAVE_VERSION = 1;
    static final Path SAVE_PATH = Path.of(System.getProperty("user.home"),
            ".mineworld-game-project", "world.dat");

    enum BlockType {
        AIR, GRASS, DIRT, STONE, WOOD, LEAF, WATER, TORCH, COBBLE, SAND
    }

    static final class Chunk {
        final int cx, cz;
        boolean generated;
        int displayList;
        int transparentDisplayList;
        boolean meshDirty = true;
        final byte[] blocks = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
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
        final BlockType[] hotbar = new BlockType[9];
        final int[] counts = new int[BlockType.values().length];
        int selected = 0;

        Inventory() {
            hotbar[0] = BlockType.GRASS;
            hotbar[1] = BlockType.DIRT;
            hotbar[2] = BlockType.STONE;
            hotbar[3] = BlockType.WOOD;
            hotbar[4] = BlockType.LEAF;
            hotbar[5] = BlockType.TORCH;
            hotbar[6] = BlockType.COBBLE;
            hotbar[7] = BlockType.SAND;
            hotbar[8] = BlockType.WATER;

            counts[BlockType.GRASS.ordinal()] = 256;
            counts[BlockType.DIRT.ordinal()] = 256;
            counts[BlockType.STONE.ordinal()] = 256;
            counts[BlockType.WOOD.ordinal()] = 64;
            counts[BlockType.LEAF.ordinal()] = 128;
            counts[BlockType.TORCH.ordinal()] = 64;
            counts[BlockType.COBBLE.ordinal()] = 64;
            counts[BlockType.SAND.ordinal()] = 64;
            counts[BlockType.WATER.ordinal()] = 64;
        }

        BlockType selectedBlock() {
            return hotbar[Math.max(0, Math.min(8, selected))];
        }
    }

    record BlockHit(int x, int y, int z, int previousX, int previousY, int previousZ) {}

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
    static boolean saveKeyWasDown;
    static boolean loadKeyWasDown;
    static boolean escapeKeyWasDown;
    static boolean captureKeyWasDown;
    static boolean spaceKeyWasDown;
    static boolean flightMode;
    static double lastSpacePressTime = Double.NEGATIVE_INFINITY;
    static double mouseDeltaX;
    static double mouseDeltaY;
    static double lastMouseX;
    static double lastMouseY;
    static boolean mousePositionInitialized;

    public static void main(String[] args) {
        GLFWErrorCallback.createPrint(System.err).set();

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

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
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
        player.y = Math.max(terrainHeight((int)player.x, (int)player.z), 10);
        inventory = new Inventory();

        ensureWorldAround(player.x, player.z);

        loop();

        disposeChunkMeshes(world);
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
                        ? "Mineworld | " + displayedFps + " FPS | " + statusMessage
                        : "Mineworld | " + displayedFps + " FPS");
            }

            update(dt);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    static void update(float dt) {
        statusTimer = Math.max(0.0, statusTimer - dt);
        boolean escapeDown = glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS;
        boolean captureDown = glfwGetKey(window, GLFW_KEY_F3) == GLFW_PRESS;
        if (escapeDown && !escapeKeyWasDown) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            mousePositionInitialized = false;
            mouseDeltaX = 0.0;
            mouseDeltaY = 0.0;
        }
        if (captureDown && !captureKeyWasDown) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            double[] cursorX = new double[1];
            double[] cursorY = new double[1];
            glfwGetCursorPos(window, cursorX, cursorY);
            lastMouseX = cursorX[0];
            lastMouseY = cursorY[0];
            mousePositionInitialized = true;
        }
        escapeKeyWasDown = escapeDown;
        captureKeyWasDown = captureDown;

        for (int i = 0; i < 9; i++) {
            int key = GLFW_KEY_1 + i;
            if (glfwGetKey(window, key) == GLFW_PRESS) {
                inventory.selected = i;
            }
        }

        if (glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
            player.yaw += (float)mouseDeltaX * 0.0032f;
            player.pitch -= (float)mouseDeltaY * 0.0032f;
            player.pitch = clamp(player.pitch, -1.50f, 1.50f);
            mouseDeltaX = 0.0;
            mouseDeltaY = 0.0;
        }

        float yaw = player.yaw;
        float pitch = player.pitch;

        float fx = (float)(Math.cos(pitch) * Math.sin(yaw));
        float fy = (float)(Math.sin(pitch));
        float fz = (float)(Math.cos(pitch) * Math.cos(yaw));

        float speed = (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) ? RUN_SPEED : MOVE_SPEED;
        boolean forward = isKeyDown(GLFW_KEY_W) || isKeyDown(GLFW_KEY_UP);
        boolean backward = isKeyDown(GLFW_KEY_S) || isKeyDown(GLFW_KEY_DOWN);
        boolean left = isKeyDown(GLFW_KEY_A) || isKeyDown(GLFW_KEY_LEFT);
        boolean right = isKeyDown(GLFW_KEY_D) || isKeyDown(GLFW_KEY_RIGHT);
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

        boolean leftMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
        boolean rightMouseDown = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
        if (leftMouseDown && !leftMouseWasDown) {
            raycastBreak(fx, fy, fz);
        }
        if (rightMouseDown && !rightMouseWasDown) {
            raycastPlace(fx, fy, fz);
        }
        leftMouseWasDown = leftMouseDown;
        rightMouseWasDown = rightMouseDown;

        boolean saveDown = glfwGetKey(window, GLFW_KEY_F5) == GLFW_PRESS;
        boolean loadDown = glfwGetKey(window, GLFW_KEY_F9) == GLFW_PRESS;
        if (saveDown && !saveKeyWasDown) {
            try {
                saveWorld();
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

    static boolean isKeyDown(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
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

    static void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];
        glfwGetFramebufferSize(window, framebufferWidth, framebufferHeight);
        int width = Math.max(1, framebufferWidth[0]);
        int height = Math.max(1, framebufferHeight[0]);
        glViewport(0, 0, width, height);
        float aspect = (float) width / (float) height;
        setPerspective(70.0f, aspect, 0.1f, 1000.0f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glRotatef((float)Math.toDegrees(player.pitch), 1, 0, 0);
        glRotatef((float)Math.toDegrees(player.yaw), 0, 1, 0);
        glTranslatef(-player.x, -(player.y + PLAYER_EYE), -player.z);

        drawWorld(aspect);
        drawHud(width, height);
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
        float forwardY = sinPitch;
        float forwardZ = cosPitch * cosYaw;
        float rightX = cosYaw;
        float rightZ = -sinYaw;
        float upX = -sinPitch * sinYaw;
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
                                    chunk.sunlight[index] & 0xff, chunk.torchlight[index] & 0xff);
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

    static void drawBlock(int x, int y, int z, BlockType t, int sunlight, int torchlight) {
        if (t == BlockType.TORCH) {
            drawTorch(x, y, z);
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
        float illumination = Math.min(1.0f, 0.20f + sunlight * (0.68f / 15.0f)
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

    static void drawTorch(int x, int y, int z) {
        drawCuboid(x + 0.45f, y + 0.18f, z + 0.45f,
                x + 0.55f, y + 0.72f, z + 0.55f, 0.34f, 0.22f, 0.10f);
        drawCuboid(x + 0.39f, y + 0.67f, z + 0.39f,
                x + 0.61f, y + 0.83f, z + 0.61f, 1.0f, 0.38f, 0.06f);
        drawCuboid(x + 0.43f, y + 0.81f, z + 0.43f,
                x + 0.57f, y + 0.94f, z + 0.57f, 1.0f, 0.82f, 0.28f);
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
                || neighbor == BlockType.TORCH;
    }

    static void setBlockFaceColor(float[] color, float brightness) {
        glColor4f(color[0] * brightness, color[1] * brightness,
                color[2] * brightness, color[3]);
    }

    static void drawHud(int width, int height) {
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
        glColor4f(0.05f, 0.05f, 0.05f, 0.75f);
        glBegin(GL_QUADS);
        glVertex2f(centerX - 1.5f, centerY - 8.0f);
        glVertex2f(centerX + 1.5f, centerY - 8.0f);
        glVertex2f(centerX + 1.5f, centerY + 8.0f);
        glVertex2f(centerX - 1.5f, centerY + 8.0f);
        glVertex2f(centerX - 8.0f, centerY - 1.5f);
        glVertex2f(centerX + 8.0f, centerY - 1.5f);
        glVertex2f(centerX + 8.0f, centerY + 1.5f);
        glVertex2f(centerX - 8.0f, centerY + 1.5f);
        glEnd();

        int slotSize = 44;
        int gap = 3;
        int barWidth = slotSize * inventory.hotbar.length + gap * (inventory.hotbar.length - 1);
        int startX = (width - barWidth) / 2;
        int top = height - slotSize - 18;
        for (int i = 0; i < inventory.hotbar.length; i++) {
            int x = startX + i * (slotSize + gap);
            glColor4f(0.04f, 0.04f, 0.04f, 0.78f);
            drawHudRect(x, top, slotSize, slotSize);
            glColor4f(i == inventory.selected ? 1.0f : 0.55f,
                    i == inventory.selected ? 0.85f : 0.55f,
                    i == inventory.selected ? 0.25f : 0.55f, 0.95f);
            drawHudOutline(x, top, slotSize, slotSize, i == inventory.selected ? 3 : 1);

            BlockType block = inventory.hotbar[i];
            if (block == BlockType.WATER) {
                glColor4f(0.25f, 0.60f, 0.90f, 0.72f);
                drawHudRect(x + 9, top + 22, 26, 9);
                glColor4f(0.60f, 0.82f, 1.0f, 0.85f);
                drawHudRect(x + 12, top + 19, 19, 2);
            } else {
                float[] color = colorOf(block);
                glColor4f(color[0], color[1], color[2], color[3]);
                drawHudRect(x + 13, top + 11, 18, 18);
            }
            int count = inventory.counts[inventory.hotbar[i].ordinal()];
            if (count > 0) {
                glColor4f(0.95f, 0.95f, 0.95f, 0.95f);
                drawHudRect(x + 5, top + slotSize - 5,
                        Math.max(2, (slotSize - 10) * Math.min(count, 64) / 64), 2);
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

    static void raycastBreak(float fx, float fy, float fz) {
        BlockHit hit = raycastFrom(player.x, player.y + PLAYER_EYE, player.z, fx, fy, fz);
        if (hit != null) {
            breakBlock(hit);
        }
    }

    static void raycastPlace(float fx, float fy, float fz) {
        BlockHit hit = raycastFrom(player.x, player.y + PLAYER_EYE, player.z, fx, fy, fz);
        if (hit != null) {
            placeBlock(hit, inventory.selectedBlock());
        }
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
        setWorldBlock(hit.x(), hit.y(), hit.z(), BlockType.AIR);
        if (inventory != null && block != BlockType.WATER) {
            int slot = block.ordinal();
            inventory.counts[slot] = Math.min(9999, inventory.counts[slot] + 1);
        }
        return true;
    }

    static boolean placeBlock(BlockHit hit, BlockType block) {
        if (block == null || block == BlockType.AIR) return false;
        if (getBlock(hit.x(), hit.y(), hit.z()) == BlockType.AIR) return false;
        if (getBlock(hit.previousX(), hit.previousY(), hit.previousZ()) != BlockType.AIR) return false;
        if (inventory != null && inventory.counts[block.ordinal()] <= 0) return false;
        if (intersectsPlayerBlock(hit.previousX(), hit.previousY(), hit.previousZ())) return false;
        setWorldBlock(hit.previousX(), hit.previousY(), hit.previousZ(), block);
        if (inventory != null) inventory.counts[block.ordinal()]--;
        return true;
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
        return neighbor == BlockType.AIR || neighbor == BlockType.WATER || neighbor == BlockType.TORCH;
    }

    static void setWorldBlock(int x, int y, int z, BlockType block) {
        if (y < 0 || y >= WORLD_HEIGHT) return;
        int cx = floorDiv(x, CHUNK_SIZE);
        int cz = floorDiv(z, CHUNK_SIZE);
        Chunk chunk = getChunk(cx, cz, true);
        if (!chunk.generated) generateChunk(chunk);
        int localX = floorMod(x, CHUNK_SIZE);
        int localZ = floorMod(z, CHUNK_SIZE);
        BlockType previous = getBlock(chunk, localX, y, localZ);
        setBlock(chunk, localX, y, localZ, block);
        invalidateChunkAndNeighbors(chunk, localX, localZ);
        if (previous == BlockType.TORCH || block == BlockType.TORCH) {
            invalidateTorchLightingAround(chunk);
        }
    }

    static void saveWorld() throws IOException {
        saveWorld(SAVE_PATH);
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
                }
                output.writeFloat(player.x);
                output.writeFloat(player.y);
                output.writeFloat(player.z);
                output.writeFloat(player.yaw);
                output.writeFloat(player.pitch);
                output.writeInt(inventory.selected);
                output.writeInt(inventory.counts.length);
                for (int count : inventory.counts) output.writeInt(count);
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
        loadWorld(SAVE_PATH);
    }

    static void loadWorld(Path source) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(source))) {
            if (input.readInt() != SAVE_MAGIC) throw new IOException("Unrecognized save file");
            int version = input.readInt();
            if (version != SAVE_VERSION) throw new IOException("Unsupported save version: " + version);
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
                for (byte block : chunk.blocks) {
                    if ((block & 0xff) >= BlockType.values().length) {
                        throw new IOException("Invalid block type in save file");
                    }
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
            if (!Float.isFinite(loadedPlayer.x) || !Float.isFinite(loadedPlayer.y)
                    || !Float.isFinite(loadedPlayer.z) || !Float.isFinite(loadedPlayer.yaw)
                    || !Float.isFinite(loadedPlayer.pitch) || Math.abs(loadedPlayer.x) > 16_000_000.0f
                    || Math.abs(loadedPlayer.z) > 16_000_000.0f || loadedPlayer.y < 0.0f
                    || loadedPlayer.y > 1_000_000.0f) {
                throw new IOException("Invalid player data in save file");
            }
            loadedPlayer.pitch = clamp(loadedPlayer.pitch, -1.50f, 1.50f);
            int selected = input.readInt();
            int countLength = input.readInt();
            if (selected < 0 || selected >= 9 || countLength != BlockType.values().length) {
                throw new IOException("Invalid inventory data");
            }
            Inventory loadedInventory = new Inventory();
            loadedInventory.selected = selected;
            for (int i = 0; i < countLength; i++) {
                int count = input.readInt();
                if (count < 0 || count > 9999) throw new IOException("Invalid inventory count");
                loadedInventory.counts[i] = count;
            }
            if (input.read() != -1) throw new IOException("Unexpected data after save file");

            disposeChunkMeshes(world);
            world = loadedWorld;
            player = loadedPlayer;
            inventory = loadedInventory;
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

        px += player.vx * dt;
        if (intersectsSolid(px, py, pz)) {
            px -= player.vx * dt;
            player.vx = 0.0f;
        }

        py += player.vy * dt;
        if (intersectsSolid(px, py, pz)) {
            if (player.vy < 0.0f) player.onGround = true;
            py -= player.vy * dt;
            player.vy = 0.0f;
        }

        pz += player.vz * dt;
        if (intersectsSolid(px, py, pz)) {
            pz -= player.vz * dt;
            player.vz = 0.0f;
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
                    if (isSolid(getBlock(x, y, z))) return true;
                }
            }
        }
        return false;
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
                int h = terrainHeight(wx, wz);

                for (int y = 0; y < WORLD_HEIGHT; y++) {
                    BlockType t = BlockType.AIR;

                    if (y == 0) t = BlockType.STONE;
                    else if (y < h - 4) t = BlockType.STONE;
                    else if (y < h - 1) t = BlockType.DIRT;
                    else if (y == h - 1) t = (h < 12) ? BlockType.SAND : BlockType.GRASS;
                    else if (y < 10 && h < 10) t = BlockType.WATER;

                    setBlock(c, x, y, z, t);
                }

                if (h < 10) {
                    for (int y = h; y < 10; y++) {
                        setBlock(c, x, y, z, BlockType.WATER);
                    }
                }

                if (hash2(wx, wz) > 0.985f && h >= 8 && h < WORLD_HEIGHT - 8) {
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
        float h = 22.0f + fbm2d(wx, wz) * 10.0f;
        h += noise2d(wx * 0.2f, wz * 0.2f) * 3.0f;
        return clamp((int)h, 6, WORLD_HEIGHT - 4);
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
            default:    return new float[]{0, 0, 0, 0};
        }
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