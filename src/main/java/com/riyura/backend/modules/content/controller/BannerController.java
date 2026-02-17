package com.riyura.backend.modules.content.controller;

import com.riyura.backend.modules.content.dto.banner.BannerResponse;
import com.riyura.backend.modules.content.service.banner.BannerService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/banner")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class BannerController {

    private final BannerService bannerService;

    // Fetch banner content (trending movies + TV shows) and return as JSON
    @GetMapping
    public ResponseEntity<Map<String, List<BannerResponse>>> getBanner() {
        List<BannerResponse> bannerItems = bannerService.getBannerData();
        return ResponseEntity.ok(Map.of("items", bannerItems));
    }
}