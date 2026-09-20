package com.landrop.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    // For a zero-config local mesh project, deriving a 128-bit AES key from a static passphrase is standard.
    // In a production app, you would use a Diffie-Hellman key exchange per session.
    private static final String MESH_PASSPHRASE = "LANDrop-Local-Mesh-Secret-2026"; 
    
    private static SecretKeySpec getMeshKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(MESH_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
        // Use first 16 bytes for AES-128
        byte[] aesKey = new byte[16];
        System.arraycopy(keyBytes, 0, aesKey, 0, 16);
        return new SecretKeySpec(aesKey, ALGORITHM);
    }

    public static OutputStream wrapEncryptedOutput(OutputStream out) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, getMeshKey());
        return new CipherOutputStream(out, cipher);
    }

    public static InputStream wrapDecryptedInput(InputStream in) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, getMeshKey());
        return new CipherInputStream(in, cipher);
    }
}