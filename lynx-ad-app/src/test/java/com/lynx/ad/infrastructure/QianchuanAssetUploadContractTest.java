package com.lynx.ad.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.AdvertiserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.http.*;
import com.lynx.ad.modules.creative.domain.model.AssetKind;
import com.lynx.ad.modules.creative.infrastructure.adapter.QianchuanAssetUploadAdapter;
import com.lynx.ad.modules.identity.domain.model.Credential;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QianchuanAssetUploadContractTest {
    @TempDir Path dir;
    HttpServer server;
    @AfterEach void stop() { if(server!=null) server.stop(0); }
    private QianchuanAssetUploadAdapter adapter() {
        var mapper=new ObjectMapper(); var properties=new LynxAdProperties(); properties.getMock().setQianchuan(false);
        String url="http://127.0.0.1:"+server.getAddress().getPort()+"/upload";
        properties.getQianchuan().getMaterial().setVideoUploadUrl(url); properties.getQianchuan().getMaterial().setImageUploadUrl(url); properties.getQianchuan().getMaterial().setAudioUploadUrl(url);
        return new QianchuanAssetUploadAdapter(new QianchuanRestClient(mapper,ms->{},HttpClient.newHttpClient()),new OceanJson(mapper),properties);
    }
    @Test void sendsBinaryMultipartAndPreservesSeparateLargeIds() throws Exception {
        AtomicReference<String> body=new AtomicReference<>(), token=new AtomicReference<>(), contentType=new AtomicReference<>();
        server=HttpServer.create(new InetSocketAddress(0),0);
        server.createContext("/upload",ex->{ body.set(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); token.set(ex.getRequestHeaders().getFirst("Access-Token")); contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            byte[] response="{\"code\":0,\"request_id\":\"req\",\"data\":{\"audio_info\":{\"audio_id\":\"audio-file-id\",\"material_id\":9223372036854775806}}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200,response.length); ex.getResponseBody().write(response); ex.close(); }); server.start();
        Path file=dir.resolve("audio.mp3"); Files.writeString(file,"RAW_FILE_BYTES");
        var result=adapter().upload(new Credential("db-access-token",new AdvertiserId("123")),AssetKind.AUDIO,file,"audio.mp3","audio/mpeg","actual-md5",false);
        assertTrue(contentType.get().startsWith("multipart/form-data; boundary=")); assertEquals("db-access-token",token.get());
        assertTrue(body.get().contains("name=\"audio_file\"; filename=\"audio.mp3\"")); assertTrue(body.get().contains("RAW_FILE_BYTES"));
        assertTrue(body.get().contains("name=\"audio_signature\"\r\n\r\nactual-md5")); assertTrue(body.get().contains("UPLOAD_BY_FILE"));
        assertEquals("audio-file-id",result.objectId()); assertEquals("9223372036854775806",result.materialId());
    }
    @Test void ambiguousPostIsNotRetriedAndAuthFailureIsNotRefreshed() throws Exception {
        AtomicInteger calls=new AtomicInteger(), status=new AtomicInteger(503);
        server=HttpServer.create(new InetSocketAddress(0),0); server.createContext("/upload",ex->{ calls.incrementAndGet(); ex.getRequestBody().readAllBytes(); ex.sendResponseHeaders(status.get(),-1); ex.close(); }); server.start();
        Path file=dir.resolve("file.jpg"); Files.writeString(file,"bytes"); var adapter=adapter(); var credential=new Credential("token",new AdvertiserId("123"));
        assertEquals(1523,assertThrows(BizException.class,()->adapter.upload(credential,AssetKind.IMAGE,file,"file.jpg","image/jpeg","md5",false)).code());
        assertEquals(1,calls.get()); status.set(401);
        assertEquals(1211,assertThrows(BizException.class,()->adapter.upload(credential,AssetKind.IMAGE,file,"file.jpg","image/jpeg","md5",false)).code());
        assertEquals(2,calls.get());
    }
    @Test void missingBusinessCodeIsAnUnknownReceiptRatherThanAProvenRejection()throws Exception {
        var response=new AtomicReference<>("{\"message\":\"gateway incomplete\",\"data\":{\"id\":\"maybe-committed\"}}");var calls=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress(0),0);server.createContext("/upload",ex->{calls.incrementAndGet();ex.getRequestBody().readAllBytes();var bytes=response.get().getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,bytes.length);ex.getResponseBody().write(bytes);ex.close();});server.start();
        Path file=dir.resolve("file.jpg");Files.writeString(file,"bytes");var adapter=adapter();var c=new Credential("test-token",new AdvertiserId("123"));
        for(String body:java.util.List.of("{\"message\":\"incomplete\"}","{\"code\":null}","{\"code\":-1}","{\"code\":\"0\"}")){response.set(body);assertEquals(1523,assertThrows(BizException.class,()->adapter.upload(c,AssetKind.IMAGE,file,"file.jpg","image/jpeg","md5",false)).code());}
        assertEquals(4,calls.get());response.set("{\"code\":40000,\"message\":\"explicit rejection\"}");assertEquals(1511,assertThrows(BizException.class,()->adapter.upload(c,AssetKind.IMAGE,file,"file.jpg","image/jpeg","md5",false)).code());assertEquals(5,calls.get());
    }
}
