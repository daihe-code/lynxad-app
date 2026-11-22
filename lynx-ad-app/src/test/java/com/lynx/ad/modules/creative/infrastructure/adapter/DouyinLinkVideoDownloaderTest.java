package com.lynx.ad.modules.creative.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 链接视频下载器的纯函数单测：只测不发网络请求的静态方法。
 */
class DouyinLinkVideoDownloaderTest {

    @Test
    void normalizeShareUrlAcceptsDouyinVideoUrl() {
        String url = "https://www.douyin.com/video/7400000000000000000";
        assertEquals(url, DouyinLinkVideoDownloader.normalizeShareUrl(url));
    }

    @Test
    void normalizeShareUrlAcceptsShortLink() {
        String url = "https://v.douyin.com/abc123/";
        assertEquals(url, DouyinLinkVideoDownloader.normalizeShareUrl(url));
    }

    @Test
    void normalizeShareUrlExtractsLinkFromSharedText() {
        String text = "快来看看这个视频吧：https://v.douyin.com/xyz789/ 很有意思";
        assertEquals("https://v.douyin.com/xyz789/", DouyinLinkVideoDownloader.normalizeShareUrl(text));
    }

    @Test
    void normalizeShareUrlRejectsOtherDomains() {
        assertThrows(BizException.class,
                () -> DouyinLinkVideoDownloader.normalizeShareUrl("https://www.bilibili.com/video/BV1xx411c7mD"));
    }

    @Test
    void normalizeShareUrlRejectsTextWithoutLink() {
        assertThrows(BizException.class,
                () -> DouyinLinkVideoDownloader.normalizeShareUrl("这里没有链接"));
    }

    @Test
    void awemeIdFromVideoUrl() {
        assertEquals("7400000000000000000",
                DouyinLinkVideoDownloader.awemeId("https://www.douyin.com/video/7400000000000000000"));
    }

    @Test
    void awemeIdFromShareVideoUrl() {
        assertEquals("7400000000000000000",
                DouyinLinkVideoDownloader.awemeId("https://www.iesdouyin.com/share/video/7400000000000000000"));
    }

    @Test
    void awemeIdReturnsNullForShortLink() {
        assertNull(DouyinLinkVideoDownloader.awemeId("https://v.douyin.com/abc123/"));
    }

    @Test
    void withoutWatermarkReplacesPlaywm() {
        assertEquals("https://v1.douyin.com/play/abc123",
                DouyinLinkVideoDownloader.withoutWatermark("https://v1.douyin.com/playwm/abc123"));
        assertNull(DouyinLinkVideoDownloader.withoutWatermark(null));
    }

    @Test
    void extractPlayUrlFromVideoInfoRes() throws Exception {
        String html = "<html><script>var videoInfoRes = {\"play_addr\":{\"url_list\":"
                + "[\"https://v1.douyin.com/playwm/abc123\"]}};</script></html>";
        assertEquals("https://v1.douyin.com/play/abc123",
                DouyinLinkVideoDownloader.extractPlayUrl(html, new ObjectMapper()));
    }

    @Test
    void extractPlayUrlFallsBackToUrlList() throws Exception {
        String html = "<html>\"url_list\":[\"https://v1.douyin.com/playwm/xyz\"]</html>";
        assertEquals("https://v1.douyin.com/play/xyz",
                DouyinLinkVideoDownloader.extractPlayUrl(html, new ObjectMapper()));
    }

    @Test
    void extractPlayUrlReturnsNullWithoutVideo() throws Exception {
        assertNull(DouyinLinkVideoDownloader.extractPlayUrl("<html>没有视频</html>", new ObjectMapper()));
    }

    @Test
    void findVideoUrlInJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("https://example.com/v.mp4",
                DouyinLinkVideoDownloader.findVideoUrl(mapper.readTree("{\"data\":{\"video_url\":\"https://example.com/v.mp4\"}}")));
        assertNull(DouyinLinkVideoDownloader.findVideoUrl(mapper.readTree("{\"task_id\":\"abc\"}")));
    }

    @Test
    void validateSpecAcceptsPortraitAndLandscape() {
        DouyinLinkVideoDownloader.validateSpec(720, 1280, 15.0);
        DouyinLinkVideoDownloader.validateSpec(1280, 720, 15.0);
    }

    @Test
    void validateSpecRejectsTooShort() {
        assertThrows(BizException.class, () -> DouyinLinkVideoDownloader.validateSpec(720, 1280, 3.0));
    }

    @Test
    void validateSpecRejectsSquare() {
        assertThrows(BizException.class, () -> DouyinLinkVideoDownloader.validateSpec(800, 800, 15.0));
    }

    @Test
    void validateSpecRejectsSmallShortEdge() {
        assertThrows(BizException.class, () -> DouyinLinkVideoDownloader.validateSpec(360, 640, 15.0));
    }

    @Test
    void sha256HexMatchesKnownValue() throws Exception {
        Path file = Files.createTempFile("remake-link-test-", ".bin");
        try {
            Files.write(file, "abc".getBytes(StandardCharsets.UTF_8));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                    DouyinLinkVideoDownloader.sha256Hex(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void probeRejectsNonVideoFile() throws Exception {
        Path file = Files.createTempFile("remake-link-test-", ".bin");
        try {
            Files.write(file, "not a video".getBytes(StandardCharsets.UTF_8));
            assertThrows(BizException.class,
                    () -> DouyinLinkVideoDownloader.probe(file, "ffprobe", new ObjectMapper()));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
