package ru.antoncharov;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private Semaphore semaphore;
    private final int requestLimit;
    private final long timeIntervalMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit);

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.schedule(this::reset, timeIntervalMillis, timeUnit);
        service.shutdown();
    }

    private void reset() {
        semaphore.release(requestLimit);
    }

    public CompletableFuture<String> createDocument(String document, String signature) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                return sendRequest(document, signature);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                semaphore.release();
            }
        });

    }

    private String sendRequest(String document, String signature) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        DocumentRequest.builder()
                                .productDocument(document)
                                .signature(signature)
                                .build())))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    class DocumentRequest {
        private String documentFormat;
        private String productDocument;
        private String productGroup;
        private String signature;
        private String type;
    }
}
