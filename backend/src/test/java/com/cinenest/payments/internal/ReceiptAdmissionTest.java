package com.cinenest.payments.internal;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReceiptAdmissionTest {
    final ReceiptImageValidator validator=new ReceiptImageValidator();
    static byte[] image(String format,int width,int height) throws Exception {
        var raster=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        raster.setRGB(0,0,0x123456);
        var bytes=new ByteArrayOutputStream(); assertThat(ImageIO.write(raster,format,bytes)).isTrue(); raster.flush(); return bytes.toByteArray();
    }
    static byte[] chunk(String type,byte[] data) throws Exception {
        var out=new ByteArrayOutputStream(); var stream=new DataOutputStream(out);
        stream.writeInt(data.length); byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);stream.write(name);stream.write(data);
        var crc=new CRC32();crc.update(name);crc.update(data);stream.writeInt((int)crc.getValue());return out.toByteArray();
    }
    static byte[] insert(byte[] png,byte[] chunk) {
        var out=new ByteArrayOutputStream();out.writeBytes(Arrays.copyOf(png,33));out.writeBytes(chunk);out.writeBytes(Arrays.copyOfRange(png,33,png.length));return out.toByteArray();
    }
    @Test void admissionAlwaysDeniesRegardlessOfValidationResult() throws Exception {
        validator.validate(image("png",2,2),"image/png");
        var policy=new ReceiptAdmissionPolicy();assertThat(policy.decision()).isEqualTo(ReceiptAdmissionPolicy.Decision.DENY);
        assertThatThrownBy(policy::requireAdmission).isInstanceOf(BankFailure.class).hasMessage("Real receipt admission is disabled");
    }
    @Test void bothFormatsProduceFreshPngWithImmutableResultAndOriginalDigest() throws Exception {
        for(String format:List.of("png","jpeg")) {
            byte[] input=image(format,8,9);var result=validator.validate(input,"image/"+format);
            assertThat(result.sourceSha256()).isEqualTo(BankPolicy.hash(input));assertThat(result.width()).isEqualTo(8);assertThat(result.height()).isEqualTo(9);
            byte[] safe=result.png();assertThat(ImageIO.read(new ByteArrayInputStream(safe)).getWidth()).isEqualTo(8);
            safe[0]=0;assertThat(result.png()[0]).isEqualTo((byte)137);input[0]=0;assertThat(result.png()[0]).isEqualTo((byte)137);
        }
    }
    @Test void nullEmptyOversizeAndWrongMimeAreDenied() throws Exception {
        for(byte[] b:new byte[][]{null,new byte[0],new byte[ReceiptImageValidator.MAX_BYTES+1]})assertThatThrownBy(()->validator.validate(b,"image/png")).isInstanceOf(BankFailure.class);
        byte[] png=image("png",1,1),jpeg=image("jpeg",1,1);
        for(String mime:Arrays.asList(null,"image/jpeg","IMAGE/PNG","image/png; charset=utf-8","application/pdf"))assertThatThrownBy(()->validator.validate(png,mime)).isInstanceOf(BankFailure.class);
        assertThatThrownBy(()->validator.validate(jpeg,"image/png")).isInstanceOf(BankFailure.class);
        assertThatThrownBy(()->validator.validate("not an image".getBytes(),"image/jpeg")).isInstanceOf(BankFailure.class);
    }
    @Test void everyTruncatedPrefixAndTrailingPayloadAreDenied() throws Exception {
        for(String format:List.of("png","jpeg")) {
            byte[] b=image(format,3,3);
            for(int n=1;n<b.length;n++){byte[] prefix=Arrays.copyOf(b,n);assertThatThrownBy(()->validator.validate(prefix,"image/"+format)).isInstanceOf(BankFailure.class);}
            byte[] trailing=Arrays.copyOf(b,b.length+1);assertThatThrownBy(()->validator.validate(trailing,"image/"+format)).isInstanceOf(BankFailure.class);
        }
    }
    @Test void crcMalformedChunksAnimationAndInvalidDimensionsAreDenied() throws Exception {
        byte[] png=image("png",1,1),bad=png.clone();bad[45]^=1;
        assertThatThrownBy(()->validator.validate(bad,"image/png")).isInstanceOf(BankFailure.class);
        for(String kind:List.of("acTL","ABCD")) {byte[] b=insert(png,chunk(kind,new byte[8]));assertThatThrownBy(()->validator.validate(b,"image/png")).isInstanceOf(BankFailure.class);}
        for(int width:new int[]{0,20_000_001,Integer.MAX_VALUE}) {
            byte[] header=Arrays.copyOfRange(png,16,29);java.nio.ByteBuffer.wrap(header).putInt(width);
            var out=new ByteArrayOutputStream();out.writeBytes(Arrays.copyOf(png,8));out.writeBytes(chunk("IHDR",header));out.writeBytes(Arrays.copyOfRange(png,33,png.length));byte[] b=out.toByteArray();
            assertThatThrownBy(()->validator.validate(b,"image/png")).isInstanceOf(BankFailure.class);
        }
    }
    @Test void inputLimitIsInclusiveAndMetadataIsRemoved() throws Exception {
        byte[] png=image("png",2,2);int padding=ReceiptImageValidator.MAX_BYTES-png.length-12;
        byte[] withMetadata=insert(png,chunk("teSt",new byte[padding]));assertThat(withMetadata).hasSize(ReceiptImageValidator.MAX_BYTES);
        assertThat(validator.validate(withMetadata,"image/png").png()).hasSizeLessThan(1024);
    }
    @Test void pixelLimitIsInclusive() throws Exception {
        byte[] png=image("png",5000,4000);assertThat(validator.validate(png,"image/png").width()).isEqualTo(5000);
        byte[] header=Arrays.copyOfRange(png,16,29);java.nio.ByteBuffer.wrap(header).putInt(5001);
        var out=new ByteArrayOutputStream();out.writeBytes(Arrays.copyOf(png,8));out.writeBytes(chunk("IHDR",header));out.writeBytes(Arrays.copyOfRange(png,33,png.length));
        assertThatThrownBy(()->validator.validate(out.toByteArray(),"image/png")).isInstanceOf(BankFailure.class);
    }
    @Test void boundedOutputRejectsDecompressionExpansionBeyondPreviewLimit() throws Exception {
        var raster=new BufferedImage(1800,1800,BufferedImage.TYPE_INT_RGB);var random=new Random(71);
        for(int y=0;y<1800;y++)for(int x=0;x<1800;x++)raster.setRGB(x,y,random.nextInt());
        var bytes=new ByteArrayOutputStream();ImageIO.write(raster,"jpeg",bytes);raster.flush();byte[] jpeg=bytes.toByteArray();assertThat(jpeg.length).isLessThan(ReceiptImageValidator.MAX_BYTES);
        assertThatThrownBy(()->validator.validate(jpeg,"image/jpeg")).isInstanceOf(BankFailure.class);
    }
    @Test void parallelValidationHasNoSharedMutableState() throws Exception {
        byte[] png=image("png",8,8);try(var workers=java.util.concurrent.Executors.newFixedThreadPool(4)){
            var jobs=new ArrayList<java.util.concurrent.Future<String>>();for(int i=0;i<12;i++)jobs.add(workers.submit(()->BankPolicy.hash(validator.validate(png,"image/png").png())));
            String expected=jobs.getFirst().get();for(var job:jobs)assertThat(job.get()).isEqualTo(expected);
        }
    }
    @Test void truncatedEntropyWithValidOuterFramingIsRejected() throws Exception {
        byte[] jpeg=image("jpeg",16,16);int sos=-1;
        for(int i=2;i<jpeg.length-1;i++)if((jpeg[i]&255)==255 && (jpeg[i+1]&255)==218){sos=i;break;}
        assertThat(sos).isPositive();int entropy=sos+2+(jpeg[sos+2]&255)*256+(jpeg[sos+3]&255);
        byte[] truncated=Arrays.copyOf(jpeg,entropy+2);truncated[entropy]=(byte)255;truncated[entropy+1]=(byte)217;
        assertThatThrownBy(()->validator.validate(truncated,"image/jpeg")).isInstanceOf(BankFailure.class);
        byte[] png=image("png",16,16);int p=8;var out=new ByteArrayOutputStream();out.writeBytes(Arrays.copyOf(png,8));
        while(p<png.length){int length=java.nio.ByteBuffer.wrap(png,p,4).getInt();String type=new String(png,p+4,4,java.nio.charset.StandardCharsets.US_ASCII);byte[] data=Arrays.copyOfRange(png,p+8,p+8+length);if(type.equals("IDAT"))data=Arrays.copyOf(data,Math.max(1,data.length/2));out.writeBytes(chunk(type,data));p+=length+12;}
        assertThatThrownBy(()->validator.validate(out.toByteArray(),"image/png")).isInstanceOf(BankFailure.class);
    }
    @Test void transparentAndGraySourcesBecomeMetadataFreeRgb() throws Exception {
        for(int type:new int[]{BufferedImage.TYPE_INT_ARGB,BufferedImage.TYPE_BYTE_GRAY}){
            var image=new BufferedImage(2,2,type);var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
            byte[] withText=insert(bytes.toByteArray(),chunk("tEXt","Comment\0SYNTHETIC PRIVATE METADATA".getBytes()));
            byte[] safe=validator.validate(withText,"image/png").png();assertThat(new String(safe,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("PRIVATE METADATA");
            var raster=ImageIO.read(new ByteArrayInputStream(safe));assertThat(raster.getColorModel().hasAlpha()).isFalse();
            if(type==BufferedImage.TYPE_INT_ARGB)assertThat(raster.getRGB(0,0)&0xffffff).isEqualTo(0xffffff);
        }
    }
}
