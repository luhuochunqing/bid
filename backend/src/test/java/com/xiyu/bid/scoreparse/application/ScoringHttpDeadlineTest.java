package com.xiyu.bid.scoreparse.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ScoringHttpDeadlineTest {
    @Test void unknownLengthResponseKeepsQuotaDiagnosis(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception {
        var store=new TempBidFileStore(directory,32*1024);
        var fetcher=new StreamingBidFileFetcher(store,4096,16*1024,5);
        var client=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked")
        java.net.http.HttpResponse<java.io.InputStream> response=org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(),(a,b)->true));
        org.mockito.Mockito.when(response.body()).thenReturn(new java.io.ByteArrayInputStream(new byte[24*1024]));
        org.mockito.Mockito.when(client.send(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<java.io.InputStream>>any())).thenReturn(response);
        org.springframework.test.util.ReflectionTestUtils.setField(fetcher,"httpClient",client);
        assertThatThrownBy(()->fetcher.fetchWithDeadline("http://download.invalid/bid","unknown","bid.pdf","application/pdf",System.currentTimeMillis()+5000))
                .isInstanceOf(BidFileQuotaException.class).hasMessageContaining("配额");
        assertThat(java.nio.file.Files.exists(directory.resolve("unknown.part"))).isFalse();
        store.reserve("next");store.recordSpillChunk("next",32*1024);store.release("next");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false,true})
    void chunkedOversizeDownloadKeepsQuotaDiagnosisAndReleasesSpill(boolean withDeadline,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chunked",exchange->{
            exchange.sendResponseHeaders(200,0);
            try(var body=exchange.getResponseBody()) {
                body.write(new byte[24*1024]);
            }finally{exchange.close();}
        });
        server.start();
        try {
            var store=new TempBidFileStore(directory,32*1024);
            var fetcher=new StreamingBidFileFetcher(store,4096,16*1024,5);
            String url="http://127.0.0.1:"+server.getAddress().getPort()+"/chunked";
            assertThatThrownBy(()->{
                if(withDeadline)fetcher.fetchWithDeadline(url,"chunked","bid.pdf","application/pdf",System.currentTimeMillis()+5000);
                else fetcher.fetch(url,"chunked","bid.pdf","application/pdf");
            }).isInstanceOf(BidFileQuotaException.class).hasMessageContaining("配额");
            assertThat(java.nio.file.Files.exists(directory.resolve("chunked.part"))).isFalse();
            store.reserve("next");store.recordSpillChunk("next",32*1024);store.release("next");
        }finally{server.stop(0);}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"IMAGE,503","IMAGE,422","TEXT,503","TEXT,422"})
    void deadlineClosesStalledErrorBodyAfterHeaders(String kind,int status)throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var workers=Executors.newCachedThreadPool();server.setExecutor(workers);
        var headersSent=new CountDownLatch(1);var release=new CountDownLatch(1);
        server.createContext("IMAGE".equals(kind)?"/convert-images":"/convert",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(status,0);
            try(var body=exchange.getResponseBody()) {
                body.flush();headersSent.countDown();
                try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            }finally{exchange.close();}
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var text=new SidecarStreamTextExtractor(url,"",1000,30);
            var image=new SidecarImageExtractor(url,"",1000,30,new com.fasterxml.jackson.databind.ObjectMapper());
            long started=System.nanoTime();
            assertThatThrownBy(()->{
                long deadline=System.currentTimeMillis()+500;
                if("IMAGE".equals(kind))image.renderPages(new byte[]{1},"bid.pdf",java.util.Set.of(1),deadline);
                else text.extractScoringDocument(new byte[]{1},"bid.pdf",deadline);
            }).isInstanceOfAny(java.io.IOException.class,IllegalStateException.class);
            assertThat(headersSent.await(0,TimeUnit.MILLISECONDS)).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(2000);
        }finally{release.countDown();server.stop(0);workers.shutdownNow();}
    }
    @Test void deadlineClosesStalledBodyAfterSuccessfulHeaders()throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var workers=Executors.newCachedThreadPool();server.setExecutor(workers);
        var headersSent=new CountDownLatch(1);var release=new CountDownLatch(1);
        server.createContext("/convert",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,0);
            try(var body=exchange.getResponseBody()) {
                body.write("{\"markdown\":\"".getBytes(StandardCharsets.UTF_8));body.flush();headersSent.countDown();
                try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            }finally{exchange.close();}
        });
        server.start();
        try {
            var extractor=new SidecarStreamTextExtractor("http://127.0.0.1:"+server.getAddress().getPort(),"",1000,30);
            long started=System.nanoTime();
            assertThatThrownBy(()->extractor.extractScoringDocument(new byte[]{1},"bid.pdf",System.currentTimeMillis()+500))
                    .isInstanceOfAny(java.io.IOException.class,IllegalStateException.class);
            assertThat(headersSent.await(0,TimeUnit.MILLISECONDS)).isTrue();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)).isLessThan(2000);
        }finally{release.countDown();server.stop(0);workers.shutdownNow();}
    }
}
