package com.systembpm.system.modules.document.application.port.out;

import java.io.InputStream;
import java.time.Duration;

public interface DocumentStoragePort {

    void upload(String key, String contentType, long size, InputStream inputStream);

    void delete(String key);

    PresignedDownloadUrl generateDownloadUrl(String key, String downloadFileName, Duration expiration);
}
