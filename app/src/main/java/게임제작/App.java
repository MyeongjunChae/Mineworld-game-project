package 게임제작;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

    static long window;
    static World world;
    static Player player;
    static Inventory inventory;

    static int fpsCounter = 0;
    static double fpsTimer = 0.0;

    public static void main(String[] args) {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }

        // fixed pipeline 쓰기 위한 2.1 컨텍스트
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(SCREEN_W, SCREEN_H, "Minecraft Clone - Single File", NULL, NULL);
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
                fpsTimer = 0.0;
                fpsCounter = 0;
            }

            update(dt);
            render();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    static void update(float dt) {
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
        if (glfwGetKey(window, GLFW_KEY_F3) == GLFW_PRESS) {
            glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }

        for (int i = 0; i < 9; i++) {
            int key = GLFW_KEY_1 + i;
            if (glfwGetKey(window, key) == GLFW_PRESS) {
                inventory.selected = i;
            }
        }

        double[] mx = new double[1];
        double[] my = new double[1];
        glfwGetCursorPos(window, mx, my);

        // 마우스 델타는 원래는 callback으로 처리하는 게 더 낫지만,
        // 지금은 단순화를 위해 커서 잠금 상태에서 매 프레임 누적만 사용.
        // 실제 구현에서는 이전 좌표를 저장하세요.
        float mouseDX = 0.0f;
        float mouseDY = 0.0f;

        if (glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED) {
            mouseDX = (float)mx[0] - SCREEN_W * 0.5f;
            mouseDY = (float)my[0] - SCREEN_H * 0.5f;
            glfwSetCursorPos(window, SCREEN_W * 0.5, SCREEN_H * 0.5);

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

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            raycastBreak(fx, fy, fz);
        }
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
            raycastPlace(fx, fy, fz);
        }

        if (glfwGetKey(window, GLFW_KEY_F5) == GLFW_PRESS) {
            // saveWorld();
        }
        if (glfwGetKey(window, GLFW_KEY_F9) == GLFW_PRESS) {
            // loadWorld();
        }
    }

    static void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        float aspect = (float) SCREEN_W / (float) SCREEN_H;
        setPerspective(70.0f, aspect, 0.1f, 1000.0f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glRotatef((float)Math.toDegrees(player.pitch), 1, 0, 0);
        glRotatef((float)Math.toDegrees(player.yaw), 0, 1, 0);
        glTranslatef(-player.x, -(player.y + PLAYER_EYE), -player.z);

        drawGrid(64, 1.0f);
        drawWorld();

        // 창 제목에 FPS 표시
        if (fpsCounter > 0) {
            glfwSetWindowTitle(window, "Minecraft Clone - Single File");
        }
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
        float[] c = colorOf(t);

        if (t == BlockType.WATER) {
            c[3] = 0.65f;
        }

        glColor4f(c[0], c[1], c[2], c[3]);

        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1;
        float y1 = y + 1;
        float z1 = z + 1;

        glBegin(GL_QUADS);

        // front
        glVertex3f(x0, y0, z1);
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x0, y1, z1);

        // back
        glVertex3f(x1, y0, z0);
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y1, z0);
        glVertex3f(x1, y1, z0);

        // left
        glVertex3f(x0, y0, z0);
        glVertex3f(x0, y0, z1);
        glVertex3f(x0, y1, z1);
        glVertex3f(x0, y1, z0);

        // right
        glVertex3f(x1, y0, z1);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y1, z0);
        glVertex3f(x1, y1, z1);

        // top
        glVertex3f(x0, y1, z1);
        glVertex3f(x1, y1, z1);
        glVertex3f(x1, y1, z0);
        glVertex3f(x0, y1, z0);

        // bottom
        glVertex3f(x0, y0, z0);
        glVertex3f(x1, y0, z0);
        glVertex3f(x1, y0, z1);
        glVertex3f(x0, y0, z1);

        glEnd();
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

    static void raycastBreak(float fx, float fy, float fz) {
        // 아주 단순한 예시
        // 실제로는 당신 C 코드의 RaycastBlocks 로직을 그대로 Java로 옮기면 됩니다.
    }

    static void raycastPlace(float fx, float fy, float fz) {
        // break와 동일한 방식으로 직전 공기 블록에 설치
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