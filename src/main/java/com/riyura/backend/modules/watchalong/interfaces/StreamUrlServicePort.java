package com.riyura.backend.modules.watchalong.interfaces;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.watchalong.dto.stream.StreamUrlResponse;
import java.util.List;
import java.util.UUID;

public interface StreamUrlServicePort {
    List<StreamUrlResponse> buildStreamUrls(StreamProviderRequest request, MediaType mediaType, UUID userId);
}
