package com.landrop.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class CryptoUtil {

    private static final String ALGORITHM = "AES/CTR/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String MESH_PASSPHRASE = "LANDrop-Local-Mesh-Secret-2026"; 
    
    private static SecretKeySpec getMeshKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(MESH_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
        byte[] aesKey = new byte[16];
        System.arraycopy(keyBytes, 0, aesKey, 0, 16);
        return new SecretKeySpec(aesKey, KEY_ALGORITHM);
    }

    private static IvParameterSpec getMeshIV() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(MESH_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
        byte[] ivBytes = new byte[16];
        // Use the last 16 bytes of the SHA-256 hash as the 128-bit CTR IV
        System.arraycopy(keyBytes, 16, ivBytes, 0, 16);
        return new IvParameterSpec(ivBytes);
    }

    public static OutputStream wrapEncryptedOutput(OutputStream out) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, getMeshKey(), getMeshIV());
        return new CipherOutputStream(out, cipher);
    }

    public static InputStream wrapDecryptedInput(InputStream in) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, getMeshKey(), getMeshIV());
        return new CipherInputStream(in, cipher);
    }
}