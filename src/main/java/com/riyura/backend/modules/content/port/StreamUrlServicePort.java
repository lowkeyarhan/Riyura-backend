package com.riyura.backend.modules.content.port;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.content.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.content.dto.stream.StreamUrlResponse;
import java.util.List;
import java.util.UUID;

public interface StreamUrlServicePort {
    List<StreamUrlResponse> buildStreamUrls(StreamProviderRequest request, MediaType mediaType, UUID userId);
}
