# Riyura Backend

## Overview
Riyura is a comprehensive backend service designed for a modern media streaming platform. It acts as an orchestrator between external media metadata providers and the platform's frontend, while managing user-specific features such as watch history and secure authentication.
## Tech Stack
* **Framework**: Spring Boot 4.0.2
* **Language**: Java 21
* **Database**: PostgreSQL
* **Security**: Spring Security with OAuth2 Resource Server (Supabase JWT integration)
* **Persistence**: Spring Data JPA with Hibernate
* **Documentation**: SpringDoc OpenAPI (Swagger UI)
* **Utilities**: Lombok, RestTemplate

## Architecture
The project follows a **Modular Monolith** architecture. Code is organized into feature-based modules within the `com.riyura.backend.modules` package. This ensures high cohesion and makes the system easier to transition into microservices if needed.

## Module Breakdown
* **Banner Module**: Fetches and shuffles trending movies and TV shows to provide dynamic content for the home screen banner.
* **Movie Module**: Provides dedicated endpoints for "Now Playing," "Trending," "Popular," and "Upcoming" movies.
* **TV Module**: Manages TV-specific discovery, including "Airing Today" and "On The Air" shows.
* **Anime Module**: A specialized discovery engine that filters TMDB data for Japanese animation across both movies and TV formats.
* **Profile Module**: Handles protected user data, specifically the "Watch History" feature which tracks user progress and specific episode metadata for TV shows.

## Configuration
The application requires several environment variables for sensitive credentials, as defined in `application.yaml`:

### Database (PostgreSQL)
* `DB_URL`: The JDBC connection string for the database.
* `DB_USERNAME`: Database user.
* `DB_PASSWORD`: Database password.

### External APIs (TMDB)
* `TMDB_API_KEY`: The Movie Database API key.
* `TMDB_BASE_URL`: The base URL for TMDB API calls.
* `TMDB_IMAGE_BASE_URL`: Base URL for fetching media posters and backdrops.

### Security (Supabase)
* `SUPABASE_JWT_SECRET`: The secret key used to validate JWT tokens issued by Supabase.

## Development Setup
1.  **Clone the repository**.
2.  **Configure Environment Variables**: Ensure all variables listed in the Configuration section are set in your local environment.
3.  **Build the project**: Use the included Maven wrapper:
    ```bash
    ./mvnw clean install
    ```
4.  **Run the application**:
    ```bash
    ./mvnw spring-boot:run
    ```
5.  **API Documentation**: Access the Swagger UI at `http://localhost:8080/swagger-ui.html` to explore the endpoints.

## Security & Authentication
Endpoints are split into two categories in `SecurityConfig.java`:
* **Public**: `/api/banner/**`, `/api/movies/**`, `/api/tv/**`, and `/api/anime/**` are accessible without a token.
* **Protected**: `/api/profile/**` and `/api/watch-history/**` require a valid Supabase JWT passed in the Authorization header.
