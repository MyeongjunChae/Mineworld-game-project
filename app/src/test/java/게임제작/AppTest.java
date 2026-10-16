package 게임제작;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @BeforeEach
    void createEmptyWorld() {
        App.world = new App.World();
        App.player = new App.Player();
        App.inventory = new App.Inventory();
        App.Chunk chunk = new App.Chunk(0, 0);
        chunk.generated = true;
        App.world.chunks.put(App.key(0, 0), chunk);
        App.setBlock(chunk, 3, 2, 0, App.BlockType.STONE);
    }

    @Test
    void raycastFindsBlockAndPreviousPlacementCell() {
        App.BlockHit hit = App.raycastFrom(0.5f, 2.5f, 0.5f, 1.0f, 0.0f, 0.0f);

        assertNotNull(hit);
        assertEquals(3, hit.x());
        assertEquals(2, hit.y());
        assertEquals(0, hit.z());
        assertEquals(2, hit.previousX());
        assertEquals(2, hit.previousY());
        assertEquals(0, hit.previousZ());
    }

    @Test
    void raycastStopsAtReachDistance() {
        App.setBlock(App.world.chunks.get(App.key(0, 0)), 3, 2, 0, App.BlockType.AIR);
        App.setBlock(App.world.chunks.get(App.key(0, 0)), 7, 2, 0, App.BlockType.STONE);

        assertNull(App.raycastFrom(0.5f, 2.5f, 0.5f, 1.0f, 0.0f, 0.0f));
    }

    @Test
    void breakingAndPlacingUpdatesWorldAndInventory() {
        App.setBlock(App.world.chunks.get(App.key(0, 0)), 5, 2, 0, App.BlockType.STONE);
        App.BlockHit hit = new App.BlockHit(5, 2, 0, 4, 2, 0);
        App.inventory.counts[App.BlockType.COBBLE.ordinal()] = 2;

        assertTrue(App.breakBlock(new App.BlockHit(3, 2, 0, 2, 2, 0)));
        assertEquals(App.BlockType.AIR, App.getBlock(3, 2, 0));
        assertEquals(257, App.inventory.counts[App.BlockType.STONE.ordinal()]);
        assertTrue(App.placeBlock(hit, App.BlockType.COBBLE));
        assertEquals(App.BlockType.COBBLE, App.getBlock(4, 2, 0));
        assertEquals(1, App.inventory.counts[App.BlockType.COBBLE.ordinal()]);
    }

    @Test
    void placementCannotIntersectPlayer() {
        App.player.x = 2.5f;
        App.player.y = 2.0f;
        App.player.z = 0.5f;
        App.BlockHit hit = new App.BlockHit(3, 2, 0, 2, 2, 0);

        assertFalse(App.placeBlock(hit, App.BlockType.DIRT));
        assertEquals(App.BlockType.AIR, App.getBlock(2, 2, 0));
    }

    @Test
    void chunkCullingRejectsBehindAndBeyondViewDistance() {
        App.player.x = 8.0f;
        App.player.y = 20.0f;
        App.player.z = 8.0f;
        App.player.yaw = 0.0f;
        App.player.pitch = 0.0f;
        App.Chunk ahead = new App.Chunk(0, 1);
        App.Chunk behind = new App.Chunk(0, -1);
        App.Chunk tooFar = new App.Chunk(5, 0);

        assertTrue(App.isChunkInView(ahead, 16.0f / 9.0f));
        assertFalse(App.isChunkInView(behind, 16.0f / 9.0f));
        assertFalse(App.isChunkInView(tooFar, 16.0f / 9.0f));
    }

    @Test
    void editingChunkInvalidatesItsCachedMesh() {
        App.Chunk chunk = App.world.chunks.get(App.key(0, 0));
        chunk.meshDirty = false;

        App.setWorldBlock(3, 2, 0, App.BlockType.DIRT);

        assertTrue(chunk.meshDirty);
    }

    @Test
    void savedWorldRestoresBlocksPlayerAndInventory(@TempDir Path directory) throws IOException {
        App.player.x = 12.5f;
        App.player.yaw = 1.25f;
        App.inventory.selected = 4;
        App.inventory.counts[App.BlockType.LEAF.ordinal()] = 7;
        Path saveFile = directory.resolve("world.dat");
        App.saveWorld(saveFile);

        App.world = new App.World();
        App.player.x = 100.0f;
        App.inventory = new App.Inventory();
        App.loadWorld(saveFile);

        assertEquals(App.BlockType.STONE, App.getBlock(3, 2, 0));
        assertEquals(12.5f, App.player.x);
        assertEquals(1.25f, App.player.yaw);
        assertEquals(4, App.inventory.selected);
        assertEquals(7, App.inventory.counts[App.BlockType.LEAF.ordinal()]);
    }
}
