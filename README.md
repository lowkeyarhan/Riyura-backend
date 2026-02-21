# Riyura Backend

## Overview & About

Riyura is a sophisticated backend service that acts as the core orchestration layer for a modern media streaming platform. Designed to seamlessly serve rich media content, dynamic discovery features, and personalized user experiences, the system ensures high reliability and data integrity. From managing real-time trending banners to personalized watchlists, Riyura powers the essential features required to run a comprehensive entertainment hub.

## System Architecture

The application follows a **Modular Monolith** architecture that emphasizes high cohesion and loose coupling. By organizing the internal structure into distinct, feature-driven modules, the platform remains highly maintainable, testable, and primed for a seamless transition to microservices if future scale demands it.

**Core Modules:**

- **Content Module**: The central discovery engine that processes, filters, and paginates diverse media types (Movies, TV Shows, Anime). It also dynamically serves high-priority banners and search functionalities.
- **Identity Module**: Handles protected user contexts. This module tracks personalized interactions such as Watchlists and user Profiles, acting as the secure gateway between public media and private user data.

## Tech Stack & Tools

- **Framework**: Spring Boot 4.x
- **Language**: Java 21
- **Database**: PostgreSQL
- **Security**: Spring Security with OAuth2 Resource Server (Supabase JWT integration)
- **Persistence**: Spring Data JPA with Hibernate
- **API Documentation**: SpringDoc OpenAPI (Swagger UI)
- **Core Utilities**: Lombok, Maven

## Database Design & Optimization

Riyura utilizes a robust relational database schema managed by PostgreSQL.

- **Entity Management**: Spring Data JPA with Hibernate acts as the ORM, allowing complex relational mappings while minimizing boilerplate code. The system uses auto-DDL updates during the development lifecycle for rapid iteration, leveraging the PostgreSQL-specific dialect for query tuning.
- **Connection Optimization**: Database connections are efficiently managed using **HikariCP**. The connection pool is carefully optimized (maximum pool size: 15, optimal minimum idle connections, and explicit idle/lifetime timeouts) to ensure maximum throughput and minimal latency during traffic spikes.
- **Data Integrity**: Enforced via strict object validations and optimized table structures tailored for read-intensive workflows (like content fetching) as well as transactional workflows (like watchlist updates).

## Performance & Scalability

- **Stateless Authentication**: By relying on JWT assertions securely validated via Supabase, the backend remains completely stateless. This effectively enables horizontal scaling without the overhead of distributed session management.
- **Connection Pooling**: Pre-warmed database connections keep query latency exceptionally low, actively preventing connection bottlenecks during concurrent user requests.
- **Optimized Content Delivery**: Dedicated RESTful endpoints are strictly grouped (e.g., `/api/movies/**`, `/api/tv/**`, `/api/anime/**`) and tailored to assemble precise JSON payloads, fundamentally reducing over-fetching of data.

## Key Workflows

- **Dynamic Content Discovery**: Clients can request categorized media—such as "Trending", "Popular", "Now Playing", or "Airing Today". The centralized `Content` module orchestrates fetching, standardizing, and assembling this data before delivery.
- **Anime Detection & Delivery**: A specialized workflow intelligently filters Japanese animation across both movie and TV show formats, subsequently delivering a unified anime discovery experience.
- **Streaming Orchestration**: Responsible for retrieving and securely aggregating available stream URLs for specified content, seamlessly adapting parsing logic based on media formatting constraints (e.g., differentiating between standard movies and episodic TV structures).
- **Personalized Watchlists**: Authenticated users can securely persist viewing preferences, manage watchlists, and retrieve profile-specific metrics. This data is rigorously protected and bound strictly to their uniquely authenticated identity token.

## Security & Authentication

Endpoints are categorized based on access requirements:

- **Public Edges**: Categories like `/api/banner/**`, `/api/movies/**`, `/api/tv/**`, `/api/anime/**`, and `/api/search/**` are inherently open for seamless discovery by unauthenticated clients.
- **Protected Edges**: Personal endpoints targeting `/api/profile/**` and `/api/watchlist/**` strictly require a valid Supabase-issued JWT passed seamlessly via the `Authorization` header.

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL instance running
- Environment variables configured (Database credentials, JWT Secrets, etc.)

### Installation

1. **Clone the repository** to your local environment.
2. **Configure Environment Variables**: Ensure you have defined the necessary environment variables explicitly inside a local `.env` file or export them to your system (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SUPABASE_JWT_SECRET`, etc.).
3. **Build the application** using the Maven wrapper:
   ```bash
   ./mvnw clean install
   ```
4. **Run the backend**:
   ```bash
   ./mvnw spring-boot:run
   ```
5. **API Exploration**: Once the application is running, seamlessly navigate to `http://localhost:8080/swagger-ui.html` via your browser to explore the OpenAPI documentation and test the available endpoints directly.
