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

## File Consumer Headers

When `from("file:...")` consumes a file, Camel copies the file's metadata into
exchange headers. Example for
`camel-microservice-a/files/input/HELLOWORLD.json`:

| Header | Example value | Meaning |
| --- | --- | --- |
| `CamelFileAbsolute` | `false` | Boolean: is the consumed path absolute? `false` = the endpoint path was relative. |
| `CamelFileAbsolutePath` | `/Users/.../files/input/HELLOWORLD.json` | Full path Camel uses to open the file. |
| `CamelFileInitialOffset` | `Resumables$AnonymousResumable@...` | Internal read-offset/state marker used by resume strategies. Ignore it in normal routes. |
| `CamelFileLastModified` | `1793658798638` | File's last-modified time, in epoch milliseconds. |
| `CamelFileLength` | `37` | File size in bytes. |
| `CamelFileName` | `HELLOWORLD.json` | File name relative to the endpoint's starting directory (includes sub-folders). |
| `CamelFileNameConsumed` | `HELLOWORLD.json` | Name at the moment it was consumed; unchanged even if Camel later moves/renames the file. |
| `CamelFileNameOnly` | `HELLOWORLD.json` | File name only, with no directory part. |
| `CamelFileParent` | `camel-microservice-a/files/input` | Parent directory of the consumed file. |
| `CamelFilePath` | `camel-microservice-a/files/input/HELLOWORLD.json` | Path as resolved for the endpoint (relative in this example). |
| `CamelFileRelativePath` | `HELLOWORLD.json` | Path relative to the endpoint's starting directory. |
| `CamelMessageTimestamp` | `1793658798638` | Message timestamp in epoch milliseconds. For the file consumer Camel sets it to the file's last-modified time, so it matches `CamelFileLastModified`. |

Handy ways to use them:

```java
from("file:camel-microservice-a/files/input")
    .log("Consumed ${header.CamelFileNameOnly} (${header.CamelFileLength} bytes)")
    .filter(header("CamelFileNameOnly").endsWith(".json"))
    .to("...");
```

```java
String name = exchange.getIn().getHeader("CamelFileNameOnly", String.class);
Long size = exchange.getIn().getHeader("CamelFileLength", Long.class);
```

Dump every header on a message with:

```java
.log("${headers}")
```

Notes:

- These `CamelFile*` headers are set on the **consumer** side. A `to("file:...")`
  producer instead sets `CamelFileNameProduced` (the absolute path that was written).
- The File Language gives you the same data without Java, e.g. `${file:onlyname}`,
  `${file:length}`, `${file:lastModified}`.

> Background / notes live in `ActiveMQ-Guide.md`.
