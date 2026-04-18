package com.riyura.backend.modules.content.port;

import com.riyura.backend.modules.content.dto.movie.MoviePlayerResponse;

public interface MoviePlayerServicePort {
    MoviePlayerResponse getMoviePlayer(String id);
}
