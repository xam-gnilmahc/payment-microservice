# payment-microservice

Payment Microservice for processing payments across multiple platforms built with Spring Boot 3 and MySQL.

## Prerequisites

- Java 17
- Maven
- MySQL (via XAMPP or standalone)
- Docker (optional)

## Getting Started

### 1. Start MySQL (XAMPP)

Start MySQL from XAMPP Control Panel.

### 2. Create Database

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS payment_db;"
```

### 3. Run Application

```bash
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home mvn spring-boot:run
```

### 4. Access API

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **API Docs:** http://localhost:8080/api-docs
- **Health Check:** http://localhost:8080/actuator/health

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/payments` | Create a new payment |
| GET | `/api/v1/payments/{paymentId}` | Get payment by ID |
| GET | `/api/v1/payments/merchant/{merchantId}` | Get payments by merchant |
| GET | `/api/v1/payments/status/{status}` | Get payments by status |
| POST | `/api/v1/payments/{paymentId}/process` | Process a payment |
| POST | `/api/v1/payments/{paymentId}/cancel` | Cancel a payment |

## Docker

```bash
docker-compose up --build
```

## Tech Stack

- Spring Boot 3.3.5
- Java 17
- MySQL 8
- Spring Data JPA
- Spring Validation
- Spring Actuator
- OpenAPI/Swagger
- Lombok
- MapStruct
- Docker
