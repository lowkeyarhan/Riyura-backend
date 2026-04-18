package com.riyura.backend.modules.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.riyura.backend.modules.content.model.StreamProvider;

import java.util.List;

@Repository
public interface StreamProviderRepository extends JpaRepository<StreamProvider, String> {

    // Returns active providers ordered by priority
    List<StreamProvider> findByIsActiveTrueOrderByPriorityAsc();
}
