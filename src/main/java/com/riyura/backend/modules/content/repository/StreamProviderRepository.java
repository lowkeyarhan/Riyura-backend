package com.riyura.backend.modules.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.riyura.backend.modules.content.dto.stream.StreamProviderResponse;

import java.util.List;

@Repository
public interface StreamProviderRepository extends JpaRepository<StreamProviderResponse, String> {

    // Returns active providers ordered by priority
    List<StreamProviderResponse> findByIsActiveTrueOrderByPriorityAsc();
}
