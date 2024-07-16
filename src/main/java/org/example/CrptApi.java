package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class CrptApi {
    private static final String BASE_URL = "https://ismp.crpt.ru";
    private static final String DOCUMENT_FORMAT = "MANUAL";

    private final ObjectMapper objectMapper;
    private final Retrofit retrofit;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private final int requestLimit;
    private final AtomicInteger requestCount;
    private final long timeIntervalMillis;
    private volatile long lastResetTime;

    public CrptApi(TimeUnit timeInterval, int requestsLimit) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        this.retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        this.requestLimit = requestsLimit;
        this.requestCount = new AtomicInteger(0);

        this.timeIntervalMillis = timeInterval.toMillis(1);
        this.lastResetTime = System.currentTimeMillis();
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            System.err.println("Пожалуйста, предоставьте JSON-документ для запроса в виде аргумента к программе.");
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            String jsonDocument = args[0];
            Document document = objectMapper.readValue(jsonDocument, Document.class);
            CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);

            Response<String> response = crptApi.crtpCreateDocument(document, "signature");
            System.out.println(response);
        } catch (IOException e) {
            System.err.println("Произошла ошибка во время запроса: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    public Response<String> crtpCreateDocument(Document document, String signature) throws IOException, InterruptedException, URISyntaxException {
        acquirePermit();

        CreationDocumentRequest creationDocumentRequest = new CreationDocumentRequest();
        String documentBody = objectMapper.writeValueAsString(document);

        creationDocumentRequest.setDocument(Base64.getEncoder().encodeToString(documentBody.getBytes()));
        creationDocumentRequest.setSignature(Base64.getEncoder().encodeToString(signature.getBytes()));
        creationDocumentRequest.setType(document.getDocType());

        CreateDocument createDocument = retrofit.create(CreateDocument.class);

        return createDocument.sendCreateDocumentRequest(creationDocumentRequest).execute();
    }

    private void acquirePermit() throws InterruptedException {
        lock.lock();
        try {
            while (true) {
                long currentTime = System.currentTimeMillis();
                long resetInterval = currentTime - lastResetTime;

                if (resetInterval >= timeIntervalMillis) {
                    requestCount.set(0);
                    lastResetTime = currentTime;
                }

                if (requestCount.get() < requestLimit) {
                    requestCount.incrementAndGet();
                    break;
                } else {
                    long waitTime = timeIntervalMillis - resetInterval;
                    if (waitTime > 0) {
                        condition.await(waitTime, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }


    public interface CreateDocument {
        @POST("api/v3/lk/documents/create")
        Call<String> sendCreateDocumentRequest(@Body CreationDocumentRequest requestBody);
    }

    public static class CreationDocumentRequest {
        @JsonProperty("document_format")
        private static final String documentFormat = DOCUMENT_FORMAT;
        @JsonProperty("product_document")
        private String document;
        @JsonProperty("signature")
        private String signature;
        @JsonProperty("type")
        private String type;

        public CreationDocumentRequest() {
        }

        public CreationDocumentRequest(String document, String signature, String type) {
            this.document = document;
            this.signature = signature;
            this.type = type;
        }

        public String getDocument() {
            return document;
        }

        public void setDocument(String document) {
            this.document = document;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Document {
        @JsonProperty("description")
        private Description description;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;
        @JsonProperty("doc_type")
        private String docType;
        @JsonProperty("importRequest")
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        @JsonProperty("products")
        private List<Product> products;
        @JsonProperty("reg_date")
        private LocalDate regDate;
        @JsonProperty("reg_number")
        private String regNumber;

        public static class Description {
            @JsonProperty("participantInn")
            private String participantInn;
        }

        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;
            @JsonProperty("certificate_document_date")
            private LocalDate certificateDocumentDate;
            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;
            @JsonProperty("owner_inn")
            private String ownerInn;
            @JsonProperty("producer_inn")
            private String producerInn;
            @JsonProperty("production_date")
            private LocalDate productionDate;
            @JsonProperty("tnved_code")
            private String tnvedCode;
            @JsonProperty("uit_code")
            private String uitCode;
            @JsonProperty("uitu_code")
            private String uituCode;
        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public boolean isImportRequest() {
            return importRequest;
        }

        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<Product> getProducts() {
            return products;
        }

        public void setProducts(List<Product> products) {
            this.products = products;
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }
    }
}