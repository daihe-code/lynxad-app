package com.lynx.ad.domain.creative;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.service.AssetFilePolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreativeFixture;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AssetFilePolicyTest {
    @TempDir Path dir;
    @Test void actualBytesDetermineSignatureAndDimensions() throws Exception {
        Path file=dir.resolve("test.jpg");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,24,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpg",file.toFile());
        byte[] bytes=Files.readAllBytes(file);
        var validated=AssetFilePolicy.validate(file,"商品.jpg",bytes.length);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)),validated.md5());
        assertTrue(validated.width()>0); assertTrue(validated.height()>0);
        assertThrows(BizException.class,()->AssetFilePolicy.validate(file,"wrong.png",bytes.length));
    }
    @Test void validatesVideoBoxBoundariesAndRejectsTruncation() throws Exception {
        var out=new java.io.ByteArrayOutputStream(); var data=new java.io.DataOutputStream(out);
        data.write(CreativeFixture.mp4());
        data.writeInt(12); data.writeBytes("moov"); data.writeInt(0);
        data.writeInt(12); data.writeBytes("mdat"); data.writeInt(0);
        byte[] bytes=out.toByteArray(); Path file=dir.resolve("test.mp4"); Files.write(file,bytes);
        assertNotNull(AssetFilePolicy.validate(file,"test.mp4",bytes.length).md5());
        Files.write(file,java.util.Arrays.copyOf(bytes,bytes.length-7));
        assertThrows(BizException.class,()->AssetFilePolicy.validate(file,"test.mp4",bytes.length-7));
    }
    @Test void textSupportsChineseEncodingButRejectsBinary() throws Exception {
        String text="商品介绍：清洁用品\n卖点：柔软耐用";
        for(Charset charset:new Charset[]{StandardCharsets.UTF_8,Charset.forName("GB18030")}) {
            Path file=dir.resolve("text.txt"); Files.write(file,text.getBytes(charset));
            assertEquals(text,AssetFilePolicy.validate(file,"文案.txt",Files.size(file)).text());
        }
        Path binary=dir.resolve("binary.txt"); Files.write(binary,new byte[]{1,2,0,4});
        assertThrows(BizException.class,()->AssetFilePolicy.validate(binary,"binary.txt",4));
    }
    @Test void limitsAreSpecificToTheFileKind() {
        AssetFilePolicy.size(AssetFilePolicy.spec("test.mp4"),1000L*1024*1024);
        assertThrows(BizException.class,()->AssetFilePolicy.size(AssetFilePolicy.spec("test.mp4"),1000L*1024*1024+1));
        for(String extension:new String[]{"jpg","jpeg","png"}) {
            AssetFilePolicy.size(AssetFilePolicy.spec("test."+extension),10L*1024*1024);
            assertThrows(BizException.class,()->AssetFilePolicy.size(AssetFilePolicy.spec("test."+extension),10L*1024*1024+1));
        }
        for(String extension:new String[]{"mp3","m4a"}) {
            AssetFilePolicy.size(AssetFilePolicy.spec("test."+extension),20L*1024*1024);
            assertThrows(BizException.class,()->AssetFilePolicy.size(AssetFilePolicy.spec("test."+extension),20L*1024*1024+1));
        }
        AssetFilePolicy.size(AssetFilePolicy.spec("test.wav"),10L*1024*1024);
        assertThrows(BizException.class,()->AssetFilePolicy.size(AssetFilePolicy.spec("test.wav"),10L*1024*1024+1));
        assertThrows(BizException.class,()->AssetFilePolicy.size(AssetFilePolicy.spec("test.txt"),2L*1024*1024));
        assertThrows(BizException.class,()->AssetFilePolicy.spec("test.mov"));
    }
    @Test void wavCannotBeSentToQianchuanAndTextLimitIsEnforced() throws Exception {
        assertDoesNotThrow(()->AssetFilePolicy.target("音频.WAV",false));
        assertThrows(BizException.class,()->AssetFilePolicy.target("音频.WAV",true));
        assertDoesNotThrow(()->AssetFilePolicy.target("音频.m4a",true));
        Path file=dir.resolve("large.txt"); Files.writeString(file,"字".repeat(50001));
        assertThrows(BizException.class,()->AssetFilePolicy.validate(file,"large.txt",Files.size(file)));
    }
    @Test void oversizedImageDimensionsAreRejectedBeforePixelAllocation() throws Exception {
        Path file=dir.resolve("oversized.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",file.toFile());
        byte[] bytes=Files.readAllBytes(file); var data=java.nio.ByteBuffer.wrap(bytes);
        data.putInt(16,10000); data.putInt(20,10000);
        var crc=new java.util.zip.CRC32(); crc.update(bytes,12,17); data.putInt(29,(int)crc.getValue());
        Files.write(file,bytes);
        assertTrue(assertThrows(BizException.class,()->AssetFilePolicy.validate(file,"oversized.png",bytes.length)).getMessage().contains("尺寸"));
    }
}
