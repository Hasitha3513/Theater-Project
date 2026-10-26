package com.cinenest.payments.internal;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Pure qualification utility, deliberately not wired to uploads or the synthetic processor. */
final class ReceiptImageValidator {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final long MAX_PIXELS = 20_000_000;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};

    record Image(String sourceSha256, int width, int height, byte[] png) {
        Image { png = png.clone(); }
        @Override public byte[] png() { return png.clone(); }
    }

    Image validate(byte[] supplied, String mime) {
        if (supplied == null || supplied.length == 0 || supplied.length > MAX_BYTES)
            throw new BankFailure(413, "Receipt image size rejected");
        byte[] bytes = supplied.clone();
        String format;
        try {
            if ("image/png".equals(mime) && bytes.length > 8 && Arrays.equals(PNG, Arrays.copyOf(bytes,8))) {
                pngStructure(bytes); format = "png";
            } else if ("image/jpeg".equals(mime) && bytes.length > 3 && u(bytes[0]) == 255 && u(bytes[1]) == 216) {
                jpegStructure(bytes); format = "jpeg";
            } else throw new IOException();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                // Built-in JDK readers only: no unqualified image plugin or disk cache.
                var reader = ImageIO.getImageReadersByFormatName(format).next();
                try {
                    if (reader.getClass().getModule() != ImageIO.class.getModule()) throw new IOException();
                    var warning = new AtomicBoolean();
                    reader.addIIOReadWarningListener((r,w) -> warning.set(true));
                    reader.setInput(input, false, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long)width * height > MAX_PIXELS) throw new IOException();
                    BufferedImage source = reader.read(0);
                    if (warning.get() || source == null || source.getWidth() != width || source.getHeight() != height) throw new IOException();
                    var raster = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
                    // Row-bounded conversion works headlessly; flatten alpha onto white without metadata.
                    int[] row=new int[width];
                    for(int y=0;y<height;y++) {
                        source.getRGB(0,y,width,1,row,0,width);
                        for(int x=0;x<width;x++) {
                            int pixel=row[x],alpha=pixel>>>24;
                            int red=((pixel>>>16&255)*alpha+255*(255-alpha)+127)/255;
                            int green=((pixel>>>8&255)*alpha+255*(255-alpha)+127)/255;
                            int blue=((pixel&255)*alpha+255*(255-alpha)+127)/255;
                            row[x]=red<<16|green<<8|blue;
                        }
                        raster.setRGB(0,y,width,1,row,0,width);
                    }
                    source.flush();
                    try (var output = new BoundedOutput(); var imageOutput = new BoundedImageOutput(output)) {
                        var writer = ImageIO.getImageWritersByFormatName("png").next();
                        try {
                            if (writer.getClass().getModule() != ImageIO.class.getModule()) throw new IOException();
                            writer.setOutput(imageOutput);
                            writer.write(null,new javax.imageio.IIOImage(raster,null,null),writer.getDefaultWriteParam());
                            imageOutput.flush();
                        } finally { writer.dispose(); }
                        return new Image(BankPolicy.hash(bytes),width,height,output.toByteArray());
                    } finally { raster.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | RuntimeException rejected) {
            throw new BankFailure(415, "Receipt image format rejected");
        }
    }

    private static int u(byte b) { return b & 255; }
    private static long uint(byte[] b,int p) { return (long)u(b[p])<<24 | (long)u(b[p+1])<<16 | (long)u(b[p+2])<<8 | u(b[p+3]); }

    private static void pngStructure(byte[] b) throws IOException {
        int p=8; boolean header=false, data=false, dataEnded=false;
        while (p < b.length) {
            if (b.length-p < 12) throw new IOException();
            long n=uint(b,p); if (n > b.length-p-12) throw new IOException();
            int length=(int)n;
            String type=new String(b,p+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            if (!type.matches("[A-Za-z]{4}")) throw new IOException();
            var crc=new CRC32(); crc.update(b,p+4,length+4);
            if (crc.getValue()!=uint(b,p+8+length)) throw new IOException();
            if (!header && (!type.equals("IHDR") || length!=13)) throw new IOException();
            if (type.equals("IHDR")) { if(header) throw new IOException(); header=true; }
            else if (type.equals("IDAT")) { if(dataEnded) throw new IOException(); data=true; }
            else if (data) dataEnded=true;
            // APNG, unknown critical chunks and trailing payloads are outside this bounded format.
            if (type.equals("acTL") || type.equals("fcTL") || type.equals("fdAT") ||
                Character.isUpperCase(type.charAt(0)) && !java.util.Set.of("IHDR","PLTE","IDAT","IEND").contains(type)) throw new IOException();
            p+=length+12;
            if(type.equals("IEND")) { if(length!=0 || !data || p!=b.length) throw new IOException(); return; }
        }
        throw new IOException();
    }

    private static void jpegStructure(byte[] b) throws IOException {
        int p=2; boolean scan=false;
        while (p < b.length) {
            if(u(b[p++])!=255) throw new IOException();
            while(p < b.length && u(b[p])==255) p++;
            if(p==b.length) throw new IOException();
            int marker=u(b[p++]);
            if(marker==217) { if(!scan || p!=b.length) throw new IOException(); return; }
            if(marker==0 || marker==216 || marker==1 || marker>=208 && marker<=215 || p+2>b.length) throw new IOException();
            int length=u(b[p])*256+u(b[p+1]);
            if(length<2 || length>b.length-p) throw new IOException();
            p+=length;
            if(marker==218) {
                scan=true;
                while(p<b.length) {
                    if(u(b[p])!=255) { p++; continue; }
                    int start=p++;
                    while(p<b.length && u(b[p])==255) p++;
                    if(p==b.length) throw new IOException();
                    int next=u(b[p]);
                    if(next==0 || next>=208 && next<=215) { p++; continue; }
                    p=start; break;
                }
            }
        }
        throw new IOException();
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        @Override public void write(int b) throws IOException { if(bytes.size()>=MAX_BYTES) throw new IOException(); bytes.write(b); }
        @Override public void write(byte[] b,int off,int len) throws IOException { if(len>MAX_BYTES-bytes.size()) throw new IOException(); bytes.write(b,off,len); }
        byte[] toByteArray() { return bytes.toByteArray(); }
    }

    /** No ImageIO disk cache, and the encoder cannot buffer beyond the output ceiling. */
    private static final class BoundedImageOutput extends MemoryCacheImageOutputStream {
        BoundedImageOutput(OutputStream output) { super(output); }
        private void capacity(int length) throws IOException { if(length<0 || length>MAX_BYTES-getStreamPosition()) throw new IOException(); }
        @Override public void write(int b) throws IOException { capacity(1); super.write(b); }
        @Override public void write(byte[] b,int off,int len) throws IOException { capacity(len); super.write(b,off,len); }
        @Override public void seek(long position) throws IOException { if(position>MAX_BYTES) throw new IOException(); super.seek(position); }
    }
}
