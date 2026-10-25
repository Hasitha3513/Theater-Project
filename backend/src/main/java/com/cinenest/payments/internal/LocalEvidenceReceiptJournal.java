package com.cinenest.payments.internal;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import com.nimbusds.jose.util.JSONObjectUtils;

/** Single-process, local-filesystem journal. No shared/network filesystem or production use. */
final class LocalEvidenceReceiptJournal implements AutoCloseable {
    record Entry(UUID id,String digest,int size,Instant created,String version,boolean deleted) {}
    private final Path root;
    private final UUID namespace;
    private final SecretKeySpec key;
    private final FileChannel owner;
    private final FileLock lock;

    LocalEvidenceReceiptJournal(Path supplied,UUID namespace,byte[] encryptionKey) {
        FileChannel channel=null;FileLock acquired=null;
        try {
            if(!supplied.isAbsolute()||encryptionKey.length!=32)throw new IllegalArgumentException();
            root=supplied.normalize();this.namespace=Objects.requireNonNull(namespace);key=new SecretKeySpec(encryptionKey.clone(),"AES");
            Path repo=Path.of("").toAbsolutePath();while(repo.getParent()!=null&&!Files.exists(repo.resolve("AGENTS.md")))repo=repo.getParent();
            if(root.startsWith(repo))throw new IllegalArgumentException();
            for(Path p=root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IllegalArgumentException();
            Files.createDirectories(root,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if(!Files.getPosixFilePermissions(root).equals(PosixFilePermissions.fromString("rwx------")))throw new IllegalArgumentException();
            channel=FileChannel.open(root.resolve("owner.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            acquired=channel.tryLock();if(acquired==null)throw new IllegalStateException();
            owner=channel;lock=acquired;
            // Validate committed records before admitting operations. Unknown files fail closed.
            entries();
        } catch(Exception failure) {
            try{if(acquired!=null)acquired.release();if(channel!=null)channel.close();}catch(Exception ignored){}
            throw unavailable();
        }
    }
    static BankFailure unavailable(){return new BankFailure(503,"Local synthetic evidence unavailable");}
    private void active(){if(!lock.isValid()||!owner.isOpen())throw unavailable();}
    private Path path(UUID id,String phase){return root.resolve(id+"."+phase);}
    private byte[] aad(UUID id,String phase){return (namespace+":"+id+":"+phase).getBytes(java.nio.charset.StandardCharsets.US_ASCII);}
    private Map<String,Object> read(UUID id,String phase)throws Exception {
        Path p=path(id,phase);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>4096)throw unavailable();
        byte[] data=Files.readAllBytes(p);if(data.length<29)throw unavailable();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(data,12)));c.updateAAD(aad(id,phase));
        return JSONObjectUtils.parse(new String(c.doFinal(Arrays.copyOfRange(data,12,data.length)),java.nio.charset.StandardCharsets.UTF_8));
    }
    private void write(UUID id,String phase,Map<String,Object> value)throws Exception {
        if(Files.exists(path(id,phase),LinkOption.NOFOLLOW_LINKS))throw unavailable();
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(aad(id,phase));
        byte[] encrypted=c.doFinal(JSONObjectUtils.toJSONString(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path temp=Files.createTempFile(root,"pending-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            try(var out=FileChannel.open(temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){ByteBuffer b=ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted);b.flip();while(b.hasRemaining())out.write(b);out.force(true);}
            Files.move(temp,path(id,phase),StandardCopyOption.ATOMIC_MOVE);
            try(var dir=FileChannel.open(root,StandardOpenOption.READ)){dir.force(true);}
        } finally {Files.deleteIfExists(temp);}
    }
    synchronized Entry find(UUID id) {
        active();try {
            // A retry after an uncertain rename/force must not use an unforced publication.
            try(var dir=FileChannel.open(root,StandardOpenOption.READ)){dir.force(true);}
            var intent=read(id,"intent");if(intent==null){if(Files.exists(path(id,"bound"))||Files.exists(path(id,"deleted")))throw unavailable();return null;}
            if(!intent.keySet().equals(Set.of("digest","size","created")))throw unavailable();
            String digest=Objects.toString(intent.get("digest"));int size=((Number)intent.get("size")).intValue();Instant created=Instant.parse(Objects.toString(intent.get("created")));
            if(!digest.matches("[a-f0-9]{64}")||size<1||size>BankPolicy.MAX_BYTES)throw unavailable();
            var binding=read(id,"bound");String version=null;if(binding!=null){if(!binding.keySet().equals(Set.of("version")))throw unavailable();version=Objects.toString(binding.get("version"));if(!version.matches("[A-Za-z0-9._~+/=-]{1,512}")||version.equals("null"))throw unavailable();}
            var deleted=read(id,"deleted");if(deleted!=null&&!deleted.equals(Map.of("deleted",true)))throw unavailable();
            return new Entry(id,digest,size,created,version,deleted!=null);
        }catch(Exception failure){throw unavailable();}
    }
    synchronized Entry intent(UUID id,byte[] bytes) {
        active();try {Entry old=find(id);String digest=BankPolicy.hash(bytes);
            if(bytes.length<1||bytes.length>BankPolicy.MAX_BYTES)throw new BankFailure(413,"Synthetic evidence size rejected");
            if(old!=null){if(old.deleted()||old.size()!=bytes.length||!old.digest().equals(digest))throw new BankFailure(409,"Evidence command changed");return old;}
            if(entries().size()>=1000)throw unavailable();
            write(id,"intent",Map.of("digest",digest,"size",bytes.length,"created",Instant.now().toString()));return find(id);
        }catch(BankFailure failure){throw failure;}catch(Exception failure){throw unavailable();}
    }
    synchronized void bind(UUID id,String version) {
        try {Entry e=find(id);if(e==null||e.deleted())throw unavailable();if(e.version()!=null){if(!e.version().equals(version))throw unavailable();return;}write(id,"bound",Map.of("version",version));find(id);}catch(Exception failure){throw unavailable();}
    }
    synchronized void deleted(UUID id) {
        try{Entry e=find(id);if(e!=null&&!e.deleted())write(id,"deleted",Map.of("deleted",true));}catch(Exception failure){throw unavailable();}
    }
    synchronized List<Entry> entries() {
        active();try(var paths=Files.list(root)) {
            Set<UUID> ids=new HashSet<>();for(Path p:paths.toList()){String n=p.getFileName().toString();if(n.equals("owner.lock"))continue;
                if(n.matches("pending-[0-9]+\\.tmp")){if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw unavailable();continue;}
                if(!n.matches("[a-f0-9-]{36}\\.(intent|bound|deleted)"))throw unavailable();ids.add(UUID.fromString(n.substring(0,36)));}
            if(ids.size()>1000)throw unavailable();List<Entry> result=new ArrayList<>();for(UUID id:ids)result.add(Objects.requireNonNull(find(id)));return List.copyOf(result);
        }catch(Exception failure){throw unavailable();}
    }
    UUID namespace(){return namespace;}
    synchronized byte[] encryptObject(UUID id,byte[] plain){
        active();try{byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(aad(id,"object"));byte[] encrypted=c.doFinal(plain);return ByteBuffer.allocate(12+encrypted.length).put(nonce).put(encrypted).array();}catch(Exception failure){throw unavailable();}
    }
    synchronized byte[] decryptObject(UUID id,byte[] data){
        active();try{if(data.length<29||data.length>BankPolicy.MAX_BYTES+28)throw unavailable();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(data,12)));c.updateAAD(aad(id,"object"));return c.doFinal(Arrays.copyOfRange(data,12,data.length));}catch(Exception failure){throw unavailable();}
    }
    public synchronized void close(){try{lock.release();owner.close();}catch(Exception failure){throw unavailable();}}
}
