package com.riyura.backend.modules.watchalong.interfaces;

import com.riyura.backend.modules.watchalong.dto.movie.MoviePlayerResponse;

public interface MoviePlayerServicePort {
    MoviePlayerResponse getMoviePlayer(String id);
}
