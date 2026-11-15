package edu.uconn.cse.team13.producer;

import edu.uconn.cse.team13.common.storage.StorageClient;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Test stand-in for object storage: keeps written files in a map so no server is needed. */
class InMemoryStorageClient implements StorageClient {

    private final Map<String, byte[]> files = new HashMap<>();

    @Override
    public InputStream open(String path) throws IOException {
        byte[] content = files.get(path);
        if (content == null) {
            throw new FileNotFoundException("No file at " + path);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public boolean exists(String path) {
        return files.containsKey(path);
    }

    @Override
    public void write(String path, InputStream data) throws IOException {
        files.put(path, data.readAllBytes());
    }
}
