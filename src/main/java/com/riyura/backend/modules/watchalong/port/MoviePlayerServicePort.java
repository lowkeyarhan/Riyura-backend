package com.riyura.backend.modules.watchalong.port;

import com.riyura.backend.modules.watchalong.dto.movie.MoviePlayerResponse;

public interface MoviePlayerServicePort {
    MoviePlayerResponse getMoviePlayer(String id);
}
