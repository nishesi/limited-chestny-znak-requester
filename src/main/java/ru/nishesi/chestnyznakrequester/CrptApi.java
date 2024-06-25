package ru.nishesi.chestnyznakrequester;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CrptApi {
    private static final URI REQUEST_URL = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final Duration duration;
    private final int requestLimit;

    private final AtomicReference<Instant> lastTimeRef = new AtomicReference<>(Instant.now());
    private final AtomicLong counter = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock();

    public CrptApi(ObjectMapper objectMapper, HttpClient httpClient,
                   int requestLimit, TimeUnit timeUnit) {
        if (requestLimit <= 0)
            throw new IllegalArgumentException("requestLimit less then 1");
        this.objectMapper = objectMapper;
        this.requestLimit = requestLimit;
        this.httpClient = httpClient;

        this.duration = Duration.of(1, timeUnit.toChronoUnit());
    }

    public Response createDocument(Document document, String sign) throws CrptApiException {
        Objects.requireNonNull(document);
        Objects.requireNonNull(sign);
        try {
            synchronizeRequest();
            HttpResponse<byte[]> response = sendRequest(document, sign);
            return handleResponse(response);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void synchronizeRequest() throws InterruptedException {
        while (counter.incrementAndGet() > requestLimit) {
            Instant lastTime = lastTimeRef.get();
            if (!isNewPeriod(lastTime)) {
                waitNextPeriod(lastTime);
            }
        }
    }

    private boolean isNewPeriod(Instant lastTime) {
        lock.lock();
        if (lastTime != lastTimeRef.get()) return true;
        try {
            Instant now = Instant.now();
            if (lastTime.plus(duration).isBefore(now)) {
                counter.set(0);
                lastTimeRef.set(now);
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }

    private void waitNextPeriod(Instant lastTime) {
        if (lastTime != lastTimeRef.get()) return;
        Duration d = Duration.between(Instant.now(), lastTimeRef.get().plus(duration));
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected HttpResponse<byte[]> sendRequest(Document document,
                                             // в задании не описано, куда вставлять подпись
                                             String sign
    ) throws IOException, InterruptedException {
        byte[] body = objectMapper.writeValueAsBytes(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(REQUEST_URL)
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        return httpClient.send(request, BodyHandlers.ofByteArray());
    }

    protected Response handleResponse(HttpResponse<byte[]> response) throws IOException {
        int code = response.statusCode();
        if (200 <= code && code < 300) {
            return objectMapper.readValue(response.body(), Response.class);
        } else {
            String body = new String(response.body(), UTF_8);
            throw new CrptApiException(body);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private Description description;
        @JsonProperty("doc_id")
        private String id;
        @JsonProperty("doc_status")
        private String status;
        @JsonProperty("doc_type")
        private String type;
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<Product> products;
        @JsonProperty("reg_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate regDate;
        @JsonProperty("reg_number")
        private String regNumber;

        public record Description(String participantInn) {
        }

        @Builder
        public record Product(
                @JsonProperty("certificate_document")
                String certificateDocument,

                @JsonProperty("certificate_document_date")
                @JsonFormat(pattern = "yyyy-MM-dd")
                LocalDate certificateDocumentDate,

                @JsonProperty("certificate_document_number")
                String certificateDocumentNumber,

                @JsonProperty("owner_inn")
                String ownerInn,

                @JsonProperty("producer_inn")
                String producerInn,

                @JsonProperty("production_date")
                @JsonFormat(pattern = "yyyy-MM-dd")
                LocalDate productionDate,

                @JsonProperty("tnved_code")
                String tnvedCode,

                @JsonProperty("uit_code")
                String uitCode,

                @JsonProperty("uitu_code")
                String uituCode
        ) {
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
//    @AllArgsConstructor
    public static class Response {
        // поля ответа...
    }

    @Getter
    public static class CrptApiException extends RuntimeException {
        private final String body;

        public CrptApiException(String body) {
            this.body = body;
        }
    }
}

