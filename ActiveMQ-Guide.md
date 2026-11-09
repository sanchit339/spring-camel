# ActiveMQ Guide

Everything about how ActiveMQ is wired into this project.

## 1. Broker (Docker)

```bash
docker run -p 61616:61616 -p 8161:8161 rmohr/activemq
```

Ports:

- `61616` — ActiveMQ broker port (OpenWire). This is the port Camel/Spring Boot connects to for sending and receiving messages.
- `8161` — ActiveMQ web admin dashboard: http://localhost:8161
- Dashboard credentials: username `admin`, password `admin`

## 2. Maven dependency

ActiveMQ support was added to `camel-microservice-a/pom.xml` via the Camel Spring Boot starter:

```xml
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-activemq-starter</artifactId>
    <version>${camel.version}</version>
</dependency>
```

This starter brings in the ActiveMQ component (plus the `activemq-client` / JMS libraries) so routes can use `from("activemq:...")` and `to("activemq:...")` without adding the component manually.

## 3. Connection settings (`application.properties`)

Added to `camel-microservice-a/src/main/resources/application.properties` to establish the broker connection:

```properties
spring.activemq.broker-url=tcp://localhost:61616
```

This tells Spring Boot's ActiveMQ auto-configuration where the broker is listening (`tcp://localhost:61616` = the `61616` port mapped in the Docker command above). If the broker runs elsewhere, only this URL needs to change.

## 4. Queues are created automatically

You do **not** have to create a queue in the ActiveMQ dashboard first. Queues are created automatically the first time a message is sent to (or consumed from) a destination, using whatever name you put in the route URI.

For example, this route:

```java
from("timer:active-mq-timer?period=10000")
        .transform().constant("My message for ActiveMQ")
        .to("activemq:my-activemq-queue");
```

creates a queue named **`my-activemq-queue`** on the broker automatically — it will then show up under **Queues** in the dashboard at http://localhost:8161.

The same applies when consuming:

```java
from("activemq:my-activemq-queue")
        .log("Received: ${body}");
```

If the queue does not exist yet, ActiveMQ creates it (empty) as soon as the consumer starts.

### Naming notes

- The queue name is simply the part after `activemq:` — `activemq:orders`, `activemq:my-queue`, etc.
- Producer and consumer must use the **exact same name** (case-sensitive) to talk to each other.
- Sending to `activemq:queue:orders` (explicit `queue:` prefix) also works; without a prefix ActiveMQ defaults to a queue. Use `activemq:topic:orders` for a topic instead.
- A queue with no consumers keeps messages until someone consumes them; a topic drops messages when no subscriber is active.

## 5. JMS message vs Camel body (`mapJmsMessage`)

A JMS consumer receives a **`jakarta.jms.Message`** (for ActiveMQ TextMessages
that is `org.apache.activemq.command.ActiveMQTextMessage`). Camel then decides
what the route's **body** should be.

### Consumer side (default: mapping enabled)

`mapJmsMessage` defaults to **`true`**, so Camel's `JmsBinding` converts the JMS
message into a normal Java body **before your first processor runs**:

| JMS message | Camel body type |
| --- | --- |
| `TextMessage` | `java.lang.String` |
| `BytesMessage` | `byte[]` |
| `MapMessage` | `java.util.Map` |
| `ObjectMessage` | the deserialized `Serializable` object (blocked by default — see below) |

```text
ActiveMQ broker
   │  delivers ActiveMQTextMessage
   ▼
Camel JMS consumer (JmsBinding, mapJmsMessage=true)
   │  TextMessage.getText()
   ▼
Exchange body = java.lang.String
   │
   ▼
.process(...)   ← processors only ever see the converted body
```

So a processor like this logs the **converted** type, not the raw JMS type:

```java
from("activemq:csv-processor")   // mapJmsMessage defaults to true
        .process(exchange -> {
            Object body = exchange.getIn().getBody();
            String bodyType = body == null ? "null" : body.getClass().getName();
            exchange.getIn().setHeader("BodyType", bodyType);
        })
        .log("type: ${header.BodyType}, body: ${body}");
```

Expected: `type: java.lang.String, body: ...`

### Seeing the raw JMS message

Set `mapJmsMessage=false` on the consumer to skip the conversion. The body then
stays a JMS object, and **you** are responsible for extracting its content:

```java
from("activemq:csv-processor?mapJmsMessage=false")
        .process(exchange -> {
            Object body = exchange.getIn().getBody();
            String bodyType = body == null ? "null" : body.getClass().getName();
            exchange.getIn().setHeader("BodyType", bodyType);
        })
        .log("raw JMS type: ${header.BodyType}, body: ${body}");
```

Expected: `raw JMS type: org.apache.activemq.command.ActiveMQTextMessage`

To read the text from it:

```java
if (body instanceof jakarta.jms.TextMessage textMessage) {
    String text = textMessage.getText();
}
```

| Setting | Body seen in the route | Logs |
| --- | --- | --- |
| `mapJmsMessage=true` (default) | `java.lang.String` | Type **after** Camel converts it |
| `mapJmsMessage=false` | `jakarta.jms.TextMessage` | **Raw** JMS message type |

### Producer side (body → JMS message type)

The reverse also applies: Camel picks the outgoing JMS message type from the body.

| Camel body | JMS message created |
| --- | --- |
| `String` | `TextMessage` |
| `byte[]` / `ByteBuffer` | `BytesMessage` |
| `Map` | `MapMessage` |
| any other `Serializable` (e.g. `List`) | `ObjectMessage` |

This is why sending a split CSV row (`List<String>`) without
`.convertBodyTo(String.class)` failed with:

```text
JMS ObjectMessage is disabled by default for security reasons
```

`ObjectMessage` is disabled since Camel 4.18 (`objectMessageEnabled=false`,
CAMEL-23373). Prefer converting to `String`/JSON, or enable it explicitly on
**both** producer and consumer.

## 6. Troubleshooting: file sent but receiver gets nothing

If the sender logs nothing and the receiver never fires, check **where the app's working directory points**.

`from("file:files/json")` is a *relative* path, so it is resolved against the process working directory:

- `./mvnw spring-boot:run` from `camel-microservice-a/` → watches `camel-microservice-a/files/json/` ✅
- IntelliJ with the repo root (`apache-camel/`) as the project → watches `apache-camel/files/json/` ❌ (Camel silently creates that empty folder)

`FileRouter` handles both cases by prefixing `camel-microservice-a/` when it detects the repo root:

```java
private static final String MODULE_PREFIX =
        Files.isDirectory(Path.of("camel-microservice-a")) ? "camel-microservice-a/" : "";

from("file:" + MODULE_PREFIX + "files/json")
        .log("${body}")
        .to("activemq:inbound-queue");
```

Checklist when a message does not arrive:

1. Broker is up: `docker ps` shows `rmohr/activemq`, ports `61616` and `8161`.
2. Sender consumed the file — it disappears from `files/json/` and appears under `files/json/.camel/`.
3. Queue counters moved on the broker:
   ```bash
   curl -s -u admin:admin \
     "http://localhost:8161/api/jolokia/read/org.apache.activemq:brokerName=localhost,destinationName=inbound-queue,destinationType=Queue,type=Broker/QueueSize,EnqueueCount,DequeueCount,ConsumerCount"
   ```
   `EnqueueCount` increments when the sender sends, `DequeueCount` when the receiver consumes.
4. Producer and consumer use the **same** queue name.

## 7. Quick verification

1. Start the broker with the Docker command above.
2. Run `./mvnw spring-boot:run` in `camel-microservice-a`.
3. Open http://localhost:8161 (admin / admin) → **Queues** → your queue name appears if a route used it.
