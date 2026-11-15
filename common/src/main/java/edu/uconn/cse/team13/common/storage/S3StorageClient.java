package edu.uconn.cse.team13.common.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

/**
 * {@link StorageClient} backed by an S3-compatible object store (RustFS locally, AWS S3 later).
 * Each {@code path} is used as the object key inside one configured bucket.
 */
public final class S3StorageClient implements StorageClient, AutoCloseable {

    private static final int NOT_FOUND = 404;

    private final S3Client s3;
    private final String bucket;
    private boolean bucketReady;

    /** Connects with the given settings. No request is sent until the first call. */
    public S3StorageClient(S3StorageConfig config) {
        this(buildClient(config), config.bucket());
    }

    /** Lets tests pass in a fake {@link S3Client} so no real server is needed. */
    S3StorageClient(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3 must not be null");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
    }

    private static S3Client buildClient(S3StorageConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                // Path style puts the bucket in the URL path (http://host:9000/bucket/key).
                // RustFS needs this; the AWS default puts the bucket in the host name instead.
                .serviceConfiguration(s3Config -> s3Config.pathStyleAccessEnabled(true))
                .build();
    }

    @Override
    public InputStream open(String path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(path).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND) {
                throw new FileNotFoundException("No file at " + path + " in bucket " + bucket);
            }
            throw new IOException("Could not open " + path + " in bucket " + bucket, ex);
        } catch (SdkException ex) {
            throw new IOException("Could not open " + path + " in bucket " + bucket, ex);
        }
    }

    @Override
    public boolean exists(String path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(path).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == NOT_FOUND) {
                return false;
            }
            throw new IOException("Could not check " + path + " in bucket " + bucket, ex);
        } catch (SdkException ex) {
            throw new IOException("Could not check " + path + " in bucket " + bucket, ex);
        }
    }

    @Override
    public void write(String path, InputStream data) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(data, "data must not be null");

        // S3 must know the size before an upload starts, and a stream cannot tell us its size,
        // so the content is read into memory first. Job input files are small enough for that;
        // much larger files would need a multipart upload instead.
        byte[] content = data.readAllBytes();
        try {
            createBucketIfMissing();
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(path).build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new IOException("Could not write " + path + " to bucket " + bucket, ex);
        }
    }

    /** Checks once that the bucket exists and creates it if the server says it does not. */
    private synchronized void createBucketIfMissing() {
        if (bucketReady) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() != NOT_FOUND) {
                throw ex;
            }
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
        bucketReady = true;
    }

    /** Releases the network connections held by the S3 client. */
    @Override
    public void close() {
        s3.close();
    }
}
