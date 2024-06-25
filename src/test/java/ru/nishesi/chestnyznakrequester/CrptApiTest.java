package ru.nishesi.chestnyznakrequester;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrptApiTest {
    CrptApi crptApi;
    ObjectMapper objectMapper;
    HttpClient httpClient;


    @BeforeEach
    void init() {
        objectMapper = Mockito.mock(ObjectMapper.class);
        httpClient = Mockito.mock(HttpClient.class);
        crptApi = new CrptApi(objectMapper, httpClient, 5, TimeUnit.SECONDS);
    }

    @Test
    void should_block_excess_requests_in_period() throws IOException, InterruptedException {
        when(httpClient.send(any(), any())).thenReturn(new TestHttpResponse());
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 6; i++) {
            executorService.submit(() ->
                    crptApi.createDocument(CrptApi.Document.builder().build(), "sign")
            );
        }
        TimeUnit.MILLISECONDS.sleep(500);
        verify(httpClient, times(5)).send(any(), any());
    }

    @Test
    void should_complete_all_requests() throws IOException, InterruptedException, ExecutionException {
        when(httpClient.send(any(), any())).thenReturn(new TestHttpResponse());
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<?> future = executorService.submit(() ->
                    crptApi.createDocument(CrptApi.Document.builder().build(), "sign")
            );
            futures.add(future);
        }
        for (Future<?> future : futures) {
            future.get();
        }
        futures.forEach(future -> {
            assertThat(future)
                    .isDone();
            assertThatNoException()
                    .isThrownBy(future::get);
        });
    }

    @Test
    void should_not_be_deadlocks() throws IOException, InterruptedException {
        when(httpClient.send(any(), any())).thenReturn(new TestHttpResponse());
        when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Future<?> future = executorService.submit(() ->
                    crptApi.createDocument(CrptApi.Document.builder().build(), "sign")
            );
            futures.add(future);
        }

        futures.forEach(future -> {
            try {
                future.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                throw new AssertionError("deadlock found");
            }
        });
    }

    @Test
    void should_generate_correct_request_body() throws IOException, InterruptedException {
        var product = CrptApi.Document.Product.builder()
                .certificateDocument("certificate_document_10")
                .certificateDocumentDate(LocalDate.of(2024, 1, 26))
                .certificateDocumentNumber("certificate_document_number_11")
                .ownerInn("owner_inn_12")
                .producerInn("producer_inn_13")
                .productionDate(LocalDate.of(2024, 1, 27))
                .tnvedCode("tnved_code_14")
                .uitCode("uit_code_15")
                .uituCode("uitu_code_16")
                .build();

        CrptApi.Document document = CrptApi.Document.builder()
                .description(new CrptApi.Document.Description("description_1"))
                .id("id_2")
                .status("status_3")
                .type("type_4")
                .importRequest(true)
                .ownerInn("owner_inn_5")
                .participantInn("participant_inn_6")
                .producerInn("producer_inn_7")
                .productionDate(LocalDate.of(2024, 1, 24))
                .productionType("production_type_8")
                .products(List.of(product))
                .regDate(LocalDate.of(2024, 1, 25))
                .regNumber("reg_number_9")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        byte[] arr = mapper.writeValueAsBytes(document);
        Map<String, Object> result = mapper.readValue(arr, new TypeReference<>() {
        });

        assertThat(result)
                .hasFieldOrPropertyWithValue("doc_id", document.getId())
                .hasFieldOrPropertyWithValue("doc_status", document.getStatus())
                .hasFieldOrPropertyWithValue("doc_type", document.getType())
                .hasFieldOrPropertyWithValue("importRequest", document.isImportRequest())
                .hasFieldOrPropertyWithValue("owner_inn", document.getOwnerInn())
                .hasFieldOrPropertyWithValue("participant_inn", document.getParticipantInn())
                .hasFieldOrPropertyWithValue("producer_inn", document.getProducerInn())
                .hasFieldOrPropertyWithValue("production_date", "2024-01-24")
                .hasFieldOrPropertyWithValue("production_type", document.getProductionType())
                .hasFieldOrPropertyWithValue("reg_date", "2024-01-25")
                .hasFieldOrPropertyWithValue("reg_number", document.getRegNumber());

        assertThat(result)
                .extracting("description")
                .hasFieldOrPropertyWithValue("participantInn", document.getDescription().participantInn());

        var resProduct = ((List<Map<String, Object>>)result.get("products")).get(0);
        assertThat(resProduct)
                .hasFieldOrPropertyWithValue("certificate_document", product.certificateDocument())
                .hasFieldOrPropertyWithValue("certificate_document_date", "2024-01-26")
                .hasFieldOrPropertyWithValue("certificate_document_number", product.certificateDocumentNumber())
                .hasFieldOrPropertyWithValue("owner_inn", product.ownerInn())
                .hasFieldOrPropertyWithValue("producer_inn", product.producerInn())
                .hasFieldOrPropertyWithValue("production_date", "2024-01-27")
                .hasFieldOrPropertyWithValue("tnved_code", product.tnvedCode())
                .hasFieldOrPropertyWithValue("uit_code", product.uitCode())
                .hasFieldOrPropertyWithValue("uitu_code", product.uituCode());
    }

    static class TestHttpResponse implements HttpResponse<Object> {
        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<Object>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return null;
        }

        @Override
        public byte[] body() {
            return new byte[0];
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return null;
        }
    }
}