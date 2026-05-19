package com.riyura.backend.modules.content.interfaces;

import com.riyura.backend.common.dto.media.MediaGridResponse;
import com.riyura.backend.modules.content.dto.movie.MovieDetail;
import java.util.List;

public interface MovieDetailServicePort {
    MovieDetail getMovieDetails(String id);

    List<MediaGridResponse> getSimilarMovies(String id);
}
