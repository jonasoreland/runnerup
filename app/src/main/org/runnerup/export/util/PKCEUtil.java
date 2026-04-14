package org.runnerup.export.util;

import android.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

public class PKCEUtil {

    public static String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] code = new byte[64]; // Increased from 32 to 64 to ensure length is well within 43-128 range
        sr.nextBytes(code);
        return Base64.encodeToString(code, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public static String generateCodeChallenge(String verifier) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        byte[] bytes = verifier.getBytes("US-ASCII");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(bytes, 0, bytes.length);
        byte[] digest = md.digest();
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
