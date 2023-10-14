package com.codesimcoe.mapexplorer;

import com.codesimcoe.mapexplorer.save.SaveData;
import com.codesimcoe.mapexplorer.save.SaveUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Test class for {@link SaveUtils}.
 */
public class SaveUtilsTest {

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
    private static final Random RANDOM = new Random();

    @Test
    void testRandom() {

        byte[] randomBytes = new byte[1024];
        RANDOM.nextBytes(randomBytes);

        this.test(randomBytes);
    }

    @Test
    void testZeros() {

        byte[] zeros = new byte[1024];
        Arrays.fill(zeros, (byte) 0);

        byte[] compressed = this.test(zeros);
        Assertions.assertEquals(29, compressed.length);
    }

    @Test
    void testSaveLoad() {

        String filename = TMP_DIR + "/map-explorer-savetest.me";
        try {
            Files.deleteIfExists(Path.of(filename));

            int width = RANDOM.nextInt(800, 2048);
            int height = RANDOM.nextInt(800, 2048);
            byte[] pixels = new byte[width * height * 2 * 4];
            RANDOM.nextBytes(pixels);
            SaveData data = new SaveData(width, height, pixels);

            SaveUtils.saveToFile(data, filename);
            SaveData loaded = SaveUtils.loadFromFile(filename);

            Assertions.assertEquals(width, loaded.width());
            Assertions.assertEquals(height, loaded.height());
            Assertions.assertArrayEquals(pixels, loaded.pixels());

        } catch (IOException | ClassNotFoundException e) {
            Assertions.fail(e);
        }
    }

    private byte[] test(byte[] input) {
        try {
            byte[] compressed = SaveUtils.compress(input);
            byte[] decompressed = SaveUtils.decompress(compressed);
            Assertions.assertArrayEquals(input, decompressed);

            return compressed;
        } catch (IOException e) {
            Assertions.fail(e);
            return new byte[] {};
        }
    }
}
