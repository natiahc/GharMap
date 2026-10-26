package com.paisapilot.app;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable v1 backup: PPBK + version byte + salt(16) + nonce(12) + ciphertext/tag. */
final class Backup {
    static final int LIMIT=10_000_000, ITERATIONS=210_000;
    private static final byte[] MAGIC={'P','P','B','K',1};
    static byte[] encrypt(JSONObject snapshot,char[] password) throws GeneralSecurityException {
        validatePassword(password);
        byte[] plaintext=snapshot.toString().getBytes(StandardCharsets.UTF_8);
        if(plaintext.length>LIMIT-49)throw new IllegalArgumentException("Backup exceeds 10 MB");
        byte[] header=new byte[33];System.arraycopy(MAGIC,0,header,0,5);
        byte[] random=new byte[28];new SecureRandom().nextBytes(random);System.arraycopy(random,0,header,5,28);
        Cipher cipher=cipher(Cipher.ENCRYPT_MODE,password,header);
        try {byte[] encrypted=cipher.doFinal(plaintext),result=Arrays.copyOf(header,header.length+encrypted.length);
            System.arraycopy(encrypted,0,result,header.length,encrypted.length);return result;
        }finally{Arrays.fill(plaintext,(byte)0);}
    }
    static JSONObject decrypt(byte[] bytes,char[] password) throws Exception {
        validatePassword(password);
        if(bytes.length<49||bytes.length>LIMIT||!Arrays.equals(MAGIC,Arrays.copyOf(bytes,5)))
            throw new IllegalArgumentException("Unsupported or invalid PaisaPilot backup");
        byte[] header=Arrays.copyOf(bytes,33),plaintext;
        try{plaintext=cipher(Cipher.DECRYPT_MODE,password,header).doFinal(bytes,33,bytes.length-33);}
        catch(GeneralSecurityException e){throw new IllegalArgumentException("Incorrect passphrase or damaged backup",e);}
        try{return new JSONObject(new String(plaintext,StandardCharsets.UTF_8));}
        finally{Arrays.fill(plaintext,(byte)0);}
    }
    private static Cipher cipher(int mode,char[] password,byte[] header) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,Arrays.copyOfRange(header,5,21),ITERATIONS,256);
        byte[] key;
        try{key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(header,21,33)));cipher.updateAAD(header);return cipher;}
        finally{Arrays.fill(key,(byte)0);}
    }
    private static void validatePassword(char[] password){
        if(password==null||password.length<12||password.length>128)
            throw new IllegalArgumentException("Use a passphrase of 12 to 128 characters");
        for(char c:password)if(c<32||c>126)throw new IllegalArgumentException("Use printable English characters for portable backups");
    }
}
