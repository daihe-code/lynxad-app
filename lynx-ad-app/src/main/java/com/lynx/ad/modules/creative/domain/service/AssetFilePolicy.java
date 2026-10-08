package com.lynx.ad.modules.creative.domain.service;

import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class AssetFilePolicy {
    private AssetFilePolicy() {}
    public record FileSpec(AssetKind kind, String contentType, long maxBytes) {}
    public record Validated(String md5, long size, Integer width, Integer height, String text) {}
    public static final long MAX_PIXELS = 40_000_000;
    public record Rule(String extension, AssetKind kind, long maxBytes, boolean qianchuanSupported) {}
    public record Policy(List<Rule> files, long maxPixels, int maxTextChars, int maxFiles, String videoCodec, String m4aCodec) {}
    public static Policy policy() {
        return new Policy(List.of("mp4", "jpg", "jpeg", "png", "mp3", "m4a", "wav", "txt").stream()
            .map(ext -> new Rule(ext, spec("file."+ext).kind(), spec("file."+ext).maxBytes(), !ext.equals("wav") && !ext.equals("txt"))).toList(),
            MAX_PIXELS, 50000, 20, "h264", "aac");
    }
    public static void target(String filename, boolean qianchuan) {
        if (qianchuan && filename.toLowerCase(Locale.ROOT).endsWith(".wav"))
            throw new BizException(1520, "千川音频仅支持 MP3/M4A；请转换 WAV，或取消同步账户后仅保存到平台");
    }

    public static void library(AssetKind kind, String libraryType) {
        // Older API clients may omit library context; file-content validation still applies.
        if (libraryType == null) return;
        String type = libraryType.toUpperCase(Locale.ROOT);
        String label = switch (type) {
            case "VIDEO" -> "视频库";
            case "IMAGE" -> "图片库";
            case "AUDIO" -> "音频库";
            case "TEXT" -> "文本库";
            case "CAROUSEL" -> "图文库";
            default -> throw new BizException(1520, "素材库类型不合法");
        };
        boolean accepted = type.equals("CAROUSEL") ? kind != AssetKind.VIDEO : type.equals(kind.name());
        if (!accepted) throw new BizException(1520, "文件类型与当前" + label + "不匹配，请在对应素材库上传");
    }

    public static FileSpec spec(String filename) {
        String ext = filename == null ? "" : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "mp4" -> new FileSpec(AssetKind.VIDEO, "video/mp4", 500L * 1024 * 1024);
            case "jpg", "jpeg" -> new FileSpec(AssetKind.IMAGE, "image/jpeg", 1536L * 1024);
            case "png" -> new FileSpec(AssetKind.IMAGE, "image/png", 1536L * 1024);
            // The review engine accepts at most 10 MiB; do not accept files it cannot inspect.
            case "mp3" -> new FileSpec(AssetKind.AUDIO, "audio/mpeg", 10L * 1024 * 1024);
            case "m4a" -> new FileSpec(AssetKind.AUDIO, "audio/mp4", 10L * 1024 * 1024);
            case "wav" -> new FileSpec(AssetKind.AUDIO, "audio/wav", 10L * 1024 * 1024);
            case "txt" -> new FileSpec(AssetKind.TEXT, "text/plain; charset=utf-8", 1024L * 1024);
            default -> throw new BizException(1520, "支持 MP4、JPG/JPEG/PNG、MP3/M4A/WAV 和 TXT 文件");
        };
    }
    public static void size(FileSpec spec, long size) {
        if (size <= 0 || size > spec.maxBytes()) throw new BizException(1520, "文件为空或超过该类型的大小限制");
    }
    public static Validated validate(Path path, String filename, long expected) throws IOException {
        FileSpec spec = spec(filename); long size = Files.size(path); size(spec, size);
        if (size != expected) throw new BizException(1520, "实际文件大小与上传声明不一致");
        MessageDigest md5;
        try { md5 = MessageDigest.getInstance("MD5"); } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        try (var in = new DigestInputStream(Files.newInputStream(path), md5)) { in.transferTo(OutputStream.nullOutputStream()); }
        byte[] header;
        try (var in = Files.newInputStream(path)) { header = in.readNBytes(32); }
        Integer width = null, height = null; String text = null;
        switch (spec.kind()) {
            case IMAGE -> {
                boolean png = header.length >= 8 && Arrays.equals(Arrays.copyOf(header, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
                boolean jpg = header.length >= 3 && header[0] == (byte)255 && header[1] == (byte)216 && header[2] == (byte)255;
                if (!(spec.contentType().equals("image/png") ? png : jpg)) invalid();
                // Read dimensions before decoding to avoid decompression bombs.
                try (var imageIn = ImageIO.createImageInputStream(path.toFile())) {
                    var readers = ImageIO.getImageReaders(imageIn); if (!readers.hasNext()) invalid();
                    var reader = readers.next();
                    try {
                        reader.setInput(imageIn); width = reader.getWidth(0); height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw new BizException(1520,"图片尺寸无效或超过 4,000 万像素，请缩小图片后上传");
                        if (reader.read(0) == null) invalid();
                    } finally { reader.dispose(); }
                } catch (javax.imageio.IIOException ex) { invalid(); }
            }
            case VIDEO -> {
                if (header.length < 16 || !new String(header, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")) invalid();
                // Box boundaries are checked, rather than trusting an extension or browser MIME.
                try (var file = new RandomAccessFile(path.toFile(), "r")) {
                    long pos = 0; boolean media = false, metadata = false;
                    while (pos + 8 <= size) {
                        file.seek(pos); long boxSize = Integer.toUnsignedLong(file.readInt());
                        byte[] type = new byte[4]; file.readFully(type); String box = new String(type, StandardCharsets.US_ASCII);
                        long min = 8;
                        if (boxSize == 1) { boxSize = file.readLong(); min = 16; }
                        else if (boxSize == 0) boxSize = size - pos;
                        if (boxSize < min || boxSize > size - pos) invalid();
                        media |= "mdat".equals(box) && boxSize > min; metadata |= "moov".equals(box) && boxSize > min; pos += boxSize;
                    }
                    if (pos != size || !media || !metadata) invalid();
                }
            }
            case AUDIO -> {
                if (spec.contentType().equals("audio/mp4")) {
                    if (header.length < 16 || !new String(header,4,4,StandardCharsets.US_ASCII).equals("ftyp")) invalid();
                    break; // Actual AAC stream and full decoding are checked by MediaFileInspector.
                }
                boolean wav = header.length >= 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF") && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
                boolean mp3 = header.length >= 3 && ((header[0] == 'I' && header[1] == 'D' && header[2] == '3') || (header[0] == (byte)255 && (header[1] & 0xe0) == 0xe0));
                if (!(spec.contentType().equals("audio/wav") ? wav : mp3)) invalid();
                if (wav) {
                    try (var audio=javax.sound.sampled.AudioSystem.getAudioInputStream(path.toFile())) { if (audio.getFrameLength()<=0) invalid(); }
                    catch (javax.sound.sampled.UnsupportedAudioFileException ex) { invalid(); }
                } else {
                    try (var file=new RandomAccessFile(path.toFile(),"r")) {
                        long offset=0;
                        if(header[0]=='I') {
                            if(header.length<10 || (header[6]&128)!=0 || (header[7]&128)!=0 || (header[8]&128)!=0 || (header[9]&128)!=0) invalid();
                            offset=10L+((header[6]&127)<<21)+((header[7]&127)<<14)+((header[8]&127)<<7)+(header[9]&127);
                        }
                        if(offset+4>size) invalid(); file.seek(offset); int frame=file.readInt();
                        int version=(frame>>>19)&3, layer=(frame>>>17)&3, rate=(frame>>>12)&15, sample=(frame>>>10)&3;
                        if((frame&0xffe00000)!=0xffe00000 || version==1 || layer!=1 || rate==0 || rate==15 || sample==3) invalid();
                        int[] bitrate=version==3 ? new int[]{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320} : new int[]{0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
                        int hz=new int[]{44100,48000,32000}[sample]/(version==3 ? 1 : version==2 ? 2 : 4);
                        int frameSize=(version==3 ? 144000 : 72000)*bitrate[rate]/hz+((frame>>>9)&1);
                        if(offset+frameSize>size) invalid();
                    }
                }
            }
            case TEXT -> {
                byte[] bytes = Files.readAllBytes(path);
                text = decodeText(bytes);
                if(text.length()>50000) throw new BizException(1520,"AI 文本审核最多支持 50,000 个字符，请拆分文件后上传");
                if (text.isBlank() || text.indexOf('\0') >= 0 || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != 9 && c != 10 && c != 13)) invalid();
            }
        }
        return new Validated(HexFormat.of().formatHex(md5.digest()), size, width, height, text);
    }
    private static String decodeText(byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == (byte)255 && bytes[1] == (byte)254) return strict(bytes, 2, StandardCharsets.UTF_16LE);
        if (bytes.length >= 2 && bytes[0] == (byte)254 && bytes[1] == (byte)255) return strict(bytes, 2, StandardCharsets.UTF_16BE);
        int start = bytes.length >= 3 && bytes[0] == (byte)239 && bytes[1] == (byte)187 && bytes[2] == (byte)191 ? 3 : 0;
        try { return strict(bytes, start, StandardCharsets.UTF_8); }
        catch (BizException ex) { return strict(bytes, 0, Charset.forName("GB18030")); }
    }
    private static String strict(byte[] bytes, int start, Charset charset) {
        try { return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString(); }
        catch (CharacterCodingException ex) { throw new BizException(1520, "文本编码无法识别，请使用 UTF-8、带 BOM 的 UTF-16 或 GB18030"); }
    }
    private static void invalid() { throw new BizException(1520, "文件内容损坏或与文件扩展名不匹配"); }
}
