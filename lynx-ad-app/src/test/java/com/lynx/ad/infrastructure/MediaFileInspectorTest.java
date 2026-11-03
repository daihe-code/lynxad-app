package com.lynx.ad.infrastructure;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.service.AssetFilePolicy;
import com.lynx.ad.modules.creative.infrastructure.adapter.MediaFileInspector;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MediaFileInspectorTest {
    @TempDir Path dir;
    String executable;
    MediaFileInspector inspector;
    @BeforeEach void setup() throws Exception {
        executable=System.getenv().getOrDefault("LYNX_FFMPEG_PATH","ffmpeg");
        try { var p=new ProcessBuilder(executable,"-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start(); Assumptions.assumeTrue(p.waitFor(10,TimeUnit.SECONDS)&&p.exitValue()==0,"FFmpeg runtime required"); }
        catch(java.io.IOException ex) { Assumptions.abort("FFmpeg runtime required"); }
        inspector=new MediaFileInspector(executable,30);
    }
    Path generate(String name,String... args) throws Exception {
        Path file=dir.resolve(name); var command=new ArrayList<>(List.of(executable,"-hide_banner","-nostdin","-v","error","-y"));
        command.addAll(Arrays.asList(args)); command.add(file.toString());
        var p=new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(p.waitFor(30,TimeUnit.SECONDS)); assertEquals(0,p.exitValue(),new String(p.getInputStream().readAllBytes())); return file;
    }
    @Test void realH264VideoHasDimensionsAndDurationAndFullyDecodes() throws Exception {
        Path file=generate("video.mp4","-f","lavfi","-i","color=c=blue:s=320x240:r=25","-t","1","-c:v","libx264","-pix_fmt","yuv420p");
        AssetFilePolicy.validate(file,"video.mp4",Files.size(file));
        var result=inspector.validate(file,"video.mp4"); assertEquals(320,result.width()); assertEquals(240,result.height()); assertEquals(1,result.durationSeconds(),0.1);
        Path cover=dir.resolve("cover.jpg");inspector.cover(file,cover);
        var image=javax.imageio.ImageIO.read(cover.toFile());assertNotNull(image);assertEquals(320,image.getWidth());assertEquals(240,image.getHeight());
    }
    @Test void realAudioFormatsAndCodecMismatch() throws Exception {
        for(String ext:List.of("mp3","m4a","wav")) {
            Path file=generate("audio."+ext,"-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","1");
            AssetFilePolicy.validate(file,file.getFileName().toString(),Files.size(file));
            assertTrue(inspector.validate(file,file.getFileName().toString()).durationSeconds()>0);
        }
        Path file=generate("alac.m4a","-f","lavfi","-i","sine=frequency=440","-t","1","-c:a","alac");
        assertThrows(BizException.class,()->inspector.validate(file,"alac.m4a"));
    }
    @Test void fakeContainerMissingTrackAndTruncatedWavAreRejected() throws Exception {
        Path fake=dir.resolve("fake.mp4"); Files.write(fake,new byte[]{0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0});
        assertThrows(BizException.class,()->inspector.validate(fake,"fake.mp4"));
        Path audio=generate("truncated.wav","-f","lavfi","-i","sine=frequency=440","-t","1");
        byte[] bytes=Files.readAllBytes(audio); Files.write(audio,Arrays.copyOf(bytes,bytes.length/2));
        assertThrows(BizException.class,()->inspector.validate(audio,"truncated.wav"));
    }
    @Test void unsupportedVideoCodecAndMissingRuntimeFailClosed() throws Exception {
        Path file=generate("mpeg4.mp4","-f","lavfi","-i","color=s=320x240","-t","1","-c:v","mpeg4");
        assertThrows(BizException.class,()->inspector.validate(file,"mpeg4.mp4"));
        assertThrows(BizException.class,()->new MediaFileInspector(dir.resolve("missing.exe").toString(),5).validate(file,"mpeg4.mp4"));
    }
    @Test void compatibilityRepairRetainsDurationAndAudioAndMakesPlayablePortraitMp4() throws Exception {
        Path source=generate("source.mp4","-f","lavfi","-i","color=c=blue:s=240x320:r=25","-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","2","-c:v","mpeg4","-c:a","aac");
        Path target=dir.resolve("compatible.mp4");inspector.repairForUpload(source,target,"VIDEO");
        var metadata=inspector.validate(target,"compatible.mp4");assertEquals(720,metadata.width());assertEquals(1280,metadata.height());assertEquals(2,metadata.durationSeconds(),.12);
        // Mapping the audio track fails if repair dropped it. Decode it completely as well.
        var p=new ProcessBuilder(executable,"-v","error","-i",target.toString(),"-map","0:a:0","-f","null","-").redirectErrorStream(true).start();
        assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue(),new String(p.getInputStream().readAllBytes()));
        assertTrue(Files.size(source)>0);
    }
    @Test void imageRepairKeepsSquareCompositionAndRemovesAlpha() throws Exception {
        var image=new java.awt.image.BufferedImage(800,800,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(400,400,0xffff0000);var source=dir.resolve("source.png");javax.imageio.ImageIO.write(image,"png",source.toFile());
        var target=dir.resolve("compatible.jpg");inspector.repairForUpload(source,target,"IMAGE");
        var checked=AssetFilePolicy.validate(target,"compatible.jpg",Files.size(target));assertEquals(800,checked.width());assertEquals(800,checked.height());
        var repaired=javax.imageio.ImageIO.read(target.toFile());assertFalse(repaired.getColorModel().hasAlpha());assertEquals(java.awt.Color.WHITE.getRGB(),repaired.getRGB(0,0));
    }
}
