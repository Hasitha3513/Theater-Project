package com.cinenest.payments.internal;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Bounded local Unix-socket protocol only; no process execution or Docker privileges. */
final class ClamAvSocketClient {
    record Scan(String engine,String signatureVersion,Instant signatureTime) {}
    private final Path socket;private final int seconds;
    ClamAvSocketClient(Path socket,int seconds){if(!socket.isAbsolute()||seconds<1||seconds>60)throw LocalEvidenceReceiptJournal.unavailable();this.socket=socket.normalize();this.seconds=seconds;}
    private String exchange(byte[] bytes,long deadline)throws Exception {
        if(Files.isSymbolicLink(socket)||((int)Files.getAttribute(socket,"unix:mode",LinkOption.NOFOLLOW_LINKS)&0170000)!=0140000)throw LocalEvidenceReceiptJournal.unavailable();
        for(Path p=socket.getParent();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw LocalEvidenceReceiptJournal.unavailable();
        if(Files.getPosixFilePermissions(socket.getParent()).stream().anyMatch(p->p.name().startsWith("OTHERS_")))throw LocalEvidenceReceiptJournal.unavailable();
        var executor=Executors.newVirtualThreadPerTaskExecutor();var channel=SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            var result=executor.submit(()->{
                channel.connect(UnixDomainSocketAddress.of(socket));var out=new DataOutputStream(Channels.newOutputStream(channel));
                out.write((bytes==null?"zVERSION\0":"zINSTREAM\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if(bytes!=null){for(int offset=0;offset<bytes.length;offset+=65536){int n=Math.min(65536,bytes.length-offset);out.writeInt(n);out.write(bytes,offset,n);}out.writeInt(0);}out.flush();
                var in=Channels.newInputStream(channel);var response=new ByteArrayOutputStream();for(int i=0;i<2048;i++){int b=in.read();if(b==0)return response.toString(java.nio.charset.StandardCharsets.US_ASCII);if(b<0)throw new IOException();response.write(b);}throw new IOException();
            });
            return result.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
        } finally {channel.close();executor.shutdownNow();}
    }
    private Scan metadata(String text) {
        try {String[] fields=text.split("/",3);if(fields.length!=3||!fields[0].matches("ClamAV [0-9]+\\.[0-9]+\\.[0-9]+")||!fields[1].matches("[0-9]+"))throw new IllegalArgumentException();
            Instant time=LocalDateTime.parse(fields[2],DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH)).toInstant(ZoneOffset.UTC);
            Duration age=Duration.between(time,Instant.now());if(age.isNegative()||age.compareTo(Duration.ofHours(24))>0)throw new IllegalArgumentException();return new Scan(fields[0],fields[1],time);
        }catch(Exception failure){throw LocalEvidenceReceiptJournal.unavailable();}
    }
    Scan scan(byte[] bytes) {
        if(bytes.length<1||bytes.length>BankPolicy.MAX_BYTES)throw new BankFailure(413,"Synthetic evidence size rejected");
        try {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);Scan before=metadata(exchange(null,deadline));String answer=exchange(bytes,deadline);Scan after=metadata(exchange(null,deadline));
            if(!before.equals(after)||!answer.equals("stream: OK"))throw LocalEvidenceReceiptJournal.unavailable();return after;
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw LocalEvidenceReceiptJournal.unavailable();}catch(Exception failure){throw LocalEvidenceReceiptJournal.unavailable();}
    }
}
