package com.riyura.backend.modules.content.port;

import com.riyura.backend.modules.content.dto.search.SearchResponse;
import com.riyura.backend.modules.content.dto.search.SearchSortOrder;
import java.util.List;

public interface SearchServicePort {
    List<SearchResponse> search(String query, int page, SearchSortOrder sortOrder);
}
