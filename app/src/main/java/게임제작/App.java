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

    static final float PLAYER_WIDTH = 0.60f;
    static final float PLAYER_HEIGHT = 1.80f;
    static final float PLAYER_EYE = 1.62f;
    static final float GRAVITY = 24.0f;
    static final float JUMP_VEL = 8.5f;
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
        final byte[] blocks = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];
        final byte[] light = new byte[CHUNK_SIZE * WORLD_HEIGHT * CHUNK_SIZE];

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

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.2f, 0.5f, 1.0f, 1.0f);

        world = new World();
        player = new Player();
        player.y = Math.max(terrainHeight((int)player.x, (int)player.z), 10);
        inventory = new Inventory();

        ensureWorldAround(player.x, player.z);

        loop();

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
        }
        if (captureDown && !captureKeyWasDown) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetWindowSize(window, width, height);
            glfwSetCursorPos(window, width[0] * 0.5, height[0] * 0.5);
        }
        escapeKeyWasDown = escapeDown;
        captureKeyWasDown = captureDown;

        for (int i = 0; i < 9; i++) {
            int key = GLFW_KEY_1 + i;
            if (glfwGetKey(window, key) == GLFW_PRESS) {
                inventory.selected = i;
            }
        }

        float mouseDX = 0.0f;
        float mouseDY = 0.0f;

        if (glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
            double[] mx = new double[1];
            double[] my = new double[1];
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetCursorPos(window, mx, my);
            glfwGetWindowSize(window, width, height);
            double centerX = width[0] * 0.5;
            double centerY = height[0] * 0.5;
            mouseDX = (float)(mx[0] - centerX);
            mouseDY = (float)(my[0] - centerY);
            glfwSetCursorPos(window, centerX, centerY);

            player.yaw += mouseDX * 0.0032f;
            player.pitch -= mouseDY * 0.0032f;
            player.pitch = clamp(player.pitch, -1.50f, 1.50f);
        }

        float yaw = player.yaw;
        float pitch = player.pitch;

        float fx = (float)(Math.cos(pitch) * Math.sin(yaw));
        float fy = (float)(Math.sin(pitch));
        float fz = (float)(Math.cos(pitch) * Math.cos(yaw));

        float rx = (float)Math.sin(yaw - Math.PI / 2.0);
        float rz = (float)Math.cos(yaw - Math.PI / 2.0);

        float speed = (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) ? RUN_SPEED : MOVE_SPEED;

        float wishX = 0.0f;
        float wishZ = 0.0f;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            wishX += fx;
            wishZ += fz;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            wishX -= fx;
            wishZ -= fz;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            wishX += rx;
            wishZ += rz;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            wishX -= rx;
            wishZ -= rz;
        }

        float len = (float)Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (len > 0.0f) {
            wishX /= len;
            wishZ /= len;
        }

        player.vx = wishX * speed;
        player.vz = wishZ * speed;

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && player.onGround) {
            player.vy = JUMP_VEL;
        }

        player.vy -= GRAVITY * dt;

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

        drawGrid(64, 1.0f);
        drawWorld();
        drawHud(width, height);
    }

    static void drawWorld() {
        int pcx = (int)Math.floor(player.x / CHUNK_SIZE);
        int pcz = (int)Math.floor(player.z / CHUNK_SIZE);

        for (int cz = pcz - RENDER_DISTANCE; cz <= pcz + RENDER_DISTANCE; cz++) {
            for (int cx = pcx - RENDER_DISTANCE; cx <= pcx + RENDER_DISTANCE; cx++) {
                Chunk c = getChunk(cx, cz, false);
                if (c == null || !c.generated) continue;

                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int y = 0; y < WORLD_HEIGHT; y++) {
                        for (int z = 0; z < CHUNK_SIZE; z++) {
                            BlockType t = getBlock(c, x, y, z);
                            if (t == BlockType.AIR) continue;

                            int wx = cx * CHUNK_SIZE + x;
                            int wz = cz * CHUNK_SIZE + z;

                            // 너무 멀면 생략
                            float dx = wx + 0.5f - player.x;
                            float dy = y + 0.5f - (player.y + PLAYER_EYE);
                            float dz = wz + 0.5f - player.z;
                            if (dx * dx + dy * dy + dz * dz > 90.0f * 90.0f) continue;

                            drawBlock(wx, y, wz, t);
                        }
                    }
                }
            }
        }
    }

    static void drawBlock(int x, int y, int z, BlockType t) {
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
        glBegin(GL_QUADS);
        if (frontVisible) {
            setBlockFaceColor(color, 0.82f);
            glVertex3f(x0, y0, z1);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x0, y1, z1);
        }
        if (backVisible) {
            setBlockFaceColor(color, 0.68f);
            glVertex3f(x1, y0, z0);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y1, z0);
            glVertex3f(x1, y1, z0);
        }
        if (leftVisible) {
            setBlockFaceColor(color, 0.76f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x0, y0, z1);
            glVertex3f(x0, y1, z1);
            glVertex3f(x0, y1, z0);
        }
        if (rightVisible) {
            setBlockFaceColor(color, 0.88f);
            glVertex3f(x1, y0, z1);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y1, z0);
            glVertex3f(x1, y1, z1);
        }
        if (topVisible) {
            setBlockFaceColor(color, 1.0f);
            glVertex3f(x0, y1, z1);
            glVertex3f(x1, y1, z1);
            glVertex3f(x1, y1, z0);
            glVertex3f(x0, y1, z0);
        }
        if (bottomVisible) {
            setBlockFaceColor(color, 0.55f);
            glVertex3f(x0, y0, z0);
            glVertex3f(x1, y0, z0);
            glVertex3f(x1, y0, z1);
            glVertex3f(x0, y0, z1);
        }
        glEnd();
    }

    static void setBlockFaceColor(float[] color, float brightness) {
        glColor4f(color[0] * brightness, color[1] * brightness,
                color[2] * brightness, color[3]);
    }

    static void drawGrid(int count, float spacing) {
        glColor3f(0.3f, 0.3f, 0.3f);
        glBegin(GL_LINES);
        for (int i = -count; i <= count; i++) {
            glVertex3f(i * spacing, 0.0f, -count * spacing);
            glVertex3f(i * spacing, 0.0f,  count * spacing);

            glVertex3f(-count * spacing, 0.0f, i * spacing);
            glVertex3f( count * spacing, 0.0f, i * spacing);
        }
        glEnd();
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

            float[] color = colorOf(inventory.hotbar[i]);
            glColor4f(color[0], color[1], color[2], color[3]);
            drawHudRect(x + 13, top + 11, 18, 18);
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
        setBlock(chunk, floorMod(x, CHUNK_SIZE), y, floorMod(z, CHUNK_SIZE), block);
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
        glFrustum(left, right, bottom, top, zNear, zFar);
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