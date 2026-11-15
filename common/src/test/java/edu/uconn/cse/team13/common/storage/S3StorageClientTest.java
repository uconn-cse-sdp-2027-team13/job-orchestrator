package edu.uconn.cse.team13.common.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3StorageClientTest {

    private static final String BUCKET = "job-files";

    private final FakeS3 s3 = new FakeS3();
    private final S3StorageClient storage = new S3StorageClient(s3, BUCKET);

    @Test
    void writeThenOpenReturnsTheSameBytes() throws IOException {
        storage.write("payments_1.csv", stream("account_number,email\nACC-1,a@example.com\n"));

        try (InputStream opened = storage.open("payments_1.csv")) {
            assertEquals("account_number,email\nACC-1,a@example.com\n", text(opened));
        }
        assertEquals(BUCKET, s3.lastBucket);
    }

    @Test
    void writeReplacesAnExistingFile() throws IOException {
        storage.write("payments_1.csv", stream("old"));
        storage.write("payments_1.csv", stream("new"));

        try (InputStream opened = storage.open("payments_1.csv")) {
            assertEquals("new", text(opened));
        }
    }

    @Test
    void writeLeavesTheCallersStreamOpen() throws IOException {
        CloseTrackingStream data = new CloseTrackingStream("rows");

        storage.write("payments_1.csv", data);

        assertFalse(data.closed);
    }

    @Test
    void writeCreatesTheBucketOnlyWhenItIsMissing() throws IOException {
        s3.bucketExists = false;

        storage.write("payments_1.csv", stream("a"));
        storage.write("payments_2.csv", stream("b"));

        assertEquals(1, s3.createBucketCalls);
        assertTrue(storage.exists("payments_2.csv"));
    }

    @Test
    void writeDoesNotCreateABucketThatAlreadyExists() throws IOException {
        s3.bucketExists = true;

        storage.write("payments_1.csv", stream("a"));

        assertEquals(0, s3.createBucketCalls);
    }

    @Test
    void existsIsTrueOnlyForStoredFiles() throws IOException {
        storage.write("payments_1.csv", stream("a"));

        assertTrue(storage.exists("payments_1.csv"));
        assertFalse(storage.exists("missing.csv"));
    }

    @Test
    void openMissingFileThrowsFileNotFound() {
        assertThrows(FileNotFoundException.class, () -> storage.open("missing.csv"));
    }

    @Test
    void serverErrorsBecomeIOExceptionNotFileNotFound() {
        s3.failEveryCallWithStatus = 500;

        IOException onOpen = assertThrows(IOException.class, () -> storage.open("payments_1.csv"));
        assertFalse(onOpen instanceof FileNotFoundException);
        assertThrows(IOException.class, () -> storage.exists("payments_1.csv"));
        assertThrows(IOException.class, () -> storage.write("payments_1.csv", stream("a")));
    }

    @Test
    void closeClosesTheS3Client() {
        storage.close();

        assertTrue(s3.closed);
    }

    @Test
    void configReadsAllFiveVariables() {
        S3StorageConfig config = S3StorageConfig.fromEnv(Map.of(
                "S3_ENDPOINT", "http://localhost:9000",
                "S3_BUCKET", "job-files",
                "S3_ACCESS_KEY", "local-rustfs",
                "S3_SECRET_KEY", "local-rustfs-password",
                "S3_REGION", "us-east-1"));

        assertEquals("http://localhost:9000", config.endpoint());
        assertEquals("job-files", config.bucket());
        assertEquals("local-rustfs", config.accessKey());
        assertEquals("local-rustfs-password", config.secretKey());
        assertEquals("us-east-1", config.region());
    }

    @Test
    void configRejectsAMissingVariableByName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> S3StorageConfig.fromEnv(Map.of(
                        "S3_ENDPOINT", "http://localhost:9000",
                        "S3_ACCESS_KEY", "local-rustfs",
                        "S3_SECRET_KEY", "local-rustfs-password",
                        "S3_REGION", "us-east-1")));

        assertTrue(error.getMessage().contains("S3_BUCKET"));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Records whether {@code close()} was called, to check that write() leaves the stream to its caller. */
    private static final class CloseTrackingStream extends ByteArrayInputStream {
        boolean closed;

        CloseTrackingStream(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /**
     * Stands in for a real S3 server: keeps one bucket's objects in a map and answers the five
     * calls S3StorageClient makes. Missing things fail with HTTP 404, the same status real S3 uses.
     */
    private static final class FakeS3 implements S3Client {
        final Map<String, byte[]> objects = new HashMap<>();
        boolean bucketExists = true;
        int createBucketCalls;
        int failEveryCallWithStatus;
        String lastBucket;
        boolean closed;

        @Override
        public HeadBucketResponse headBucket(HeadBucketRequest request) {
            failIfConfigured();
            if (!bucketExists) {
                throw s3Error(404);
            }
            return HeadBucketResponse.builder().build();
        }

        @Override
        public CreateBucketResponse createBucket(CreateBucketRequest request) {
            failIfConfigured();
            createBucketCalls++;
            bucketExists = true;
            return CreateBucketResponse.builder().build();
        }

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            failIfConfigured();
            if (!bucketExists) {
                throw s3Error(404);
            }
            try (InputStream content = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), content.readAllBytes());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            lastBucket = request.bucket();
            return PutObjectResponse.builder().build();
        }

        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            failIfConfigured();
            byte[] content = objects.get(request.key());
            if (content == null) {
                throw s3Error(404);
            }
            return new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    AbortableInputStream.create(new ByteArrayInputStream(content)));
        }

        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            failIfConfigured();
            if (!objects.containsKey(request.key())) {
                throw s3Error(404);
            }
            return HeadObjectResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {
            closed = true;
        }

        private void failIfConfigured() {
            if (failEveryCallWithStatus != 0) {
                throw s3Error(failEveryCallWithStatus);
            }
        }

        private static RuntimeException s3Error(int statusCode) {
            return S3Exception.builder().statusCode(statusCode).message("HTTP " + statusCode).build();
        }
    }
}
