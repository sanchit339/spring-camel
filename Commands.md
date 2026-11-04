# Commands used for the project

## ActiveMQ

Start the broker in Docker:

```bash
docker run -p 61616:61616 -p 8161:8161 rmohr/activemq
```

Open the ActiveMQ web console (login `admin` / `admin`):

```
http://localhost:8161
```

## Application

```bash
./mvnw spring-boot:run      # run camel-microservice-a
./mvnw clean compile        # compile only
./mvnw test                 # run tests
```

> Background / notes live in `ActiveMQ-Guide.md`.
