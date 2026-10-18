package com.cinenest.access.internal;
import java.util.*;import java.security.*;import javax.crypto.*;import javax.crypto.spec.*;
import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;
@Component
class AccessPolicy {
 private final Environment env;private final SecureRandom random=new SecureRandom();AccessPolicy(Environment env){this.env=env;}
 void configured(){try{if(!env.getProperty("cinenest.access.enabled",Boolean.class,false)||!"SYNTHETIC_BANK_ONLY".equals(env.getProperty("cinenest.access.mode")))throw new IllegalArgumentException();for(String p:List.of("attempt-limit","retry-limit","rate-count","rate-seconds","disclosure-limit","retention-seconds"))positive(p);key(version());}catch(RuntimeException e){throw new AccessFailure(503);}}
 int positive(String name){try{int n=Integer.parseInt(env.getRequiredProperty("cinenest.access."+name));if(n<=0)throw new IllegalArgumentException();return n;}catch(RuntimeException e){throw new AccessFailure(503);}}
 String version(){String v=env.getProperty("cinenest.access.key-version","");if(!v.matches("[A-Za-z0-9-]{1,40}"))throw new AccessFailure(503);return v;}
 private byte[] key(String version){try{byte[] k=Base64.getDecoder().decode(env.getRequiredProperty("cinenest.access.keys."+version));if(k.length!=32)throw new IllegalArgumentException();return k;}catch(RuntimeException e){throw new AccessFailure(503);}}
 String token(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static byte[] hash(String s){try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Digest unavailable");}}
 byte[] protect(String value,String version,UUID id){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(version),"AES"),new GCMParameterSpec(128,iv));c.updateAAD(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] body=c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));return java.nio.ByteBuffer.allocate(iv.length+body.length).put(iv).put(body).array();}catch(Exception e){throw new AccessFailure(503);}}
 String reveal(byte[] value,String version,UUID id){try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(version),"AES"),new GCMParameterSpec(128,Arrays.copyOf(value,12)));c.updateAAD(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return new String(c.doFinal(Arrays.copyOfRange(value,12,value.length)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new AccessFailure(503);}}
}
