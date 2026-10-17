package 게임제작;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    private double originalWorldClock;

    @BeforeEach
    void createEmptyWorld() {
        originalWorldClock = App.worldClockSeconds;
        App.world = new App.World();
        App.player = new App.Player();
        App.inventory = new App.Inventory();
        App.flightMode = false;
        App.lastSpacePressTime = Double.NEGATIVE_INFINITY;
        App.spaceKeyWasDown = false;
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
    void movementDirectionsStayAlignedWithCameraAndNormalizeDiagonals() {
        float[] forwardAtZeroYaw = App.movementDirection(0.0f, true, false, false, false);
        float[] rightAtZeroYaw = App.movementDirection(0.0f, false, false, false, true);
        float[] forwardAtQuarterTurn = App.movementDirection((float)Math.PI / 2.0f,
                true, false, false, false);
        float[] diagonal = App.movementDirection(0.0f, true, false, false, true);

        assertArrayEquals(new float[]{0.0f, -1.0f}, forwardAtZeroYaw, 0.0001f);
        assertArrayEquals(new float[]{1.0f, 0.0f}, rightAtZeroYaw, 0.0001f);
        assertArrayEquals(new float[]{1.0f, 0.0f}, forwardAtQuarterTurn, 0.0001f);
        float[] rightAtQuarterTurn = App.movementDirection((float)Math.PI / 2.0f,
                false, false, false, true);
        assertArrayEquals(new float[]{0.0f, 1.0f}, rightAtQuarterTurn, 0.0001f);
        assertEquals(1.0f, (float)Math.sqrt(diagonal[0] * diagonal[0]
                + diagonal[1] * diagonal[1]), 0.0001f);
    }

    @Test
    void doubleTappingSpaceTogglesFlight() {
        assertFalse(App.handleSpacePress(1.0));
        assertTrue(App.handleSpacePress(1.2));
        assertTrue(App.flightMode);
        assertFalse(App.handleSpacePress(2.0));
        assertTrue(App.handleSpacePress(2.2));
        assertFalse(App.flightMode);
    }

    @Test
    void flightControlsVerticalMovement() {
        assertEquals(App.FLIGHT_SPEED, App.flightVerticalVelocity(true, false));
        assertEquals(-App.FLIGHT_SPEED, App.flightVerticalVelocity(false, true));
        assertEquals(0.0f, App.flightVerticalVelocity(false, false));
        assertEquals(0.0f, App.flightVerticalVelocity(true, true));
    }

    @Test
    void waterFacesAreHiddenAgainstNeighboringWater() {
        App.Chunk chunk = App.world.chunks.get(App.key(0, 0));
        App.setBlock(chunk, 4, 3, 3, App.BlockType.WATER);
        App.setBlock(chunk, 5, 3, 3, App.BlockType.WATER);

        assertFalse(App.isWaterFaceVisible(5, 3, 3));
        assertTrue(App.isWaterFaceVisible(4, 4, 3));
    }

    @Test
    void sunlightAndTorchesGiveBlocksDifferentLightLevels() {
        App.Chunk chunk = App.world.chunks.get(App.key(0, 0));
        App.rebuildChunkLighting(chunk);
        assertEquals(15, chunk.sunlight[chunk.idx(3, 2, 0)] & 0xff);
        assertEquals(0, chunk.sunlight[chunk.idx(3, 1, 0)] & 0xff);

        App.setBlock(chunk, 4, 2, 0, App.BlockType.TORCH);
        App.rebuildChunkLighting(chunk);
        assertEquals(13, chunk.torchlight[chunk.idx(5, 2, 0)] & 0xff);
    }

    @Test
    void terrainContainsPlainsMountainsDesertAndCoast() {
        java.util.EnumSet<App.Biome> biomes = java.util.EnumSet.noneOf(App.Biome.class);
        for (int x = -3000; x <= 3000; x += 19) {
            for (int z = -3000; z <= 3000; z += 23) {
                biomes.add(App.biomeAt(x, z));
                if (biomes.size() == App.Biome.values().length) return;
            }
        }
        fail("Terrain generation should provide every biome, found: " + biomes);
    }

    @Test
    void dayCycleHasDawnDaySunsetAndNoDarkNight() {
        try {
            App.worldClockSeconds = App.DAY_LENGTH_SECONDS * 0.25;
            assertEquals("Day", App.skyPhaseName());
            assertEquals(1.0f, App.daylightLevel(), 0.001f);

            App.worldClockSeconds = App.DAY_LENGTH_SECONDS * 0.5;
            assertEquals("Sunset", App.skyPhaseName());

            App.worldClockSeconds = App.DAY_LENGTH_SECONDS * 0.75;
            assertEquals("Dawn", App.skyPhaseName());
            assertTrue(App.daylightLevel() >= 0.42f);
        } finally {
            App.worldClockSeconds = originalWorldClock;
        }
    }

    @Test
    void moonPhaseCyclesAcrossEightDays() {
        try {
            App.worldClockSeconds = App.DAY_LENGTH_SECONDS * 3.0;
            assertEquals(3, App.moonPhase());
            App.worldClockSeconds += App.DAY_LENGTH_SECONDS * 6.0;
            assertEquals(1, App.moonPhase());
        } finally {
            App.worldClockSeconds = originalWorldClock;
        }
    }

    @Test
    void moonPhaseChangesFromFullToNew() {
        assertEquals(1.0f, App.moonIllumination(0), 0.001f);
        assertEquals(0.0f, App.moonIllumination(4), 0.001f);
        assertEquals(0.5f, App.moonIllumination(2), 0.001f);
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
