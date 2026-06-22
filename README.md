# Trip Service - WTMS (Waste Transportation Management System)

## Service Overview

The **Trip Service** is the trip orchestration and execution tracking microservice within the WTMS ecosystem. It acts as the **operational hub** that connects external fleet management systems (GPS tracking, weighbridge data) with the internal scheduling system. This service creates, tracks, and completes waste collection trips by synchronizing external trip data with scheduled plans, calculating trip metrics (net weight, fuel consumption, distance), and maintaining a complete audit trail of waste collection activities.

### Key Responsibilities

- **Trip Synchronization**: Polls external GPS/fleet management system (AskTrack) to fetch daily completed trips with weight data
- **Schedule Matching**: Queries Schedule Service to match trips with planned schedules for validation and reconciliation
- **Trip Data Persistence**: Stores trip weight records with loaded weight, empty weight, net weight, distance, fuel, and geospatial path
- **Metric Calculation**: Computes net weight (load - empty), fuel consumption, and actual distance traveled
- **Geospatial Tracking**: Stores trip paths as LineString geometry with coordinate arrays from tracking data
- **Idempotency Management**: Ensures external trip data is not duplicated in database using unique slip IDs
- **Event Publishing**: Publishes trip events to Kafka for downstream services (reporting, analytics, tracking)
- **Vehicle Management**: Consumes vehicle events from Fleet Service and maintains vehicle-tehsil mappings in Redis
- **Cross-Service Integration**: Communicates with Schedule Service via Feign clients for trip-schedule matching
- **Trip Summary & Reporting**: Provides daily trip summaries with tonnage calculations for supervisors and administrators

### Business Context

The Trip Service is the **transaction recording layer** of WTMS. Every waste collection activity must be recorded here with precise measurements:

- **Load Time & Weight**: When waste was loaded at the collection point
- **Empty Time & Weight**: When the vehicle returned with empty weight at the disposal facility
- **Net Weight**: The actual waste collected (Load - Empty)
- **Distance & Route**: Actual path traveled with coordinates
- **Fuel Consumption**: Calculated efficiency metric for fleet optimization

Trip Service ensures accountability, enables accurate billing/reporting, and provides the foundation for performance analytics and compliance auditing.

---

## Architecture & Design

### High-Level Architecture Diagram

```mermaid
graph TB
    Client["Client Application<br/>(Dashboard/Reports)"]
    APIGateway["API Gateway<br/>(Port: 8760)"]
    TripService["Trip Service<br/>(Port: 8777)"]
    EurekaServer["Eureka Server<br/>(Service Discovery)"]
    PostgresDB["PostgreSQL<br/>(wtms_trip_db)"]
    Redis["Redis Cache<br/>(Vehicle & Schedule Data)"]
    Kafka["Apache Kafka<br/>(Event Streaming)"]
    
    AskTrack["AskTrack API<br/>(3rd Party Fleet System)"]
    ScheduleService["Schedule Service<br/>(Trip Matching)"]
    FleetService["Fleet Service<br/>(Vehicle Events)"]
    TrackingService["Tracking Service<br/>(GPS Coordinates)"]

    Client -->|HTTP Request| APIGateway
    APIGateway -->|Route /trip| TripService
    TripService -->|Register| EurekaServer
    TripService -->|Read/Write Trips| PostgresDB
    TripService -->|Cache Vehicle/Schedule Data| Redis
    TripService -->|Fetch Daily Trips| AskTrack
    TripService -->|Query Active Schedule| ScheduleService
    Kafka -->|Consume Vehicle Events| TripService
    TripService -->|Publish Trip Events| Kafka
    FleetService -->|Publish Vehicle Events| Kafka
    TrackingService -->|Real-time GPS| TripService

    style TripService fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style APIGateway fill:#50C878,stroke:#2D7A4A,color:#fff
    style PostgresDB fill:#FF6B6B,stroke:#A63F3F,color:#fff
    style Redis fill:#F4A261,stroke:#B8743D,color:#fff
    style Kafka fill:#9B59B6,stroke:#5D2E7A,color:#fff
```

### Package Diagram (Internal Structure)

```mermaid
graph LR
    TripService["trip-service"]
    
    TripService --> Controllers["controllers"]
    TripService --> Services["services"]
    TripService --> Models["models"]
    TripService --> Configurations["configurations"]
    TripService --> Integrations["integrations"]
    TripService --> Repositories["repositories"]
    TripService --> Producers["producers"]
    TripService --> Consumers["consumers"]
    TripService --> Listeners["listeners"]
    TripService --> Exceptions["exceptions"]
    TripService --> Responses["responses"]
    TripService --> Utils["utils"]

    Controllers --> TripCtrl["TripController"]

    Services --> TripService_Svc["TripService"]
    Services --> Implementations["implementations"]

    Models --> Entity["entity"]
    Models --> DTOs["dtos"]
    Models --> Enums["enums"]
    Models --> UserPrincipal["UserPrincipal"]

    Entity --> TripWeight["TripWeight"]

    DTOs --> TripEventDto["TripResponseEventDto"]
    DTOs --> VehicleEventDto["VehicleResponseEventDto"]

    Enums --> Status["Status"]
    Enums --> EventType["EventType"]
    Enums --> EventStatus["EventStatus"]

    Configurations --> SecurityConfig["SecurityConfig"]
    Configurations --> AuthFilter["AuthorizationFilter"]
    Configurations --> FeignConfig["FeignConfig"]
    Configurations --> KafkaConfigs["Kafka Configs"]
    Configurations --> RedisConfig["RedisConfig"]
    Configurations --> AppConfig["AppConfig"]

    Integrations --> AskTrackInteg["asktrack/"]
    Integrations --> ScheduleInteg["schedule/"]

    AskTrackInteg --> AskTrackClient["AskTrackerClient"]
    AskTrackInteg --> AskTrackResponse["AskTrackerTripsResponseDto"]

    ScheduleInteg --> ScheduleClient["ScheduleClientService"]
    ScheduleInteg --> ScheduleResponse["ScheduleResponseDto"]

    Repositories --> TripWeightRepo["TripWeightRepository"]

    Producers --> TripProducer["TripEventProducer"]

    Consumers --> VehicleConsumer["VehicleEventConsumer"]

    Listeners --> TripListener["TripEventListener"]

    Exceptions --> DBException["DataBaseException"]
    Exceptions --> ResourceNotFound["ResourceNotFoundException"]
    Exceptions --> UnauthorizedEx["UnauthorizedException"]
    Exceptions --> GlobalHandler["GlobalExceptionHandler"]

    Responses --> TripResp["TripResponse"]
    Responses --> TripSummaryResp["TripSummaryResponse"]
    Responses --> ErrorResp["ErrorResponse"]

    Utils --> ResponseConversion["ResponseConversion"]
    Utils --> SpatialUtils["SpatialUtils"]

    style TripService fill:#4A90E2,color:#fff
    style Controllers fill:#50C878,color:#fff
    style Services fill:#F4A261,color:#fff
    style Integrations fill:#9B59B6,color:#fff
```

### Class Diagram (Core Domain Model)

```mermaid
classDiagram
    class TripWeight {
        -UUID id
        -UUID scheduleId
        -String slipId
        -String vehicleNo
        -LocalDateTime loadTime
        -Double loadWeight
        -LocalDateTime emptyTime
        -Double emptyWeight
        -Double netWeight
        -Double actualDistance
        -Double fuelConsumed
        -LineString path
        -Status status
        +getId() UUID
        +getSlipId() String
        +getVehicleNo() String
        +getNetWeight() Double
    }

    class Status {
        <<enumeration>>
        AT_DUMPSITE
        IN_TRANSIT
        COMPLETED
        CANCELLED
    }

    class TripResponseEventDto {
        -EventType type
        -EventStatus eventTypeStatus
        -TripResponse tripData
    }

    class EventStatus {
        <<enumeration>>
        SUCCESS
        FAILURE
    }

    class EventType {
        <<enumeration>>
        CREATE
        UPDATE
        DELETE
    }

    class TripResponse {
        -UUID tripId
        -UUID scheduleId
        -String slipId
        -String vehicleNo
        -LocalDateTime loadTime
        -Double loadWeight
        -LocalDateTime emptyTime
        -Double emptyWeight
        -Double netWeight
        -Double actualDistance
        -Double fuelConsumed
        -String encodedPolyline
        -String status
    }

    class TripService {
        <<interface>>
        +syncDailyTrips(String) void
        +getTripsByDate(LocalDate) TripSummaryResponse
    }

    class AskTrackerClient {
        -String baseUrl
        -String apiKey
        -boolean isMockEnabled
        +fetchDailyTrips(String, LocalDate) List
    }

    class ScheduleClientService {
        <<interface>>
        +findActiveScheduleForTrip(String, String, String) ScheduleResponseDto
    }

    TripWeight --> Status
    TripResponseEventDto --> EventStatus
    TripResponseEventDto --> EventType
    TripResponseEventDto --> TripResponse
    TripResponse --> Status
    TripService --> TripWeight
    TripService --> TripResponse

    style TripWeight fill:#4A90E2,stroke:#2E5C8A,color:#fff
    style TripService fill:#9B59B6,stroke:#5D2E7A,color:#fff
    style AskTrackerClient fill:#50C878,stroke:#2D7A4A,color:#fff
```

---

## Setup & Execution

### Prerequisites

Ensure the following services and tools are installed and running on your machine:

- **Java Development Kit (JDK)**: Version 17 or higher
- **Apache Maven**: Version 3.8.1 or higher
- **PostgreSQL**: Version 13+ (for trip database storage)
- **Apache Kafka**: Version 3.0+ (for event streaming)
- **Redis**: Version 6.0+ (for vehicle and schedule caching)
- **Eureka Server**: Running on `http://localhost:8761/eureka/` (for service discovery)
- **Schedule Service**: Running and accessible for trip matching queries
- **External Fleet API**: AskTrack or compatible system with daily trip data

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd BackEnd/trip-service
```

### Step 2: Configure Environment Variables

Update `src/main/resources/application.properties` with your environment-specific values:

```properties
# Application Configuration
spring.application.name=trip-service
server.port=8777

# PostgreSQL Configuration
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.url=jdbc:postgresql://localhost:5432/wtms_trip_db
spring.datasource.username=admin
spring.datasource.password=your_strong_password

# JPA/Hibernate Settings
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
logging.level.org.hibernate.SQL=DEBUG

# Kafka Configuration
kafka.bootstrap.server=localhost:9092
kafka.consumer.group=trip-group

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=7

# JWT Configuration
jwt.public-key.path=classpath:certs/public_key.pem
app.security.internal-secret=yK8!pL3@xQ7#dT9$wF2^sR5&vM1*bN6(

# Eureka Configuration
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true

# AskTrack External API Configuration
asktrack.base-url=https://askteckbypass.onrender.com
asktrack.api-key=your_api_key_here
asktrack.mock.enabled=false

# Logging Configuration
logging.level.com.yasirkhan.trip=DEBUG
logging.level.org.springframework.security=INFO
```

### Step 3: Build the Service

```bash
# Clean and build with Maven
mvn clean install

# Or skip tests for faster build
mvn clean install -DskipTests
```

### Step 4: Run the Service Locally

```bash
# Option 1: Using Maven Spring Boot plugin
mvn spring-boot:run

# Option 2: Run the generated JAR
java -jar target/trip-service-0.0.1-SNAPSHOT.jar
```

### Step 5: Verify the Service

Once the service is running, verify its status:

```bash
# Health Check
curl -X GET http://localhost:8777/actuator/health

# Check Eureka Registration
curl -X GET http://localhost:8761/eureka/apps/trip-service

# Get Trip Summary for Today
curl -X GET http://localhost:8777/trip \
  -H "Authorization: Bearer <admin_jwt_token>"

# Get Trip Summary for Specific Date
curl -X GET "http://localhost:8777/trip?date=2026-06-22" \
  -H "Authorization: Bearer <admin_jwt_token>"

# Swagger UI (OpenAPI Documentation)
# Open in browser: http://localhost:8777/swagger-ui.html
```

### Default Port Configuration

| Service | Port | Description |
|---------|------|-------------|
| Trip Service | `8777` | Trip Management & Orchestration |
| Eureka Server | `8761` | Service Discovery |
| Kafka | `9092` | Event Streaming |
| PostgreSQL | `5432` | Trip Database |
| Redis | `6379` | Vehicle & Schedule Cache |

---

## Environment Variables & Application Properties

### Required Configuration Table

| Property | Type | Default | Description | Example |
|----------|------|---------|-------------|---------|
| `spring.application.name` | String | `trip-service` | Microservice identifier | `trip-service` |
| `server.port` | Integer | `8777` | HTTP server port | `8777` |
| `spring.datasource.url` | String | Required | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/wtms_trip_db` |
| `spring.datasource.username` | String | Required | Database username | `admin` |
| `spring.datasource.password` | String | Required | Database password (strong) | `your_strong_password` |
| `spring.jpa.hibernate.ddl-auto` | String | `update` | Schema generation strategy | `update` / `create` / `validate` |
| `kafka.bootstrap.server` | String | Required | Kafka broker address | `localhost:9092` |
| `kafka.consumer.group` | String | `trip-group` | Kafka consumer group ID | `trip-group` |
| `spring.data.redis.host` | String | Required | Redis server hostname | `localhost` |
| `spring.data.redis.port` | Integer | `6379` | Redis server port | `6379` |
| `spring.data.redis.database` | Integer | `7` | Redis database number | `7` |
| `asktrack.base-url` | String | Required | AskTrack API base URL | `https://askteckbypass.onrender.com` |
| `asktrack.api-key` | String | Required | AskTrack API authentication key | `my-super-secret-key-123` |
| `asktrack.mock.enabled` | Boolean | `false` | Enable mock mode (uses local JSON file) | `true` / `false` |
| `jwt.public-key.path` | String | Required | Path to RSA public key (PEM) | `classpath:certs/public_key.pem` |
| `app.security.internal-secret` | String | Required | Internal service-to-service secret (min 32 chars) | `yK8!pL3@xQ7#dT9$wF2^sR5&vM1*bN6(` |
| `eureka.client.service-url.defaultZone` | String | Required | Eureka server URL | `http://localhost:8761/eureka/` |
| `eureka.instance.prefer-ip-address` | Boolean | `true` | Use IP address instead of hostname | `true` |
| `management.tracing.sampling.probability` | Float | `1.0` | Distributed tracing sample rate (0.0-1.0) | `1.0` |
| `logging.level.com.yasirkhan.trip` | String | `INFO` | Application logging level | `DEBUG` / `INFO` |

### Kafka Topics Configuration

| Topic | Consumer Group | Producer | Purpose |
|-------|----------------|----------|---------|
| `vehicle-response-topic` | `trip-group` | Fleet Service | Vehicle assignment and tehsil mapping updates |
| `trip-response-topic` | - | Trip Service | Trip creation, update, completion events |

---

## API Endpoints

### REST Endpoints

| HTTP Method | Endpoint | Role Required | Description | Request Parameters | Response |
|-------------|----------|---------------|-------------|-------------------|----------|
| `GET` | `/trip` | `ADMIN`, `SUPERVISOR` | Get trip summary for a date (defaults to today) | `date` (optional, format: YYYY-MM-DD) | HTTP 200 OK, `TripSummaryResponse` with trips and total tonnage |
| `GET` | `/actuator/health` | Public | Service health status | None | `{ "status": "UP/DOWN", "components": {...} }` |
| `GET` | `/actuator/metrics` | Public | Application metrics | None | Micrometer metrics |
| `GET` | `/swagger-ui.html` | Public | OpenAPI documentation | None | Interactive API documentation |

### Trip Data Synchronization

**Note**: Trip synchronization (polling AskTrack API) is typically triggered by external scheduler or event listener. Below is the internal operation flow:

| Operation | Trigger | Description |
|-----------|---------|-------------|
| `syncDailyTrips(wmc)` | External scheduler/event | Polls AskTrack API for daily trips, matches with schedules, persists TripWeight records |
| Vehicle Pre-Filtering | Sync start | Validates against cached vehicles in Redis to prevent orphaned trips |
| Schedule Matching | Per trip | Queries Schedule Service for active schedule matching vehicle, date, and time |
| Trip Recording | Post-match | Creates TripWeight record with load/empty weights, distance, path, fuel consumption |

### Example API Requests

#### Get Trip Summary for Today
```bash
curl -X GET http://localhost:8777/trip \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### Response Example
```json
{
  "trips": [
    {
      "tripId": "550e8400-e29b-41d4-a716-446655440000",
      "scheduleId": "550e8400-e29b-41d4-a716-446655440001",
      "slipId": "SLIP-2026-06-22-001",
      "vehicleNo": "PKI-123",
      "loadTime": "2026-06-22T06:30:00",
      "loadWeight": 18.5,
      "emptyTime": "2026-06-22T08:45:00",
      "emptyWeight": 2.3,
      "netWeight": 16.2,
      "actualDistance": 25.5,
      "fuelConsumed": 3.2,
      "encodedPolyline": "a~l~Fjk~uOwHJy@P",
      "status": "COMPLETED"
    }
  ],
  "totalTonnage": 285.8
}
```

#### Get Trip Summary for Specific Date
```bash
curl -X GET "http://localhost:8777/trip?date=2026-06-22" \
  -H "Authorization: Bearer <jwt_token>"
```

#### Response Example (Specific Date)
```json
{
  "trips": [
    {
      "tripId": "550e8400-e29b-41d4-a716-446655440000",
      "scheduleId": "550e8400-e29b-41d4-a716-446655440001",
      "slipId": "SLIP-2026-06-22-001",
      "vehicleNo": "PKI-123",
      "loadTime": "2026-06-22T06:30:00",
      "loadWeight": 18.5,
      "emptyTime": "2026-06-22T08:45:00",
      "emptyWeight": 2.3,
      "netWeight": 16.2,
      "actualDistance": 25.5,
      "fuelConsumed": 3.2,
      "encodedPolyline": "a~l~Fjk~uOwHJy@P",
      "status": "COMPLETED"
    },
    {
      "tripId": "550e8400-e29b-41d4-a716-446655440002",
      "scheduleId": "550e8400-e29b-41d4-a716-446655440003",
      "slipId": "SLIP-2026-06-22-002",
      "vehicleNo": "PKI-124",
      "loadTime": "2026-06-22T07:15:00",
      "loadWeight": 19.2,
      "emptyTime": "2026-06-22T09:30:00",
      "emptyWeight": 2.0,
      "netWeight": 17.2,
      "actualDistance": 28.3,
      "fuelConsumed": 3.5,
      "encodedPolyline": "c~l~Fjk~uOwHJy@P",
      "status": "COMPLETED"
    }
  ],
  "totalTonnage": 33.4
}
```

---

## Test Cases & Documentation

### Core Test Scenarios

| Scenario ID | Category | Scenario Description | Input Parameters | Expected Output | Validation Type |
|-------------|----------|----------------------|-------------------|------------------|-----------------|
| **TRIP-TC-001** | Trip Sync | Successful daily trip sync from AskTrack | wmc = "RWMC", date = 2026-06-22 | Trips fetched, matched with schedules, persisted in DB | Integration Test |
| **TRIP-TC-002** | Trip Sync | Trip sync with mock mode enabled | asktrack.mock.enabled = true | Mock JSON data loaded, trips created | Integration Test |
| **TRIP-TC-003** | Trip Sync | Trip sync with no active vehicles | Redis empty/no vehicles cached | Sync aborts early, no trips created | Integration Test |
| **TRIP-TC-004** | Trip Sync | Trip sync with no matching schedule | Valid vehicle, but Schedule Service returns 404 | Trip logged as orphaned, not created (warning logged) | Integration Test |
| **TRIP-TC-005** | Trip Sync | Trip sync with AskTrack API timeout | API request exceeds timeout (>30 seconds) | Error logged, sync fails gracefully | Integration Test |
| **TRIP-TC-006** | Trip Sync | Trip sync with invalid API response | API returns malformed JSON | Error logged, deserialization fails, sync stops | Integration Test |
| **TRIP-TC-007** | Idempotency | Duplicate slip ID in subsequent sync | Same slipId processed twice | Only first instance created, second skipped (idempotency check) | Unit Test |
| **TRIP-TC-008** | Weight Calculation | Net weight calculation | loadWeight = 20.0, emptyWeight = 3.0 | netWeight = 17.0 | Unit Test |
| **TRIP-TC-009** | Trip Creation | Trip with valid load and empty times | Both loadTime and emptyTime populated | Status = COMPLETED, trip recorded | Integration Test |
| **TRIP-TC-010** | Trip Creation | Trip with missing empty weight | emptyWeight = null | Trip skipped (still in progress, not completed) | Unit Test |
| **TRIP-TC-011** | Trip Creation | Trip with invalid vehicle number | vehicleNo = non-existent, not in Redis | Trip skipped (Redis pre-filter removes it) | Unit Test |
| **TRIP-TC-012** | Schedule Matching | Match trip with active schedule | vehicleNo, loadTime → Schedule Service query | Schedule found, scheduleId assigned to trip | Integration Test |
| **TRIP-TC-013** | Schedule Matching | No schedule for trip time slot | Trip time outside any schedule | 404 Not Found from Schedule Service, trip orphaned | Integration Test |
| **TRIP-TC-014** | Distance & Path | Actual distance calculated from tracking data | Coordinates from AskTrack API | actualDistance in km, path as LineString geometry | Unit Test |
| **TRIP-TC-015** | Distance & Path | Geospatial LineString encoding | Coordinates: [(33.7250, 73.2000), (33.7251, 73.2001)] | LineString stored in PostgreSQL, encoded polyline generated | Integration Test |
| **TRIP-TC-016** | REST API | Get trip summary for today | GET /trip, Authorization: Bearer <token> | HTTP 200, trips for today returned | Integration Test |
| **TRIP-TC-017** | REST API | Get trip summary for specific date | GET /trip?date=2026-06-22 | HTTP 200, trips for specified date returned | Integration Test |
| **TRIP-TC-018** | REST API | Get trip summary without authorization | GET /trip, no Authorization header | HTTP 401 Unauthorized | Unit Test |
| **TRIP-TC-019** | REST API | Get trip summary with invalid JWT | GET /trip with expired/invalid token | HTTP 401 Unauthorized, "Invalid token" | Unit Test |
| **TRIP-TC-020** | REST API | Get trip summary as non-admin | JWT token with DRIVER role | HTTP 403 Forbidden, insufficient permissions | Unit Test |
| **TRIP-TC-021** | REST API | Get trip summary with no trips | Date with zero completed trips | HTTP 200, trips = [], totalTonnage = 0 | Integration Test |
| **TRIP-TC-022** | Kafka Events | Publish trip creation event | TripResponseEventDto created | Event sent to trip-response-topic with partition/offset | Integration Test |
| **TRIP-TC-023** | Kafka Events | TripEventListener ensures transactional delivery | Trip created in DB, event published after commit | Event only sent if DB commit successful (AFTER_COMMIT phase) | Integration Test |
| **TRIP-TC-024** | Kafka Consumer | Consume vehicle event from Fleet Service | VehicleResponseEventDto received | Vehicle cached in Redis with tehsilId and status | Integration Test |
| **TRIP-TC-025** | Redis Caching | Vehicle data cached from events | VehicleEventConsumer processes event | Redis key: wtms:vehicle:{vehicleNo}, value: {tehsilId, status} | Unit Test |
| **TRIP-TC-026** | Redis Pre-filtering | Trip filtered by cached vehicles | syncDailyTrips() reads wtms:vehicle:* patterns | Only registered vehicles processed | Unit Test |
| **TRIP-TC-027** | Feign Client | Schedule Service call with signature | FeignConfig adds X-Service-Name, X-Timestamp, X-Signature headers | Request properly signed with SHA-256 HMAC | Unit Test |
| **TRIP-TC-028** | Feign Client | Schedule Service call failure handling | Schedule Service returns 500 error | Error logged, trip marked as orphaned (graceful degradation) | Integration Test |
| **TRIP-TC-029** | Database | Trip persisted with all fields | All TripWeight fields populated | Record found in WTMS_TRIP_WEIGHTS table | Integration Test |
| **TRIP-TC-030** | Database | TripWeightRepository.existsBySlipId() check | slipId = "SLIP-001" exists | Returns true, prevents duplicate creation | Unit Test |
| **TRIP-TC-031** | Database | Total tonnage calculation | Query sum of netWeight for date range | Aggregated tonnage calculated efficiently via SQL | Integration Test |
| **TRIP-TC-032** | Authorization | Trip endpoint accessible by ADMIN | JWT token with ADMIN role | HTTP 200, data returned | Unit Test |
| **TRIP-TC-033** | Authorization | Trip endpoint accessible by SUPERVISOR | JWT token with SUPERVISOR role | HTTP 200, data returned | Unit Test |
| **TRIP-TC-034** | Authorization | Trip endpoint NOT accessible by DRIVER | JWT token with DRIVER role | HTTP 403 Forbidden | Unit Test |
| **TRIP-TC-035** | Error Handling | Database connection failure | PostgreSQL unavailable | HTTP 500 Internal Server Error, error logged | Integration Test |
| **TRIP-TC-036** | Error Handling | Redis connection failure during pre-filter | Redis unavailable | Trip sync aborts gracefully, error logged | Integration Test |
| **TRIP-TC-037** | Error Handling | Kafka producer failure | KafkaTemplate.send() fails | Error logged, trip still persisted (DB commit succeeded) | Integration Test |
| **TRIP-TC-038** | Data Validation | Trip date validation | loadTime > emptyTime | Trip considered valid, status = COMPLETED | Unit Test |
| **TRIP-TC-039** | Data Validation | Trip with zero net weight | loadWeight = 10, emptyWeight = 10 | netWeight = 0, accepted (valid scenario: no load collected) | Unit Test |
| **TRIP-TC-040** | Performance | Sync 1000 daily trips | AskTrack returns 1000 slips | All processed, filtered, and matched within 5 seconds | Performance Test |

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TripServiceImplApplicationTests

# Run tests with coverage
mvn clean test jacoco:report

# View coverage report
# Open target/site/jacoco/index.html in browser
```

### Test Dependencies

The project includes the following testing frameworks:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Key Components & Their Roles

### Trip Orchestration

- **TripService Interface**: Defines contract for trip operations (sync, retrieval)
- **TripServiceImpl**: Core implementation handling trip sync, schedule matching, and data persistence
- **TripWeight Entity**: JPA entity representing a single waste collection trip with all metrics

### External Integrations

- **AskTrackerClient**: Feign/RestClient calling external AskTrack API to fetch daily trips from weighbridge system
- **ScheduleClientService**: Feign client communicating with Schedule Service via SHA-256 signed requests
- **Mock Mode**: Loads trip data from local JSON (`mock-asktrack-trips.json`) when `asktrack.mock.enabled=true`

### Data Synchronization

- **Trip Filtering**: Pre-filters vehicles using Redis cache to prevent orphaned trips
- **Idempotency Checks**: Uses `slipId` to prevent duplicate trip entries
- **Schedule Matching**: Queries Schedule Service for active schedule matching vehicle, date, and time
- **Transactional Listeners**: Ensures Kafka events sent only after DB commit (`@TransactionalEventListener`)

### Geospatial Processing

- **SpatialUtils**: Encodes GPS coordinates into polylines, stores as LineString geometry
- **LineString Persistence**: PostgreSQL stores geometry with EPSG:4326 projection
- **Distance Calculation**: Computes actual distance from tracking coordinates

### Event Publishing

- **TripEventProducer**: Publishes trip events to Kafka `trip-response-topic`
- **TripEventListener**: Transactional listener ensuring events sent after DB commit
- **Trip Events**: Include CREATE, UPDATE, DELETE operations with status

### Data Caching

- **RedisTemplate**: Caches vehicle data (tehsilId, status) in database index 7
- **VehicleEventConsumer**: Listens to `vehicle-response-topic` and updates Redis cache
- **Pre-filtering**: Speeds up trip sync by validating vehicles against cached list

### Error Handling

- **GlobalExceptionHandler**: Handles DatabaseException, UnauthorizedException, ResourceNotFoundException
- **AskTrackIntegrationException**: Custom exception for AskTrack API failures
- **Graceful Degradation**: Trip sync continues even if individual trips fail to match schedules

---

## Cross-Service Communication Patterns

### Trip Service → Schedule Service

**Purpose**: Match actual trips with planned schedules for validation

```
Trip Service                          Schedule Service
    │                                      │
    ├─ POST http://schedule-service:8766/schedule/active-for-trip
    │  Parameters:
    │  - vehicleNo: "PKI-123"
    │  - targetDate: "2026-06-22"
    │  - targetTime: "06:30:00"
    │
    ├─ FeignClient call with signed headers:
    │  - X-Service-Name: "TRIP_SERVICE"
    │  - X-Timestamp: milliseconds
    │  - X-Signature: SHA-256(serviceName + timestamp + secret)
    │
    └─ Receives: ScheduleResponseDto
       └─ Contains: scheduleId, vehicleNo, status, tehsilId
```

### Trip Service → AskTrack (3rd Party API)

**Purpose**: Fetch daily completed trips with weight and timestamp data

```
Trip Service                     AskTrack API
    │                            (3rd Party)
    ├─ GET /api/trip-data
    │  Query Parameters:
    │  - wmc: "RWMC"
    │  - from: "22-06-2026"
    │  - to: "22-06-2026"
    │  Headers:
    │  - x-api-key: {apiKey}
    │
    └─ Receives: AskTrackerTripsResponseDto
       └─ Contains: List<AskTrackSlipDto>
          ├─ slipId, vehicleNo, loadDate, loadTime, loadWeight
          ├─ emptyDate, emptyTime, emptyWeight, netWeight
          ├─ coordinates (for path calculation)
          └─ fuelConsumed
```

### Trip Service → Fleet Service (Kafka)

**Purpose**: Consume vehicle status updates and cache for filtering

```
Fleet Service                    Trip Service
(Publishes)                      (Subscribes)
    │                                 │
    ├─ Publish to: vehicle-response-topic
    │  Payload: VehicleResponseEventDto
    │  └─ vehicleNo, tehsilId, status, eventStatus
    │
    └─ VehicleEventConsumer receives
       └─ Caches in Redis: wtms:vehicle:{vehicleNo}
```

### Trip Service → Tracking Service (Kafka)

**Purpose**: Publish trip events for real-time tracking and reporting

```
Trip Service                    Tracking Service
(Publishes)                     (Subscribes)
    │                                 │
    ├─ Publish to: trip-response-topic
    │  Payload: TripResponseEventDto
    │  ├─ type: CREATE | UPDATE | DELETE
    │  ├─ eventStatus: SUCCESS | FAILURE
    │  └─ tripData: TripResponse
    │
    └─ Downstream services receive for:
       ├─ Analytics & reporting
       ├─ Real-time dashboards
       ├─ Billing & accounting
       └─ Compliance auditing
```

---

## Trip Synchronization Flow

### Daily Sync Process

```
1. External Trigger
   └─ Scheduler or manual API call with wmc parameter

2. Vehicle Pre-Filtering
   └─ Read Redis cache: SCAN "wtms:vehicle:*"
   └─ Build validVehicles set from cached vehicle IDs

3. AskTrack API Call
   └─ Call AskTrackerClient.fetchDailyTrips(wmc, date)
   └─ Handle API failures with graceful error logging

4. Per-Trip Processing Loop
   for each AskTrackSlipDto:
   ├─ Extract slipId (fallback logic if missing)
   ├─ Get vehicleNo
   │
   ├─ Filter 1: Redis Vehicle Pre-filter
   │  └─ if vehicleNo not in validVehicles: SKIP (orphaned)
   │
   ├─ Filter 2: Completion Check
   │  └─ if emptyTime is null or emptyWeight is null: SKIP (in progress)
   │
   ├─ Filter 3: Idempotency Check
   │  └─ if slipId exists in DB: SKIP (already synced)
   │
   ├─ Data Extraction
   │  ├─ loadWeight, emptyWeight, netWeight
   │  ├─ loadTime, emptyTime (parse from date + time strings)
   │  └─ Coordinates for path (from AskTrack tracking data)
   │
   ├─ Schedule Matching
   │  └─ Call ScheduleClientService.findActiveScheduleForTrip()
   │     ├─ Parameters: vehicleNo, loadTime.toLocalDate(), loadTime.toLocalTime()
   │     ├─ If 404: Log orphaned trip, SKIP
   │     └─ If successful: Get scheduleId
   │
   ├─ Geospatial Processing
   │  ├─ Fetch trackerId from Redis or use default
   │  ├─ Call GPS tracking for coordinates (if available)
   │  └─ Build LineString path geometry
   │
   └─ Trip Creation
      ├─ Create TripWeight entity with all fields:
      │  ├─ scheduleId (from Schedule Service)
      │  ├─ slipId, vehicleNo
      │  ├─ loadTime, loadWeight
      │  ├─ emptyTime, emptyWeight
      │  ├─ netWeight (calculated: load - empty)
      │  ├─ actualDistance, fuelConsumed
      │  ├─ path (LineString geometry)
      │  └─ status = COMPLETED
      │
      ├─ Save to TripWeightRepository
      │
      └─ Publish Event (After DB Commit)
         ├─ Create TripResponseEventDto (type: CREATE)
         ├─ Send via TripEventProducer to trip-response-topic
         └─ Event published only if DB commit successful

5. Summary
   └─ Log sync statistics: processed, created, skipped counts
```

---

## Monitoring & Observability

### Actuator Endpoints

The service exposes the following monitoring endpoints via Spring Boot Actuator:

```bash
# Health check
curl http://localhost:8777/actuator/health

# Application metrics
curl http://localhost:8777/actuator/metrics

# Database connectivity
curl http://localhost:8777/actuator/health/db

# Trace recent requests (if enabled)
curl http://localhost:8777/actuator/httptrace
```

### Logging Configuration

Logs are configured using Log4j2 (high-performance asynchronous logging):

- **Log File**: `logs/trip-service.log`
- **Log Level**: Configurable per package
- **Async Appender**: Uses Disruptor for high-throughput logging
- **Key Log Messages**:
  - `Starting scheduled 3rd-party tracking API ingestion cycle`: Sync initiated
  - `Loaded {count} registered WTMS vehicles from local Redis cache`: Pre-filter completed
  - `Skipping trip {slipId} because no valid schedule was found`: Orphaned trip
  - `SUCCESS: Trip Response {type} event sent for Trip ID: {id}`: Event published
  - `FAILED to send Trip Response {type} event`: Kafka failure

### Distributed Tracing

- **Micrometer Tracing**: Enabled with Brave bridge for distributed tracing
- **Trace ID Propagation**: 100% sampling enabled
- **Integration**: Compatible with ELK Stack, Jaeger, or Zipkin
- **Trace Context**: Propagated through Kafka messages and Feign calls

---

## Common Issues & Troubleshooting

### Issue 1: PostgreSQL Connection Failed

**Symptoms**: `SQLException: Unable to connect to database at localhost:5432`

**Solutions**:
```bash
# Verify PostgreSQL is running
psql -U admin -d wtms_trip_db

# Check connection string in application.properties
# Ensure wtms_trip_db exists
# Verify credentials are correct

# Create database if missing
createdb -U admin wtms_trip_db
```

### Issue 2: AskTrack API Integration Fails

**Symptoms**: `AskTrackIntegrationException: Client error fetching daily trips`

**Solutions**:
```bash
# Verify API URL and credentials
curl -X GET "https://askteckbypass.onrender.com/api/trip-data?wmc=RWMC&from=22-06-2026&to=22-06-2026" \
  -H "x-api-key: $ASKTRACK_API_KEY"

# Check API key expiry and permissions
# For testing, enable mock mode: asktrack.mock.enabled=true

# Verify network connectivity to AskTrack endpoint
telnet askteckbypass.onrender.com 443
```

### Issue 3: Schedule Service Query Returns 404

**Symptoms**: Trips created but scheduleId remains null (orphaned trips)

**Solutions**:
```bash
# Verify Schedule Service is running
curl http://localhost:8766/actuator/health

# Test Schedule Service endpoint directly
curl -X GET "http://localhost:8766/schedule/active-for-trip?vehicleNo=PKI-123&targetDate=2026-06-22&targetTime=06:30:00" \
  -H "Authorization: Bearer $JWT_TOKEN"

# Verify vehicle is actually assigned in Schedule Service
# Check schedule date/time ranges match trip times
```

### Issue 4: Trips Not Syncing - Redis Pre-filter Issue

**Symptoms**: No trips created despite AskTrack returning data

**Solutions**:
```bash
# Verify vehicles are cached in Redis
redis-cli -n 7 SCAN 0 MATCH "wtms:vehicle:*"

# If empty, vehicle events not being consumed
# Check vehicle-response-topic in Kafka for events

# Check if VehicleEventConsumer is subscribed
kafka-consumer-groups.sh --describe --bootstrap-server localhost:9092 --group trip-group

# Manually populate Redis for testing
redis-cli -n 7 HSET wtms:vehicle:PKI-123 tehsilId "tehsil-1" status "ACTIVE"
```

### Issue 5: Kafka Event Publishing Fails

**Symptoms**: Trips created but events not reaching Kafka

**Solutions**:
```bash
# Verify Kafka broker running
jps | grep Kafka

# Check bootstrap server address
# Verify trip-response-topic exists
kafka-topics.sh --list --bootstrap-server localhost:9092

# Monitor topic for messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic trip-response-topic --from-beginning

# Check Kafka producer logs for errors
```

### Issue 6: High Database Query Times

**Symptoms**: GET /trip endpoint slow with large datasets

**Solutions**:
```bash
# Add index on loadTime for better query performance
CREATE INDEX idx_trip_load_time ON WTMS_TRIP_WEIGHTS(load_time);

# Consider pagination for large result sets
# Use EXPLAIN ANALYZE to check query plans
EXPLAIN ANALYZE SELECT SUM(net_weight) FROM WTMS_TRIP_WEIGHTS 
WHERE load_time >= '2026-06-22 00:00:00' AND load_time < '2026-06-23 00:00:00';
```

---

## Performance Optimization

### Database Optimization

- **Indexing**: Create indices on loadTime, vehicleNo, scheduleId, slipId
- **Batch Operations**: Use JPA batch inserts for large trip syncs
- **Query Optimization**: Use aggregate functions (SUM) for tonnage calculations

### Caching Optimization

- **Redis TTL**: Set expiry on vehicle cache to prevent stale data
- **Local Filtering**: Pre-filter vehicles in memory before database operations
- **Connection Pooling**: Configure Redis connection pool appropriately

### API Integration Optimization

- **Batch Requests**: Fetch multiple days worth of trips in single call if possible
- **Connection Timeout**: Configure reasonable timeout for AskTrack API
- **Retry Logic**: Implement exponential backoff for failed API calls
- **Circuit Breaker**: Use Resilience4j for cascading failure prevention

---

## Deployment & Production Checklist

- [ ] Change default passwords and API keys in `application.properties`
- [ ] Configure external PostgreSQL instance (non-localhost) with replication
- [ ] Set up automated database backups (daily snapshots)
- [ ] Configure external Kafka cluster with replication and persistence
- [ ] Configure external Redis instance with replication and AOF persistence
- [ ] Enable SSL/TLS for all external API calls
- [ ] Set up distributed tracing (Jaeger/Zipkin) for observability
- [ ] Enable centralized logging (ELK Stack) for all services
- [ ] Configure health check endpoints and alerting
- [ ] Implement rate limiting for external API calls
- [ ] Set up connection pooling and timeout configurations
- [ ] Load test trip sync with 10,000+ daily trips
- [ ] Monitor database performance for large trip datasets
- [ ] Review and harden Spring Security configurations
- [ ] Implement audit logging for trip data changes
- [ ] Set up backup and disaster recovery procedures
- [ ] Configure CI/CD pipeline with automated tests
- [ ] Regular security audits and dependency updates
- [ ] Document API integration with AskTrack
- [ ] Set up alerts for failed trip syncs or Kafka delivery failures
- [ ] Implement data retention and archival policies for historical trips

---

## Additional Resources

- **Spring Boot Documentation**: https://spring.io/projects/spring-boot
- **Spring Cloud OpenFeign**: https://spring.io/projects/spring-cloud-openfeign
- **Spring Security**: https://spring.io/projects/spring-security
- **Spring Cloud Netflix Eureka**: https://spring.io/projects/spring-cloud-netflix
- **Kafka Documentation**: https://kafka.apache.org/documentation/
- **Redis Documentation**: https://redis.io/documentation
- **Micrometer Docs**: https://micrometer.io/docs
- **PostgreSQL Geospatial (PostGIS)**: https://postgis.net/documentation/
- **OpenAPI/Swagger**: `/swagger-ui.html`

---

## Contributing & Support

For issues, questions, or contributions:

1. Review the [HELP.md](./HELP.md) file for additional setup guidance
2. Check the inline code comments for implementation details
3. Refer to the Spring Boot logs (`logs/` directory) for debugging
4. Monitor Kafka messages and database operations during troubleshooting
5. Contact the WTMS development team for support

---

**Last Updated**: June 22, 2026  
**Service Version**: 0.0.1-SNAPSHOT  
**Java Version**: 17  
**Spring Boot Version**: 4.0.6  
**Spring Cloud Version**: 2025.1.1
