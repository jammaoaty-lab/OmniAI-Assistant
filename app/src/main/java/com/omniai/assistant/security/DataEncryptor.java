package com.omniai.assistant.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DataEncryptor {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String PREFS_NAME = "omnia_encrypted_prefs";
    private static final String KEY_MASTER = "master_key";

    private String masterKey;
    private Context context;
    private SharedPreferences encryptedPrefs;

    public DataEncryptor(Context context) {
        this.context = context.getApplicationContext();
        this.masterKey = null;
        initEncryptedPrefs();
    }

    private void initEncryptedPrefs() {
        try {
            MasterKey masterKeyObj = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKeyObj,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            masterKey = encryptedPrefs.getString(KEY_MASTER, null);
            if (masterKey == null) {
                masterKey = generateMasterKey();
                encryptedPrefs.edit().putString(KEY_MASTER, masterKey).apply();
            }
        } catch (Exception e) {
            masterKey = generateMasterKey();
        }
    }

    public String encryptString(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        if (masterKey == null) {
            throw new SecurityException("Encryption key not initialized");
        }
        try {
            byte[] keyBytes = hexToBytes(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return bytesToHex(combined);
        } catch (Exception e) {
            throw new SecurityException("Encryption failed", e);
        }
    }

    public String decryptString(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (masterKey == null) {
            throw new SecurityException("Encryption key not initialized");
        }
        try {
            byte[] combined = hexToBytes(cipherText);
            if (combined.length < GCM_IV_LENGTH) {
                throw new SecurityException("Invalid cipher text length");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            byte[] keyBytes = hexToBytes(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, "UTF-8");
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Decryption failed", e);
        }
    }

    public boolean encryptFile(String inputPath, String outputPath) {
        try {
            byte[] keyBytes = hexToBytes(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] iv = cipher.getIV();

            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                return false;
            }

            byte[] fileData = readFile(inputPath);
            byte[] encrypted = cipher.doFinal(fileData);

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {
                bos.write(iv.length);
                bos.write(iv);
                bos.write(encrypted);
                bos.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean decryptFile(String inputPath, String outputPath) {
        try {
            byte[] keyBytes = hexToBytes(masterKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            File inputFile = new File(inputPath);
            if (!inputFile.exists()) {
                return false;
            }

            byte[] fileData = readFile(inputPath);
            if (fileData.length < GCM_IV_LENGTH + 1) {
                return false;
            }

            int ivLength = fileData[0] & 0xFF;
            if (ivLength != GCM_IV_LENGTH || fileData.length < 1 + ivLength) {
                return false;
            }

            byte[] iv = new byte[ivLength];
            byte[] encrypted = new byte[fileData.length - 1 - ivLength];
            System.arraycopy(fileData, 1, iv, 0, ivLength);
            System.arraycopy(fileData, 1 + ivLength, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decrypted = cipher.doFinal(encrypted);

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputPath))) {
                bos.write(decrypted);
                bos.flush();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateMasterKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new SecureRandom());
            SecretKey secretKey = keyGen.generateKey();
            return bytesToHex(secretKey.getEncoded());
        } catch (Exception e) {
            SecureRandom random = new SecureRandom();
            byte[] key = new byte[32];
            random.nextBytes(key);
            return bytesToHex(key);
        }
    }

    private String getMasterKey() {
        return masterKey;
    }

    private byte[] readFile(String path) throws Exception {
        File file = new File(path);
        byte[] data = new byte[(int) file.length()];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            int offset = 0;
            while (offset < data.length) {
                int read = bis.read(data, offset, data.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
