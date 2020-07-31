/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.util;

import android.annotation.SuppressLint;
import android.util.Base64;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class Encryption {
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * Computes RFC 2104-compliant HMAC signature.
     * 
     * @param data The data to be signed.
     * @param key The signing key.
     * @return The Base64-encoded RFC 2104-compliant HMAC signature.
     * @throws java.security.SignatureException when signature generation fails
     */
    public static String calculateRFC2104HMAC(final String data, final String key)
            throws java.security.SignatureException {
        try {

            // get an hmac_sha1 key from the raw key bytes
            final SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(),
                    HMAC_SHA1_ALGORITHM);

            // get an hmac_sha1 Mac instance and initialize with the signing key
            final Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);

            // compute the hmac on input data bytes
            final byte[] rawHmac = mac.doFinal(data.getBytes());

            // base64-encode the hmac
            return android.util.Base64.encodeToString(rawHmac, Base64.NO_WRAP);
        } catch (final Exception e) {
            throw new SignatureException("Failed to generate HMAC : "
                    + e.getMessage());
        }
    }

    private static final String CRYPT_ALGORITHM = "PBEWithMD5AndDES";

    /**
     * @param in
     * @param out
     * @param key
     * @throws Exception
     */
    @SuppressLint("TrulyRandom")
    private static void encrypt(final InputStream in, final OutputStream out, final String key)
            throws Exception {
        final PBEKeySpec keySpec = new PBEKeySpec(key.toCharArray());
        final SecretKeyFactory keyFactory = SecretKeyFactory
                .getInstance(CRYPT_ALGORITHM);
        final SecretKey passwordKey = keyFactory.generateSecret(keySpec);

        // PBE = hashing + symmetric encryption. A 64 bit random
        // number (the salt) is added to the password and hashed
        // using a Message Digest Algorithm (MD5 in this example.).
        // The number of times the password is hashed is determined
        // by the iteration count. Adding a random number and
        // hashing multiple times enlarges the key space.
        final byte[] salt = new byte[8];
        final Random rnd = new Random();
        rnd.nextBytes(salt);

        // Create the parameter spec for this salt and iteration count
        final int iterations = 100;
        final PBEParameterSpec parameterSpec = new PBEParameterSpec(salt, iterations);

        // Create the cipher and initialize it for encryption.
        final Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, passwordKey, parameterSpec);

        // Need to write the salt to the (encrypted) file. The
        // salt is needed when reconstructing the key for decryption.
        out.write(salt);

        // Read the file and encrypt its bytes.
        final byte[] input = new byte[64];
        int bytesRead;
        while ((bytesRead = in.read(input)) != -1) {
            final byte[] output = cipher.update(input, 0, bytesRead);
            if (output != null)
                out.write(output);
        }

        final byte[] output = cipher.doFinal();
        if (output != null)
            out.write(output);

        in.close();
        out.flush();
        out.close();
    }

    public static void decrypt(final InputStream in, final OutputStream out, final String key)
            throws Exception {
        final PBEKeySpec keySpec = new PBEKeySpec(key.toCharArray());
        final SecretKeyFactory keyFactory = SecretKeyFactory
                .getInstance(CRYPT_ALGORITHM);
        final SecretKey passwordKey = keyFactory.generateSecret(keySpec);

        // Read in the previously stored salt and set the iteration count.
        final byte[] salt = new byte[8];
        //noinspection ResultOfMethodCallIgnored
        in.read(salt);
        final int iterations = 100;
        final PBEParameterSpec parameterSpec = new PBEParameterSpec(salt, iterations);

        // Create the cipher and initialize it for decryption.
        final Cipher cipher = Cipher.getInstance(CRYPT_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, passwordKey, parameterSpec);

        final byte[] input = new byte[64];
        int bytesRead;
        while ((bytesRead = in.read(input)) != -1) {
            final byte[] output = cipher.update(input, 0, bytesRead);
            if (output != null)
                out.write(output);
        }

        final byte[] output = cipher.doFinal();
        if (output != null)
            out.write(output);

        in.close();
        out.flush();
        out.close();
    }

    public static void main(final String[] args) {
        if (args.length == 2) {
            final String name = args[0];
            final String key = args[1];
            try {
                final FileInputStream in = new FileInputStream(name);
                final FileOutputStream out = new FileOutputStream(name + ".des");
                encrypt(in, out, key);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static byte[] SHA1(String text) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
        //noinspection CharsetObjectCanBeUsed
        return digest.digest(text.getBytes("UTF-8"));
    }

    public static byte[] md5(String text) throws NoSuchAlgorithmException,
            UnsupportedEncodingException {
        final MessageDigest digest = MessageDigest.getInstance("MD5");
        //noinspection CharsetObjectCanBeUsed
        return digest.digest(text.getBytes("UTF-8"));
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }
}
