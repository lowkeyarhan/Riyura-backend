package com.riyura.backend.modules.watchalong.controller;

import com.riyura.backend.common.model.MediaType;
import com.riyura.backend.modules.watchalong.dto.movie.MoviePlayerResponse;
import com.riyura.backend.modules.watchalong.dto.stream.StreamProviderRequest;
import com.riyura.backend.modules.watchalong.dto.stream.StreamUrlResponse;
import com.riyura.backend.modules.watchalong.dto.tv.TvPlayerResponse;
import com.riyura.backend.modules.watchalong.port.MoviePlayerServicePort;
import com.riyura.backend.modules.watchalong.port.StreamUrlServicePort;
import com.riyura.backend.modules.watchalong.port.TvPlayerServicePort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
public class WatchalongController {

    private final MoviePlayerServicePort moviePlayerService;
    private final TvPlayerServicePort tvPlayerService;
    private final StreamUrlServicePort streamUrlService;

    @GetMapping({ "/api/movies/player/{id}" })
    public ResponseEntity<MoviePlayerResponse> getMoviePlayer(@PathVariable Long id) {
        MoviePlayerResponse playerResponse = moviePlayerService.getMoviePlayer(String.valueOf(id));
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }

    @GetMapping({ "/api/tv/player/{id}" })
    public ResponseEntity<TvPlayerResponse> getTvPlayer(@PathVariable Long id) {
        TvPlayerResponse playerResponse = tvPlayerService.getTvPlayer(String.valueOf(id));
        if (playerResponse == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerResponse);
    }

    @PostMapping({ "/api/movies/stream" })
    public ResponseEntity<List<StreamUrlResponse>> getMovieStream(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StreamProviderRequest request) {
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.Movie, resolveUserId(jwt)));
    }

    @PostMapping({ "/api/tv/stream" })
    public ResponseEntity<List<StreamUrlResponse>> getTvStream(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StreamProviderRequest request) {
        return ResponseEntity.ok(streamUrlService.buildStreamUrls(request, MediaType.TV, resolveUserId(jwt)));
    }

    private UUID resolveUserId(Jwt jwt) {
        return jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    }
}
