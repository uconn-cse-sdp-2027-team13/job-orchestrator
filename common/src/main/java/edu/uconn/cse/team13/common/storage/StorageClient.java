package edu.uconn.cse.team13.common.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Storage-neutral contract for job input files.
 * The producer writes a file here, the queue message carries only its path, and the workload opens the file by that path.
 * Implementations own the backend (RustFS locally, S3 later).
 */
public interface StorageClient {

    /**
     * Opens the file at {@code path} for reading. The caller must close the returned stream.
     *
     * @throws FileNotFoundException if no file exists at {@code path}
     */
    InputStream open(String path) throws IOException;

    /** Returns {@code true} if a file exists at {@code path}. */
    boolean exists(String path) throws IOException;

    /**
     * Stores everything in {@code data} at {@code path}, replacing any existing file.
     * The caller must close {@code data}; this method reads it to the end but does not close it.
     */
    void write(String path, InputStream data) throws IOException;
}
