package com.cinenest.verification.internal;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** Local encrypted test store only. Object names never originate in client filenames. */
@Component
class EncryptedEvidenceStore implements PrivateEvidenceStore {
    static final String OBJECT_PREFIX="cinenest-verification-";
    private final Environment env;
    EncryptedEvidenceStore(Environment env){this.env=env;}
    private Path root(){
        try {
            Path supplied=Path.of(env.getRequiredProperty("cinenest.verification.storage-root"));if(!supplied.isAbsolute())throw new IllegalArgumentException();Path p=supplied.normalize();
            Path repo=Path.of("").toAbsolutePath().normalize();
            while(repo.getParent()!=null&&!Files.exists(repo.resolve("AGENTS.md")))repo=repo.getParent();
            if(p.startsWith(repo)||!p.isAbsolute())throw new IllegalArgumentException();
            for(Path part=p;part!=null;part=part.getParent())if(Files.isSymbolicLink(part))throw new IllegalArgumentException();
            Files.createDirectories(p,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if(!Files.getPosixFilePermissions(p).equals(PosixFilePermissions.fromString("rwx------")))throw new IllegalArgumentException();
            return p;
        }catch(Exception invalid){throw new VerificationFailure(503,"Private synthetic storage unavailable");}
    }
    private SecretKeySpec key(){try{byte[] key=Base64.getDecoder().decode(env.getRequiredProperty("cinenest.verification.encryption-key"));if(key.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(key,"AES");}catch(Exception invalid){throw new VerificationFailure(503,"Private synthetic storage unavailable");}}
    public synchronized void put(UUID id,byte[] plain){
        Path temp=null;
        try {
            Path root=root();byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));cipher.updateAAD(id.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            byte[] encrypted=cipher.doFinal(plain);temp=Files.createTempFile(root,OBJECT_PREFIX+"pending-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(temp,ByteBuffer.allocate(nonce.length+encrypted.length).put(nonce).put(encrypted).array());
            Files.move(temp,root.resolve(OBJECT_PREFIX+id+".enc"),StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception invalid){throw new VerificationFailure(503,"Private synthetic storage unavailable");}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){/* Orphan reconciliation owns interrupted temporary files. */}}
    }
    public byte[] get(UUID id){try{Path path=root().resolve(OBJECT_PREFIX+id+".enc");if(Files.isSymbolicLink(path)||Files.size(path)>VerificationPolicy.MAX_BYTES+64)throw new IllegalArgumentException();byte[] bytes=Files.readAllBytes(path);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));cipher.updateAAD(id.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));return cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length));}catch(Exception invalid){throw new VerificationFailure(503,"Private synthetic storage unavailable");}}
    public void delete(UUID id){try{Files.deleteIfExists(root().resolve(OBJECT_PREFIX+id+".enc"));}catch(Exception invalid){throw new VerificationFailure(503,"Synthetic cleanup pending");}}
    public Set<UUID> olderThan(Instant cutoff){try(var paths=Files.list(root())){Set<UUID> result=new HashSet<>();for(Path p:paths.toList()){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))continue;try{String name=p.getFileName().toString();if(!name.startsWith(OBJECT_PREFIX)||!name.endsWith(".enc"))throw new IllegalArgumentException();result.add(UUID.fromString(name.substring(OBJECT_PREFIX.length(),name.length()-4)));}catch(IllegalArgumentException temp){if(p.getFileName().toString().matches(OBJECT_PREFIX+"pending-[0-9]+\\.tmp"))Files.deleteIfExists(p);}}return result;}catch(Exception invalid){throw new VerificationFailure(503,"Synthetic reconciliation unavailable");}}
}
