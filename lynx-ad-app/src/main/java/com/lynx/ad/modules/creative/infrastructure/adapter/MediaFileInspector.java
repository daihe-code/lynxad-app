package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.domain.service.AssetFilePolicy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Decode real streams before storage/review. No shell, remote protocols or client metadata are trusted. */
@Component
public class MediaFileInspector {
    private final String executable;
    private final int timeoutSeconds;
    private final Semaphore slots = new Semaphore(2);
    public MediaFileInspector(@Value("${lynx.ad.storage.ffmpeg-path:ffmpeg}") String executable,
            @Value("${lynx.ad.storage.media-validation-timeout-sec:180}") int timeoutSeconds) {
        this.executable = executable; this.timeoutSeconds = Math.max(5, Math.min(600, timeoutSeconds));
    }
    public record Metadata(Integer width, Integer height, double durationSeconds) {}
    public void cover(Path video,Path image) {
        boolean acquired=false;
        try {
            if(!(acquired=slots.tryAcquire(5,TimeUnit.SECONDS)))throw new BizException(1526,"封面处理繁忙，请稍后重试");
            run(List.of("-hide_banner","-nostdin","-v","error","-y","-threads","1","-protocol_whitelist","file",
                    "-i",video.toString(),"-map","0:v:0","-frames:v","1","-threads","1","-update","1",image.toString()),true);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new BizException(1526,"封面处理被中断，请重试");}
        finally{if(acquired)slots.release();}
    }
    public Metadata validate(Path file, String filename) {
        var kind = AssetFilePolicy.spec(filename).kind();
        if (kind != AssetKind.VIDEO && kind != AssetKind.AUDIO) return null;
        boolean acquired = false;
        try {
            if (!(acquired = slots.tryAcquire(5, TimeUnit.SECONDS))) throw new BizException(1526,"媒体校验繁忙，请稍后重试");
            String info = run(List.of("-hide_banner", "-nostdin", "-threads", "1", "-protocol_whitelist", "file", "-i", file.toString()), false);
            var duration = Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)").matcher(info);
            if (!duration.find()) invalid("无法读取媒体时长，文件可能不完整");
            double seconds = Integer.parseInt(duration.group(1))*3600d + Integer.parseInt(duration.group(2))*60d + Double.parseDouble(duration.group(3));
            if (!Double.isFinite(seconds) || seconds <= 0) invalid("媒体时长必须大于 0 秒");
            Integer width = null, height = null;
            if (kind == AssetKind.VIDEO) {
                var stream = Pattern.compile("Video: (\\w+)[^\\r\\n]*? (?<w>\\d{1,6})x(?<h>\\d{1,6})(?:[ ,\\[])" ).matcher(info);
                if (!stream.find()) invalid("MP4 中没有可读取的视频轨道");
                if (!stream.group(1).equals("h264")) invalid("当前上传支持 H.264 编码的 MP4，请转换视频编码后上传");
                width = Integer.valueOf(stream.group("w")); height = Integer.valueOf(stream.group("h"));
                if (width <= 0 || height <= 0 || (long)width*height > AssetFilePolicy.MAX_PIXELS) invalid("视频尺寸无效或超过 4,000 万像素");
            } else {
                var stream = Pattern.compile("Audio: (\\w+)[^\\r\\n]*? (\\d+) Hz").matcher(info);
                if (!stream.find() || Integer.parseInt(stream.group(2)) <= 0) invalid("音频没有有效的音轨或采样率");
                String ext = filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
                boolean codec = switch(ext) { case "mp3" -> stream.group(1).equals("mp3"); case "m4a" -> stream.group(1).equals("aac"); case "wav" -> stream.group(1).startsWith("pcm_"); default -> false; };
                if (!codec) invalid("音频编码与格式不匹配；M4A 需 AAC，WAV 需 PCM 编码");
            }
            var decode = new ArrayList<>(List.of("-hide_banner", "-nostdin", "-v", "error", "-xerror", "-err_detect", "explode", "-threads", "1",
                "-protocol_whitelist", "file", "-i", file.toString(), "-map", kind==AssetKind.VIDEO ? "0:v:0" : "0:a:0",
                "-threads", "1"));
            if (kind==AssetKind.VIDEO) decode.addAll(List.of("-map","0:a?"));
            decode.addAll(List.of("-f","null","-")); run(decode, true);
            return new Metadata(width, height, seconds);
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BizException(1526,"媒体校验被中断，请重试"); }
        finally { if (acquired) slots.release(); }
    }
    private String run(List<String> args, boolean decode) {
        Process process = null;
        try {
            var command = new ArrayList<String>(); command.add(executable); command.addAll(args);
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            final Process child = process;
            // Drain continuously, but retain bounded diagnostic data. Never expose paths/logs to clients.
            var output = CompletableFuture.supplyAsync(() -> {
                try (var in = child.getInputStream(); var out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n=in.read(buffer))!=-1) if(out.size()<262144) out.write(buffer,0,Math.min(n,262144-out.size()));
                    return out.toString(StandardCharsets.UTF_8);
                } catch (IOException ex) { throw new CompletionException(ex); }
            });
            if (!process.waitFor(timeoutSeconds,TimeUnit.SECONDS)) throw new BizException(1526,"媒体校验超时，请压缩或拆分后重试");
            String info = output.get(5,TimeUnit.SECONDS);
            if (decode && (process.exitValue()!=0 || !info.isBlank())) invalid("媒体解码失败，文件可能损坏或不完整，请重新导出后上传");
            return info;
        } catch (BizException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new BizException(1526,"媒体校验被中断，请重试"); }
        catch (IOException ex) { throw new BizException(1526,"媒体校验组件不可用，请检查服务端 FFmpeg 配置"); }
        catch (ExecutionException | TimeoutException ex) { throw new BizException(1526,"媒体校验未完成，请重试"); }
        finally { if(process!=null && process.isAlive()) process.destroyForcibly(); }
    }
    private static void invalid(String message) { throw new BizException(1520,message); }
}
