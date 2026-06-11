package com.systembpm.system.modules.document.application.port.out;

import java.time.Instant;

public record PresignedDownloadUrl(
        String url,
        Instant expiresAt
) {
}
