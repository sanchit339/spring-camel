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

## Route IDs

Give a route a readable, stable name with `.routeId(...)` instead of relying on
the generated `route1`:

```java
from("timer:first-timer")
    .routeId("The first route") // custom route name
    .bean(getCurrentTimeBean, "getCurrentTime")
    .log("${body}");
```

The route ID is what Camel shows in its startup/shutdown logs, and what you use
when referring to the route from tooling or tests:

```text
Routes stopped (total:1)
    Stopped The first route (timer://first-timer)
```

Run the app and watch the route start/stop with its custom ID:

```bash
./mvnw spring-boot:run
```

> `FirstTImerRoute` is currently disabled with `//@Component`, so enable it first
> if you want to see this route in the logs.

> Background / notes live in `ActiveMQ-Guide.md`.
