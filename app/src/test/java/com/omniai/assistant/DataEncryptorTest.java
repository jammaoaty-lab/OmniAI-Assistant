package com.omniai.assistant;

import android.content.Context;

import com.omniai.assistant.security.DataEncryptor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DataEncryptorTest {

    private DataEncryptor encryptor;
    private File testDir;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        encryptor = new DataEncryptor(context);
        testDir = new File(System.getProperty("java.io.tmpdir"), "encryptor_test_" + System.currentTimeMillis());
        testDir.mkdirs();
    }

    @After
    public void tearDown() {
        if (testDir != null && testDir.exists()) {
            File[] files = testDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            testDir.delete();
        }
    }

    @Test
    public void testEncryptDecryptString() {
        String original = "Hello, OmniAI Assistant!";
        String encrypted = encryptor.encryptString(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);
        String decrypted = encryptor.decryptString(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    public void testEncryptNullInput() {
        assertNull(encryptor.encryptString(null));
    }

    @Test
    public void testEncryptEmptyInput() {
        assertEquals("", encryptor.encryptString(""));
    }

    @Test
    public void testDecryptInvalidCipherText() {
        String invalid = "not_valid_hex";
        String result = encryptor.decryptString(invalid);
        assertEquals(invalid, result);
    }

    @Test
    public void testGenerateMasterKey() {
        String key = encryptor.generateMasterKey();
        assertNotNull(key);
        assertEquals(64, key.length());
        for (char c : key.toCharArray()) {
            assertTrue("Master key should be hex string", (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'));
        }
    }

    @Test
    public void testEncryptDecryptFile() throws Exception {
        File inputFile = new File(testDir, "test_input.txt");
        File encryptedFile = new File(testDir, "test_encrypted.bin");
        File decryptedFile = new File(testDir, "test_decrypted.txt");

        String testData = "This is test file content for OmniAI encryption!";
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(inputFile))) {
            bos.write(testData.getBytes("UTF-8"));
            bos.flush();
        }

        assertTrue("File encryption should succeed", encryptor.encryptFile(inputFile.getAbsolutePath(), encryptedFile.getAbsolutePath()));
        assertTrue("Encrypted file should exist", encryptedFile.exists());
        assertNotEquals("Encrypted file should differ from original", inputFile.length(), encryptedFile.length());

        assertTrue("File decryption should succeed", encryptor.decryptFile(encryptedFile.getAbsolutePath(), decryptedFile.getAbsolutePath()));
        assertTrue("Decrypted file should exist", decryptedFile.exists());

        byte[] decryptedData = new byte[(int) decryptedFile.length()];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(decryptedFile))) {
            int offset = 0;
            while (offset < decryptedData.length) {
                int read = bis.read(decryptedData, offset, decryptedData.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        assertEquals("Decrypted content should match original", testData, new String(decryptedData, "UTF-8"));
    }
}
