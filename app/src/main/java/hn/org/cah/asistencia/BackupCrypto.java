package hn.org.cah.asistencia;

import org.json.JSONObject;
import org.json.JSONException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Copias JSON cifradas AES-256-GCM con contraseña elegida por administrador. */
public final class BackupCrypto {
    private BackupCrypto() {}
    private static final SecureRandom RNG=new SecureRandom();
    private static final int ROUNDS=210000;
    private static SecretKeySpec derive(char[] pass,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pass,salt,ROUNDS,256);
        try {return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}
        finally {spec.clearPassword();}
    }
    public static String encrypt(String data,String password) {
        try {
            byte[] salt=new byte[16],iv=new byte[12];RNG.nextBytes(salt);RNG.nextBytes(iv);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,derive(password.toCharArray(),salt),new GCMParameterSpec(128,iv));
            byte[] encrypted=c.doFinal(data.getBytes(StandardCharsets.UTF_8));
            JSONObject wrapper=new JSONObject();
            wrapper.put("format","CAH_BACKUP_CIFRADO_V1");wrapper.put("salt",Base64.getEncoder().encodeToString(salt));
            wrapper.put("iv",Base64.getEncoder().encodeToString(iv));wrapper.put("data",Base64.getEncoder().encodeToString(encrypted));
            return wrapper.toString(2);
        }catch(Exception e){throw new IllegalStateException("Error cifrando respaldo",e);}
    }
    public static JSONObject decrypt(String text,String password) {
        try {
            JSONObject wrapper=new JSONObject(text);
            if(!"CAH_BACKUP_CIFRADO_V1".equals(wrapper.optString("format")))
                throw new IllegalArgumentException("Archivo no compatible o no cifrado");
            byte[] salt=Base64.getDecoder().decode(wrapper.getString("salt"));
            byte[] iv=Base64.getDecoder().decode(wrapper.getString("iv"));
            byte[] data=Base64.getDecoder().decode(wrapper.getString("data"));
            if(salt.length!=16||iv.length!=12)throw new IllegalArgumentException("Parámetros de cifrado inválidos");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,derive(password.toCharArray(),salt),new GCMParameterSpec(128,iv));
            return new JSONObject(new String(c.doFinal(data),StandardCharsets.UTF_8));
        }catch(Exception e){throw new IllegalArgumentException("No se pudo abrir el respaldo: contraseña incorrecta o archivo alterado");}
    }
}