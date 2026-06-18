# PlayerHive

> **"For gamers, by gamers"** — a social gaming platform for discovering games, tracking your library, reviewing titles, and connecting with other players.

PlayerHive is the final project developed for the "Large-Scale and Multi-Structured Databases" exam at the University of Pisa (Master's Degree in Computer Engineering, Academic Year 2025/2026).

Built with Spring Boot 3.5 and a polyglot persistence architecture combining MongoDB and Neo4j.

---

## Table of Contents

* [About the Project](#about-the-project)
* [Tech Stack](#tech-stack)
* [Project Structure](#project-structure)
* [Prerequisites](#prerequisites)
* [Configuration](#configuration)
* [Running the Project](#running-the-project)
* [API Documentation](#api-documentation)
* [Authentication](#authentication)
* [API Endpoints](#api-endpoints)
* [Database Design](#database-design)
* [Notable Queries](#notable-queries)
* [Authors](#authors)

---

## About the Project

PlayerHive is a REST API backend for a social gaming platform. Users can browse a game catalogue, maintain a personal game library, write reviews, and build a friend network.

Three roles are supported:

| Role | Capabilities |
| --- | --- |
| **Unregistered** | Browse catalogue, read reviews, view public profiles and libraries |
| **Registered** | All of the above + manage own library, write/delete reviews, send/accept friend requests |
| **Admin** | All of the above + add/edit/delete games, delete any user account, access analytics |

The platform is built for scale. The dataset contains 15,002 users, 121,095 games, 303,067 reviews, and a dense social graph (over 300,000 friendships and library relationships) — all loaded via a dedicated population script.

---

## Tech Stack

| Layer | Technology | Version |
| --- | --- | --- |
| Framework | Spring Boot | 3.5 |
| Language | Java | 17 |
| Primary DB | MongoDB | — |
| Graph DB | Neo4j | — |
| Auth | Spring Security + JJWT | 0.11.5 |
| API Docs | SpringDoc OpenAPI (Swagger UI) | — |
| Object mapping | MapStruct | 1.6.3 |
| Boilerplate | Lombok | 1.18.38 |
| Build tool | Maven | — |

### Why three databases?

* **MongoDB** — document store for users, games, and reviews. Flexible schema, fast single-document reads, supports complex aggregation pipelines for analytics.
* **Neo4j** — graph database for friendships and game library ownership. Graph traversal (friends-of-friends, library lookups, recommendation algorithms) is orders of magnitude faster here than in a document store.

---

## Project Structure

```
PlayerHive/
└── src/main/java/com/unipi/PlayerHive/
    ├── controller/          # HTTP route handlers
    │   ├── AuthController.java
    │   ├── GameController.java
    │   ├── UserController.java
    │   └── AdminController.java
    ├── service/             # Business logic
    │   ├── AuthService.java
    │   ├── GameService.java
    │   ├── UserService.java
    │   ├── AdminService.java
    │   └── MyUserDetailsService.java
    ├── repository/          # Database query interfaces
    │   ├── games/
    │   │   ├── GameRepository.java        # MongoDB
    │   │   └── GameNeo4jRepository.java   # Neo4j
    │   ├── users/
    │   │   ├── UserRepository.java        # MongoDB
    │   │   └── UserNeo4jRepository.java   # Neo4j
    │   └── reviews/
    │       └── ReviewRepository.java      # MongoDB
    ├── model/               # Database entity classes
    │   ├── Game.java          # MongoDB document
    │   ├── GameNeo4j.java     # Neo4j node
    │   ├── User.java          # MongoDB document
    │   ├── UserNeo4j.java     # Neo4j node
    │   ├── UserPrincipal.java # Spring Security wrapper
    │   └── Review.java        # MongoDB document
    ├── DTO/                 # Request/response data shapes
    │   ├── analytics/         # Analytics response DTOs
    │   ├── containers/
    │   ├── games/
    │   ├── reviews/
    │   └── users/
    ├── config/              # App configuration
    │   ├── SecuritySpringBoot.java  # Route permissions
    │   ├── JwtFilter.java           # JWT request interceptor
    │   ├── JwtUtils.java            # Token generation/validation
    │   ├── SwaggerConfig.java       # API docs setup
    │   └── Exceptions/
    │       └── GlobalExceptionHandler.java
    └── utility/             # Helpers
        ├── map/             # MapStruct mapper interfaces
        └── batch/           # Bulk DB operation utilities

```

---

## Prerequisites

Make sure the following are installed and running before starting the application:

| Requirement | Notes |
| --- | --- |
| Java 17+ | `java -version` to check |
| Maven | Comes bundled via `./mvnw` — no install needed |
| MongoDB | Running on `localhost:27017` |
| Neo4j | Running on `localhost:7687` |

**Start MongoDB** (if installed locally):

```bash
mongod --dbpath /usr/local/var/mongodb

```

**Start Neo4j** (if installed locally):

```bash
neo4j start

```

Or use Docker to spin up both at once:

```bash
docker run -d -p 27017:27017 mongo
docker run -d -p 7687:7687 -p 7474:7474 -e NEO4J_AUTH=neo4j/00000000 neo4j

```

---

## Configuration

All connection settings are in:

```
PlayerHive/src/main/resources/application.properties

```

```properties
# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/PlayerHive

# Neo4j
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=00000000

```

Change the Neo4j password to match your local installation if it differs.

---

## Running the Project

From the `PlayerHive/` directory:

```bash
cd PlayerHive
./mvnw spring-boot:run

```

On Windows:

```bash
mvnw.cmd spring-boot:run

```

The application starts on **`http://localhost:8080`**.

If you get a port conflict:

```bash
# find the process
lsof -i :8080
# kill it
kill -9 <PID>

```

To build a JAR and run it:

```bash
./mvnw clean package -DskipTests
java -jar target/PlayerHive-0.0.1-SNAPSHOT.jar

```

---

## API Documentation

Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html

```

The OpenAPI JSON spec is at:

```
http://localhost:8080/v3/api-docs

```

Swagger is the easiest way to explore and test all endpoints interactively. It is publicly accessible — no token required to view it.

---

## Authentication

PlayerHive uses stateless JWT authentication.

### Register

```
POST /api/auth/register

```

```json
{
  "username": "yourname",
  "email": "you@example.com",
  "password": "yourpassword",
  "birthDate": "1998-06-14"
}

```

### Login

```
POST /api/auth/login

```

```json
{
  "email": "you@example.com",
  "password": "yourpassword"
}

```

Returns:

```json
{
  "token": "eyJhbGci..."
}

```

### Using the token

Include it in every authenticated request as a header:

```
Authorization: Bearer eyJhbGci...

```

In Swagger: click the **Authorize** button at the top right, enter `Bearer <your_token>`.

Tokens are valid for **2 hours**.

---

## API Endpoints

### Auth — `/api/auth` (public)

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive a JWT token |

### Games — `/api/games`

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/games/{gameId}` | No | Get full game details |
| GET | `/api/games/search/{gameName}` | No | Search games by name (paginated) |
| GET | `/api/games/reviews/{gameId}` | No | Get paginated reviews for a game |
| POST | `/api/games/addReview/{gameId}` | Yes | Submit a review |
| DELETE | `/api/games/deleteReview/{reviewId}` | Yes | Delete a review (own or admin) |
| GET | `/api/games/getTopGames` | No | Top rated games meeting community thresholds |
| GET | `/api/games/getDeals` | No | Best rating-to-price ratio games |
| GET | `/api/games/getInvestments` | No | Best playtime-to-price ratio games |
| GET | `/api/games/getDiscussed` | No | Trending games based on recent review density |
| GET | `/api/games/getNewReleases` | No | Latest catalogue additions |
| GET | `/api/games/getRecommendations` | Yes | Personalized suggestions based on friend activity |
| GET | `/api/games/getHiddenGems` | Yes | Obscure titles popular within the user's friend network |
| GET | `/api/games/{gameId}/getRelatedGames` | No | Collaborative filtering suggesting frequently co-played titles |

### Users — `/api/user`

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/api/user/{userId}` | No | Get a user's public profile |
| GET | `/api/user/MyProfile` | Yes | Get own profile with pending friend request count |
| GET | `/api/user/search/{query}` | No | Search users by username (paginated) |
| GET | `/api/user/library/{userId}` | No | Get a user's game library (paginated) |
| GET | `/api/user/MyLibrary` | Yes | Get own library (paginated) |
| POST | `/api/user/editLibrary` | Yes | Add or update a game in own library |
| DELETE | `/api/user/removeFromLibrary/{gameId}` | Yes | Remove a game from own library |
| GET | `/api/user/friends/{userId}` | No | Get a user's friends list (paginated) |
| GET | `/api/user/MyFriends` | Yes | Get own friends list (paginated) |
| GET | `/api/user/friendRequests` | Yes | Get pending incoming friend requests |
| POST | `/api/user/sendFriendRequest/{targetUserId}` | Yes | Send a friend request |
| POST | `/api/user/approveFriendRequest/{targetUserId}` | Yes | Accept a friend request |
| DELETE | `/api/user/denyFriendRequest/{targetUserId}` | Yes | Deny a friend request |
| DELETE | `/api/user/removeFriend/{friendId}` | Yes | Remove a friend |
| GET | `/api/user/reviews/{userId}` | No | Get a user's review history (paginated) |
| GET | `/api/user/MyReviews` | Yes | Get own review history (paginated) |
| DELETE | `/api/user/deleteAccount` | Yes | Delete own account (cascading) |
| GET | `/api/user/getHardcoreGamers` | No | Ranking tracking players with massive libraries and high playtime |
| GET | `/api/user/getKeyboardWarriors` | No | Ranking highlighting users with high review-to-game ratios |
| GET | `/api/user/getMostActiveGamers` | No | Ranking finding players with the highest daily game acquisition rate |
| GET | `/api/user/friendRecommendations` | Yes | Suggests new connections using triadic closure (mutual friends) |
| GET | `/api/user/gamingTwins` | Yes | Discovers highly similar users based on shared libraries |

### Admin — `/api/admin` (ADMIN role required)

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/admin/addGame` | Add a new game to both MongoDB and Neo4j catalogues |
| PATCH | `/api/admin/editGame/{gameId}` | Update metadata fields of an existing game |
| DELETE | `/api/admin/deleteGame/{gameId}` | Permanently remove a game, cascading deletions |
| DELETE | `/api/admin/deleteUser/{userId}` | Forcefully delete a user account and associated data |
| GET | `/api/admin/getTrending` | Retrieve games currently trending among social friend clusters |
| GET | `/api/admin/getGenreStats` | Average ratings and playtime grouped by genre |
| GET | `/api/admin/getOsPlatformStats` | Metrics evaluated by OS compatibility scope |
| GET | `/api/admin/releaseYearStats` | Historical platform trends grouped by release year |

---

## Database Design

### MongoDB Collections

**`users`** — user profiles and aggregated stats

* Embedded `friendRequests` array (pending requests with sender info)
* Embedded `reviewIds` array (lightweight `{reviewId, gameId}` references for cascading deletes)

**`games`** — full game catalogue

* `recentReviews` — embeds the 25 most recent full review objects for fast page rendering
* `allReviews` — full list of lightweight `{reviewId}` references for paginated browsing
* `sumScore` / `countScore` — running totals that allow `avgScore = sumScore / countScore` without re-aggregating
* `totalHoursPlayed` / `numPlayers` — running totals for average playtime

**`reviews`** — one document per review, with denormalised `username` and `pfpURL` to optimize read operations and prevent additional lookups.

### Neo4j Graph

**Nodes:** `:User {id, username, pfpURL}` and `:Game {id, name, achievements, image}`

**Relationships:**

* `(:User)-[:FRIENDS_WITH]->(:User)` — bidirectional friendship paths
* `(:User)-[:PLAYED {hoursPlayed, achievements}]->(:Game)` — library ownership with custom metrics

Node `id` fields map 1:1 to MongoDB `_id` values. Inter-database consistency is managed at the application level via the `@Transactional` annotation.

---

## Notable Queries

### Analytics (MongoDB)

**Hardcore Gamers** — `GET /api/user/getHardcoreGamers`
Advanced aggregation pipeline tracking top gamers based on minimum game counts and total playtime, sorted by average hours per game.

**Keyboard Warriors** — `GET /api/user/getKeyboardWarriors`
Computes the ratio of reviews to owned games to find highly expressive community contributors.

**Most Discussed** — `GET /api/games/getDiscussed`
Evaluates the timing density and proximity of the `recentReviews` array to detect currently trending titles.

**Admin Analytics** — `GET /api/admin/getGenreStats` / `getOsPlatformStats` / `releaseYearStats`
Aggregations restructuring the game dataset to analyze global genre trends, platform exclusivity correlations, and historical reception patterns.

### Recommendation Engine (Neo4j)

**Gaming Twins (Jaccard Similarity)** — `GET /api/user/gamingTwins`
Employs graph traversals to calculate an intersection-over-union metric, identifying users with highly correlated library compositions.

**Hidden Gems (Inverse Popularity)** — `GET /api/games/getHiddenGems`
A two-phase traversal uncovering niche titles by identifying games heavily played within a local friend network but exhibiting low global popularity.

**Trending Games (Social Clusters)** — `GET /api/admin/getTrending`
High-performance structural query extracting titles currently popular across interconnected clusters of friends globally.

---

## Authors

- Sebastiano Pala
- Syed Rafay Zia

University of Pisa — Master's Degree in Computer Engineering
Large-Scale and Multi-Structured Databases — Academic Year 2025/2026