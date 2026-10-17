package com.cinenest.verification.internal;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
/** SIMULATED scanner. Hash admission is not real-document detection or production malware protection. */
@Component
class SyntheticEvidenceProcessor {
    record Result(String digest,String mime,byte[] preview){}
    Result process(byte[] bytes,String mime,VerificationPolicy.Policy policy){
        if(bytes.length==0||bytes.length>VerificationPolicy.MAX_BYTES)throw new VerificationFailure(413,"Synthetic image size rejected");
        String hash=VerificationPolicy.hash(bytes);
        if(!policy.fixtures().contains(hash))throw new VerificationFailure(415,"Only explicitly approved synthetic fixtures are allowed");
        boolean png=bytes.length>8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10;
        boolean jpeg=bytes.length>3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255;
        if(!(png&&mime.equals("image/png")||jpeg&&mime.equals("image/jpeg")))throw new VerificationFailure(415,"Synthetic image type rejected");
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();
            try{
                reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||(long)w*h>VerificationPolicy.MAX_PIXELS)throw new IOException();
                var source=reader.read(0);var safe=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=safe.createGraphics();graphics.drawImage(source,0,0,null);graphics.dispose();
                var out=new ByteArrayOutputStream();if(!ImageIO.write(safe,"png",out)||out.size()>VerificationPolicy.MAX_BYTES)throw new IOException();
                return new Result(hash,mime,out.toByteArray()); // Fresh raster encoding strips metadata. No OCR.
            }finally{reader.dispose();}
        }catch(IOException|RuntimeException invalid){throw new VerificationFailure(415,"Malformed synthetic image rejected");}
    }
}
