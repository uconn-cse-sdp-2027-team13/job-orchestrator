package edu.uconn.cse.team13.common.contract;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads input files from object storage (RustFS locally, S3 later).
 */
public interface StorageClient {

    /** Opens a file for streaming. The caller must close the stream. */
    InputStream open(String path) throws IOException;

    boolean exists(String path) throws IOException;
}