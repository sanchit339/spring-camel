# Apache Camel EIP Patterns

Notes and examples collected while learning the Enterprise Integration Patterns
(EIPs) used in this project. Java DSL examples live under
`camel-microservice-a/.../routes/patterns/`.

Patterns covered so far:

- Pipeline — `PipelinePattern.java`
- Multicast — `EipPatternsRouter.java`
- Splitter — `CsvSplitter.java`
- Aggregator — `AggregatorPattern.java`
- Routing Slip — `RoutingSlip.java`
- Dynamic Router — `DynamicRoutePattern.java`
- Configuration from `application.properties` & logging — `ConfigExample.java` (see below)
- `direct:` component — linking routes (see below)
- DSL note: `method(...)` vs `.bean(...)` (see below)

## Pipeline EIP

Example: `PipelinePattern.java`.

A Camel route is **already a pipeline by default**: steps chained with `.` run
sequentially on the same exchange, and each step's body/headers become the input
of the next. So `.pipeline()` is not required for plain sequential flow — it is a
**grouping/composition** construct. `PipelinePattern.java` uses it explicitly just
to make the concept visible.

What it does:

- Runs its child processors one after another on the **same exchange** (no copy).
- If a step throws, the remaining steps are skipped (subject to error handling).

How it differs from other EIPs:

| EIP | Behavior |
| --- | --- |
| `pipeline` | one exchange, steps in sequence |
| `multicast` | copies the exchange to multiple endpoints |
| `split` | splits the body, one exchange per fragment |
| `recipientList` | dynamically chooses the endpoints |

Where it is genuinely useful — grouping multiple steps where the DSL expects a
single processor:

```java
// Scope a group inside a choice branch (especially useful in XML DSL)
.choice()
    .when(simple("${body} contains 'USD'"))
        .pipeline()
            .log("USD branch")
            .to("bean:usdService")
            .to("direct:audit")
        .end()
    .otherwise()
        .to("direct:domestic")
.end();
```

```java
// Scope error handling to a group of steps
.doTry()
    .pipeline()
        .to("direct:validate")
        .to("direct:transform")
        .to("direct:persist")
    .end()
.doCatch(Exception.class)
    .log("Failed in the grouped steps: ${exception.message}")
.end();
```

```java
// Make a long route readable by grouping logical sections
.pipeline()   // validation section
    .to("direct:validate")
    .to("direct:enrich")
.end()
.pipeline()   // persistence section
    .to("direct:transform")
    .to("direct:save")
.end();
```

> It does **not** mean "execute only once" or "single execution section"; it only
> groups and orders processors.

## Direct component (`direct:`) — linking routes

Used in `ChoiceBasedRouting.java` (active) and `PipelinePattern.java` (currently
commented out with `//@Component`).

Both use the same pattern:

```java
// Producer: sends the exchange into the direct endpoint
.to("direct://log-route")

// Consumer: receives it on the same in-memory endpoint
from("direct:log-route")
    .log("${messageHistory}")
    .log("Processing of the file finished");
```

- `direct:` is an **in-memory, synchronous** endpoint — it calls the consuming
  route in the **same thread** (no queue, no broker).
- The name after `direct:` (`log-route`) is just the **endpoint/route name** that
  links the two. `direct:log-route` and `direct://log-route` point to the same
  endpoint.
- The producer (`.to(...)`) sends the exchange into the endpoint; the matching
  `from("direct:...")` route consumes it. Both routes must be in the **same**
  Camel context.
- Because it is synchronous, the producer waits for the consumer route to finish
  before continuing. For asynchronous, fire-and-forget behaviour use `seda:`
  instead.
- By default a `direct:` endpoint is meant to have a **single consumer**. If two
  routes declare the same `from("direct:log-route")`, Camel fails at startup with
  a duplicate/multiple-consumer error.

## Multicast EIP

Example: `EipPatternsRouter.java`.

```java
from("timer:multicast-timer?period=1000")
        .multicast()
        // this sends a copy of the msg to given routes ...
        .to("log:KafkaQueue", "log:differentMicroservice", "log:others");
```

What it does:

- Sends the **same message to multiple endpoints** (here three `log:` endpoints).
- Each recipient gets its **own copy of the exchange**, so changes made in one
  branch do not leak into the others.
- All recipients are invoked for **every** trigger — the timer fires every
  `1000` ms, so the multicast runs once per second.
- `.to("log:A", "log:B", "log:C")` is the multicast's set of outputs; it is
  equivalent to chaining `.to("log:A").to("log:B").to("log:C")` inside the
  multicast block.

Useful options:

| Option | Effect |
| --- | --- |
| `parallelProcessing()` | Invoke the recipients concurrently (thread pool). |
| `stopOnException()` | Stop invoking the remaining recipients when one throws. |
| `aggregationStrategy(...)` | Control how replies are combined; by default the last reply wins. |
| `timeout(...)` | Limit how long the multicast waits for recipients. |

Contrast with related EIPs:

| EIP | Behavior |
| --- | --- |
| `multicast` | Sends a copy of the same exchange to a **fixed set** of endpoints. |
| `recipientList` | Endpoints are chosen **dynamically** at runtime (e.g. from a header). |
| `split` | Breaks one message into **multiple messages**, one exchange per fragment. |
| `pipeline` | **One** exchange, processors run in sequence (no fan-out). |

> `EipPatternsRouter` is a `@Component`, so this route starts with the app. The
> `timer:` consumer makes it fire repeatedly, unlike the file-based pattern
> examples which run when a file arrives.

## Splitter EIP

Example: `CsvSplitter.java`.

```java
@Autowired
private CsvSplitterUtil csvSplitterUtil;

from("file:camel-microservice-a/files/csv")
        .convertBodyTo(String.class)
        .split(body(), ",")                 // 1) tokenize the text by ","
        .split(method(csvSplitterUtil))     // 2) custom split -> List<String>
        .to("activemq:csv-processor");
```

```java
@Component
class CsvSplitterUtil {
    public List<String> splitInput(String body) {
        // add custom split logic and return the list
        return List.of("a", "b", "c");
    }
}
```

### What it does

The Splitter breaks **one message into multiple messages** and routes each
fragment through the steps inside the `.split()...end()` block:

```text
one exchange (file text)
        │
        ▼
   split by ","
        │
        ├── token 1 ──► split(method) ──► a, b, c ──► activemq:csv-processor
        ├── token 2 ──► split(method) ──► a, b, c ──► activemq:csv-processor
        └── token 3 ──► split(method) ──► a, b, c ──► activemq:csv-processor
```

- Each fragment is wrapped in its **own copy of the exchange**, so changing the
  body/headers for one fragment does not affect the others.
- The original message continues after the splitter once all fragments finish.
- Splits can be **nested** — the example splits by delimiter, then splits each
  token again using a bean that returns a `List<String>`.

### Ways to supply the split value

| Form | Meaning |
| --- | --- |
| `.split(body())` | Body is a `Collection`, `Iterator`, array, etc. One exchange per element. |
| `.split(body(), ",")` | Treat the body as a `String` and tokenize it with the delimiter. Equivalent to `.split(body().tokenize(","))`. |
| `.split(method(bean))` | Bean returns the collection/iterator/array to split on. |
| `.split(simple("${body.items}"))` | Expression/Simple language returns the collection. |
| `.split().tokenize(",")` | Alternative tokenizer DSL. |

`.split(body(), ",")` maps to `split(Expression, String delimiter)`, which sets
`SplitDefinition.delimiter`; at runtime Camel calls
`ObjectHelper.createIterator(value, delimiter)`.

### Useful options

| Option | Effect |
| --- | --- |
| `parallelProcessing()` | Process fragments concurrently. |
| `streaming()` | Stream large collections instead of loading/copying everything. |
| `stopOnException()` | Stop splitting/processing on the first failure. |
| `aggregationStrategy(...)` | Control how fragment replies are combined into the original exchange. |
| `delimiter("...")` | Set the delimiter when not passed to `split(...)`. |
| `shareUnitOfWork()` | Share the unit of work (and error handling) between fragments. |

### Notes / gotchas

- By default Camel uses a "last reply wins" aggregation: after the splitter, the
  original exchange body is the reply from the **last** fragment, not the
  collection anymore.
- Since each fragment becomes a separate message, sending it to JMS chooses the
  message type from the **fragment's body type**: `String` → `TextMessage`,
  other `Serializable` (e.g. `List`) → `ObjectMessage`, which is disabled by
  default (see `ActiveMQ-Guide.md`, Camel 4.18 `objectMessageEnabled`).
  That is why the route starts with `.convertBodyTo(String.class)`.
- The `.unmarshal().csv()` version (commented out in the file) gives
  `List<List<String>>`; `.split(body())` then splits the **outer** list, so each
  fragment is one CSV row (`List<String>`). The active example is a simpler
  delimiter-based demo, not real CSV parsing.
- `method(csvSplitterUtil)` uses Camel bean binding: the bean method receives the
  current body (`String`) and its return value must be a splittable type.
- No `.end()` is needed at the end of the route, but use `.end()` when more
  route steps should run **after** the split block.

## Aggregator EIP

Example: `AggregatorPattern.java`.

The Aggregator is the **fan-in** counterpart to the Splitter: it collects
multiple exchanges into **one** exchange, grouped by a correlation key.

```java
from("file:camel-microservice-a/files/json")
        .routeId("aggregator-pattern-route")
        .unmarshal().json(CurrencyExchange.class)
        // Correlation key: exchanges with the same body.id are grouped together.
        .aggregate(simple("${body.id}"), new Strategy())
            // For learning: emit a group after 2 messages, or after 5s of inactivity.
            .completionSize(2)
            .completionTimeout(5000)
            .log("Aggregated exchange: ${body}")
            // Convert the aggregated CurrencyExchange back to JSON (pretty printed).
            .marshal().json(JsonLibrary.Jackson, true)
            // Write it to an output folder so the aggregated JSON can be inspected.
            .setHeader(Exchange.FILE_NAME, simple("aggregated-${header.CamelFileName}"))
            .to("file:camel-microservice-a/files/json-output")
        .end();
```

```java
class Strategy implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        // First exchange in the group: nothing to merge yet, so start with it.
        if (oldExchange == null) {
            return newExchange;
        }

        CurrencyExchange oldBody = oldExchange.getIn().getBody(CurrencyExchange.class);
        CurrencyExchange newBody = newExchange.getIn().getBody(CurrencyExchange.class);

        if (oldBody != null && newBody != null
                && oldBody.getConversionMultiple() != null
                && newBody.getConversionMultiple() != null) {
            // Example merge: add the conversion multiples together.
            BigDecimal total = oldBody.getConversionMultiple()
                    .add(newBody.getConversionMultiple());
            oldBody.setConversionMultiple(total);
        }

        // Return the accumulated exchange for the next aggregation round.
        return oldExchange;
    }
}
```

### What it does

```text
exchange(id=1) ─┐
exchange(id=1) ─┼──► aggregate by ${body.id} ──► one exchange(id=1)
exchange(id=1) ─┘

exchange(id=2) ─┐
exchange(id=2) ─┴──► aggregate by ${body.id} ──► one exchange(id=2)
```

- The **correlation expression** (`simple("${body.id}")`) decides which group an
  exchange belongs to. Same key → same group.
- Exchanges are **stored** until a completion condition fires. Only then is the
  aggregated result sent to the aggregate's output steps.
- `AggregationStrategy` is where the merge happens.
- `oldExchange` is `null` for the **first** exchange of a group; return
  `newExchange` to start the group. For every later exchange, merge into
  `oldExchange` and return it.
- The route's `routeId` and the explicit `.end()` are optional, but `.end()` is
  needed when steps should run **after** the aggregate block.

### Completion conditions

The default completion is **never** — if you don't set one, the aggregator waits
forever and nothing is emitted. Common options:

| Option | When the group completes |
| --- | --- |
| `completionSize(n)` | After `n` exchanges in the group. |
| `completionTimeout(ms)` | After `ms` of inactivity (no new exchange). |
| `completionInterval(ms)` | Every `ms`, regardless of activity. |
| `completionPredicate(...)` | When the predicate matches the aggregated exchange. |
| `completionOnNewCorrelationGroup()` | When an exchange with a new key arrives. |
| `completionFromBatchConsumer()` | When a batch consumer (e.g. file) finishes its batch. |

You can combine conditions (as in the example): size **or** timeout, whichever
fires first. The default in-memory repository is lost on restart; use a
persistent `AggregationRepository` (JDBC/JPA) if aggregation must survive
restarts.

### Tumbling vs sliding windows (what `completionSize` really means)

`completionSize` is **not a sliding window**. It is a **tumbling** window:
a fixed-size batch that is emitted and then **reset**.

Think of a supermarket checkout:

- Each **correlation key** (`${body.id}`) is its own checkout lane / bucket.
- Messages wait in the lane.
- When a lane has 2 items, the cashier rings them up and **empties the lane**.
- The next message in that lane starts a **brand new** batch of 2.

So `completionSize(2)` means "2 messages per output", not "keep adding a
running total forever". After a batch is emitted, that key starts from zero
again.

#### Terms in plain English

| Term | Easy meaning |
| --- | --- |
| **Correlation key** | The label on the bucket. Same key = same bucket. Here it is `body.id`. |
| **Bucket / group** | The messages currently waiting for one key. |
| **Window** | Another word for "the messages currently in the bucket". |
| **Completion** | The moment Camel decides the bucket is finished and **emits** one result. |
| **Tumbling window** | Fixed batches that **do not overlap**: `[1,2]`, `[3,4]`, `[5,6]`. The bucket is emptied after each batch. |
| **Sliding window** | Overlapping batches that **share** messages: `[1,2]`, `[2,3]`, `[3,4]`. Camel does **not** do this by default. |
| **Partial group** | A group emitted by timeout with fewer messages than `completionSize`. |

#### Tumbling (what this route does)

```text
id=1:  msg1  msg2 | msg3  msg4 | msg5  msg6
       └─[1,2]─┘    └─[3,4]─┘    └─[5,6]─┘
          emit         emit         emit
```

Each emitted batch is independent. `msg2` is **not** reused in the next batch.

#### Sliding (what people often confuse it with)

```text
id=1:  msg1  msg2  msg3  msg4
       └─[1,2]─┘
             └─[2,3]─┘
                   └─[3,4]─┘
```

Here `msg2` and `msg3` appear in **two** outputs. The default Camel aggregator
removes a group once it completes, so it cannot slide. To get a sliding window
you must keep the last exchange yourself (custom strategy/repository and
re-feed it into the aggregator).

#### When the size and timeout race

`completionSize(2)` and `completionTimeout(5000)` are combined with **"first one
wins"**, and both are evaluated **per correlation key**:

- 2 matching messages arrive → emit immediately.
- No new message for that key for 5 seconds → emit **whatever is in the
  bucket**, even if it is only 1 message.

That second case is the **partial group**: the batch is smaller than
`completionSize` because the timeout fired first. It is also why `Strategy`
must handle `oldExchange == null` — a 1-message group never gets a merge call.

#### Worked example with the sample files

| Input | `id` | `conversionMultiple` | What happens |
| --- | --- | --- | --- |
| `currency-1.json` | 1 | 10 | Opens bucket `id=1`, waits for a second message. |
| `currency-2.json` | 1 | 20 | Bucket `id=1` reaches size 2 → **emit** `10 + 20 = 30`. |
| `currency-3.json` | 2 | 40 | Opens bucket `id=2`. No second `id=2` message, so after 5s the timeout fires → **emit 40** as a partial group of 1. |

Result: **2 output files**, `aggregated-currency-1.json` (value `30`) and
`aggregated-currency-3.json` (value `40`):

```json
// aggregated-currency-1.json
{ "id": 1, "from": "USD", "to": "INR", "conversionMultiple": 30 }
```

```json
// aggregated-currency-3.json
{ "id": 2, "from": "EUR", "to": "INR", "conversionMultiple": 40 }
```

Notice that `currency-2.json` finished the `id=1` batch, so it is **not**
carried into another batch. If a fourth file with `id=1` arrived, it would
start a fresh `id=1` batch and wait for a partner.

#### Running the example

Input files live in `camel-microservice-a/files/json/`; aggregated output is
written to `camel-microservice-a/files/json-output/` as
`aggregated-<original-name>.json`.

The file path is **relative to the process working directory**, so run from the
repo root:

```bash
./camel-microservice-a/mvnw -f camel-microservice-a/pom.xml spring-boot:run \
  -Dspring-boot.run.workingDirectory="$(pwd)"
```

Running `./mvnw` from inside `camel-microservice-a/` makes the path resolve to
`camel-microservice-a/camel-microservice-a/files/json` and nothing is consumed.
Camel also moves consumed inputs into `files/json/.camel/`, so re-create the
test files before another run.

### Useful options

| Option | Effect |
| --- | --- |
| `aggregationStrategy(...)` | Bean/instance that merges exchanges. |
| `aggregationStrategyMethodName(...)` | Use a method on a bean as the strategy. |
| `parallelProcessing()` | Run aggregation concurrently. |
| `executorService(...)` | Custom thread pool for parallel aggregation. |
| `aggregationRepository(...)` | Where in-progress groups are stored. |
| `discardOnCompletionTimeout()` | Drop the group instead of emitting it on timeout. |
| `preComplete(...)` | Pre-process an exchange before it is added to a group. |

### Notes / gotchas

- The correlation key should normally be non-null; `null` keys can end up in the
  same group depending on the repository.
- Because the example mutates `oldBody` in place, the aggregated
  `CurrencyExchange` accumulates the `conversionMultiple` values.
- `Strategy` doesn't need `@Component` here because it is passed directly with
  `new Strategy()`; use a Spring bean only if you want injection or reuse.
- For JSON input on microservice-a, the `camel-jackson3-starter` dependency is
  required (see the POM) — otherwise `.unmarshal().json(...)` has no `jackson`
  data format at runtime.
- Aggregator (fan-in) vs Splitter (fan-out) vs Multicast (fan-out of copies):
  use Aggregator when several messages must be **combined into one**.

## Routing Slip EIP

Example: `RoutingSlip.java`.

A Routing Slip is a **to-do list of endpoints attached to the message**. Camel
reads the list **once**, then sends the exchange through those endpoints **one
by one, in order** — a pipeline whose steps are chosen at runtime instead of
being hard-coded.

### Intuitive example

Think of a courier with a slip of paper listing the stops:

```text
Slip: warehouse -> bank -> office -> customer
```

The courier visits them in exactly that order. The list is decided **before** the
trip starts; it does not change based on traffic. That is different from a
**Dynamic Router**, which decides the next stop *after every step* and can loop
back.

### The example route

```java
// The slip: endpoints are visited in this order.
private static final String SLIP = "direct:slip-validate,direct:slip-transform,direct:slip-send";

from("timer:msgSender?period=1000&includeMetadata=true")
        .routeId("slip-route")
        .setBody(simple("message-${exchangeProperty.CamelTimerCounter}"))
        // Put the slip in a header. routingSlip reads it once, before routing starts.
        .setHeader("mySlip", constant(SLIP))
        .routingSlip(header("mySlip"));

from("direct:slip-validate")
        .log("1. validate: ${body}");

from("direct:slip-transform")
        .setBody(simple("${body} -> transformed"))
        .log("2. transform: ${body}");

from("direct:slip-send")
        .log("3. send: ${body}")
        .to("log:dummyMsgSend");
```

Flow:

```text
timer -> validate -> transform -> send -> log:dummyMsgSend
         └──── same exchange, each output feeds the next ────┘
```

### How it works

- The slip is computed from an **Expression** and can be a `String`
  (comma-delimited), `Collection`, array, `Iterator`, or `Iterable`.
- It is computed **once**, up front. The slip is **not** re-evaluated between
  steps.
- The exchange is routed **pipeline-style**: each endpoint receives the output of
  the previous one (body/headers flow through).
- An **empty slip** is valid — the message simply passes through.
- Progress is available in the `CamelSlipEndpoint` exchange property
  (`Exchange.SLIP_ENDPOINT` in older code).
- `.ignoreInvalidEndpoints()` logs and skips a bad URI instead of throwing.
- `.delimiter("|")` changes the separator if your URIs contain commas.

### Routing Slip vs related EIPs

| EIP | Who chooses the destinations | When | Flow |
| --- | --- | --- | --- |
| `pipeline` | you, in code | design time | sequential |
| `routingSlip` | expression (header/body/bean) | once, before routing | sequential; output → next |
| `dynamicRouter` | bean/predicate | after every step | sequential; can loop/recalculate |
| `recipientList` | expression | once | fan-out to all (not chained) |
| `multicast` | you, in code | design time | copies to all |

### Building the slip dynamically

The slip can come from a bean, so different messages take different paths:

```java
.setHeader("mySlip", method(slipBuilder, "buildSlip"))
.routingSlip(header("mySlip"));
```

```java
public List<String> buildSlip(CurrencyExchange body) {
    if (body.getConversionMultiple().compareTo(new BigDecimal("50")) > 0) {
        return List.of("direct:audit", "direct:persist");
    }
    return List.of("direct:persist");
}
```

### Common mistake

```java
String slip = "direct:slip-validate,direct:slip-transform,direct:slip-send";
.routingSlip(simple("slip"));   // WRONG: literal endpoint name "slip"
```

`simple("slip")` is just the literal text `slip`, not the variable. Use the
variable as a constant, or put it in a header and read the header:

```java
.routingSlip(constant(slip));              // variable directly
.setHeader("mySlip", constant(slip))
.routingSlip(header("mySlip"));            // via header (shown above)
```

## Dynamic Router EIP

Example: `DynamicRoutePattern.java`.

The Dynamic Router is a **Routing Slip that is recomputed after every hop**.
Camel asks an Expression (usually a bean method) for the next endpoint, routes
there, then asks again. Returning `null` ends the routing.

### Intuitive example

| EIP | Analogy |
| --- | --- |
| Routing Slip | A fixed itinerary: `warehouse -> bank -> office` |
| Dynamic Router | A GPS: after each stop it decides the next one, and may change route or loop |

### Why you need it

Use it when the next step is **not known up front** and depends on:

- the current message (e.g. amount > 50 goes to audit),
- the result of the previous step (success -> send, failure -> retry),
- external state (database, rules engine, tenant config),
- a workflow/state machine that can branch or loop.

Without it you end up with deeply nested `choice()` blocks and duplicated logic.
With it, one bean owns the decision and the route stays small.

### The example route

```java
// Trigger with alternating normal/high-value messages.
from("timer:dynamicRouterTrigger?period=5000&includeMetadata=true")
        .routeId("dynamic-router-trigger")
        .process(exchange -> {
            // CamelTimerCounter is an EXCHANGE PROPERTY, not a message header:
            // it is the tick number (1, 2, 3, ...), one per timer period.
            Long counter = exchange.getProperty(Exchange.TIMER_COUNTER, Long.class);
            BigDecimal multiple = counter % 2 == 0
                    ? BigDecimal.valueOf(10)
                    : BigDecimal.valueOf(60);
            exchange.getIn().setBody(new CurrencyExchange(counter, "USD", "INR", multiple));
        })
        .to("direct:start");

from("direct:start")
        .routeId("dynamic-router-route")
        .dynamicRouter(method(dynamicRouterBean, "next"));

from("direct:validate")
        .log("1. Validate ${body}");

from("direct:audit")
        .log("2a. audit (High Value) : ${body}");

from("direct:enrich")
        .log("2b. enrich (normal) : ${body}");

from("direct:send")
        .log("3. Send ${body}");
```

```java
@Component
class DynamicRouterBean {
    public String next(CurrencyExchange body,
                       @ExchangeProperties Map<String, Object> properties) {
        // State lives on the Exchange, not in a bean field.
        Object current = properties.get("dynamicRouter.step");
        int step = current == null ? 0 : (Integer) current;
        properties.put("dynamicRouter.step", step + 1);

        return switch (step) {
            case 0 -> "direct:validate";
            case 1 -> body.getConversionMultiple().compareTo(new BigDecimal("50")) > 0
                    ? "direct:audit"
                    : "direct:enrich";
            case 2 -> "direct:send";
            default -> null; // no more hops -> routing ends
        };
    }
}
```

### Walkthrough

```text
conversionMultiple = 10 -> validate -> enrich -> send -> null (done)
conversionMultiple = 60 -> validate -> audit  -> send -> null (done)
```

The bean is called **once before each hop**, and the final call returns `null`
to stop. Each timer tick starts a new exchange, so the step counter starts at 0
again.

### Dynamic Router vs related EIPs

| EIP | When destinations are decided | Flow | When it stops |
| --- | --- | --- | --- |
| `recipientList` | once, by an expression | fan-out to all | after all recipients |
| `routingSlip` | once, before routing | sequential chain | when the slip is empty |
| `dynamicRouter` | **after every step** | sequential chain | when the bean returns `null` |
| `pipeline` | design time | sequential chain | when steps end |

### Gotchas

- **Always return `null` to stop**, otherwise the router loops forever.
- Store state on **exchange properties**, not bean fields. The bean is a singleton
  shared by concurrent messages; a field would mix up messages.
- Keep the method a **decision function**; do the real work in the target
  endpoints.
- A hop can return multiple endpoints (`"direct:a,direct:b"`); they run in order
  before the router is asked again.
- `.ignoreInvalidEndpoints()` logs and skips a bad URI instead of failing.
- Destinations are strings, so you lose the static route diagram and some
  compile-time safety. Use it only where the flexibility is needed.
- `CamelSlipEndpoint` / `Exchange.SLIP_ENDPOINT` shows the current endpoint.
- Camel also offers an `@DynamicRouter` annotation on a bean method as an
  alternative to calling `.dynamicRouter(method(...))` in the route.

## Bean parameter binding: `@ExchangeProperties`

`@ExchangeProperties` injects the **Exchange's property map** into a bean method:

```java
public String next(CurrencyExchange body,
                   @ExchangeProperties Map<String, Object> properties) {
    Object value = properties.get("myKey");
    properties.put("myKey", value);
    return "direct:next";
}
```

Exchange **properties** are a per-exchange scratchpad. They are not the message
body and not message headers.

| Annotation | Injects | Where it lives | Sent over JMS/HTTP? |
| --- | --- | --- | --- |
| `@Body` | message body | message | yes (the body) |
| `@Header("x")` | one header | message | yes |
| `@Headers` | all headers | message | yes |
| `@ExchangeProperty("x")` | one property | exchange | **no** |
| `@ExchangeProperties` | all properties | exchange | **no** |

### Headers vs properties

| | Headers | Exchange properties |
| --- | --- | --- |
| Purpose | message metadata / protocol data | exchange-local state and Camel internals |
| Travels with the message | yes | only inside the same exchange |
| Survives JMS/HTTP | yes (mapped/serialized) | **no** |
| Typical examples | `CamelFileName`, `CamelTimerFiredTime`, `Content-Type` | `CamelTimerCounter`, `CamelAggregatedSize`, `CamelSlipEndpoint`, step counters |
| Bean annotation | `@Header`, `@Headers` | `@ExchangeProperty`, `@ExchangeProperties` |

### Example: `CamelTimerCounter`

When a `from("timer:...")` endpoint fires, it creates a fresh exchange. **In
Camel 4.x the timer only adds the values below when `includeMetadata=true`** —
that option defaults to `false`:

```java
from("timer:tick?period=5000&includeMetadata=true")
```

Then, before your route runs, these values are available:

| Name | Where it lives | Type | Meaning |
| --- | --- | --- | --- |
| `CamelTimerCounter` | exchange **property** | `Long` | tick number: `1`, `2`, `3`, ... |
| `CamelTimerName` | exchange **property** | `String` | timer name (`dynamicRouterTrigger`) |
| `CamelTimerPeriod` | exchange **property** | `Long` | period in ms |
| `CamelTimerFiredTime` | property **and** header | `Date` | when this tick fired |
| `CamelMessageTimestamp` | header | `Long` | same instant, in epoch millis |

`CamelTimerCounter` answers "which tick is this?". It is useful for alternating
behaviour, logging, or building test messages:

```java
// Java: it is a property, so use getProperty (getHeader returns null!)
Long counter = exchange.getProperty(Exchange.TIMER_COUNTER, Long.class);

// Simple DSL: same thing
.setBody(simple("message-${exchangeProperty.CamelTimerCounter}"))
```

The counter is reset when the route (timer consumer) restarts, and each timer
endpoint keeps its own counter. It is **not** a message header, so
`getHeader("CamelTimerCounter", ...)` returns `null` and `${header.CamelTimerCounter}`
renders empty. If `includeMetadata` is left at its default `false`, the property
is missing as well (`null`), so `includeMetadata=true` is required.

### Reading and writing without bean binding

```java
Object value = exchange.getProperty("myKey");
exchange.setProperty("myKey", value);
exchange.removeProperty("myKey");
```

### Where properties help

- **State across repeated calls** — the Dynamic Router step counter.
- **Passing data between processors** without polluting headers.
- **Camel's own metadata** — aggregator (`CamelAggregatedSize`,
  `CamelAggregatedCorrelationKey`, `CamelAggregatedCompletedBy`), routing slip
  (`CamelSlipEndpoint`), and `CamelToEndpoint`.
- **Per-message flags** for error handling / retries.

### Gotchas

- Use `Map<String, Object>`, not `Map<String, String>`; property values can be any
  type. (In `ChoiceBasedRouting.DeciderBean` the maps are typed
  `Map<String, String>`; prefer `Object` values for safety.)
- Prefix your own keys (e.g. `myApp.step` or `dynamicRouter.step`) so you don't
  collide with Camel's `Camel*` properties.
- Properties are **not** propagated across a JMS or HTTP call. Use headers for
  data that must cross a broker/network boundary.

## DSL note: `method(...)` vs `.bean(...)`

These are two different roles in Camel:

- **`method(...)`** builds an **Expression / Predicate** (returns a `ValueBuilder`).
- **`.bean(...)`** is a **Processor** (a step in the route).

Understanding which one an EIP needs answers when to use each.

### `method(...)` — a value supplier

Use it when the DSL expects an **Expression** or **Predicate**: something that
*returns a value* — a boolean, a `List`, a `String`, a key, an endpoint name.

```java
// Predicate: bean returns boolean -> drives when()
.choice()
    .when(method(deciderBean))          // must return boolean
        .log("USD stuff")
    .otherwise()
        .log("other")
.end();

// Predicate: filter()
.filter(method(validator, "isValid"))

// Expression: bean returns a Collection -> split() splits on it
.split(method(orderService, "getLineItems"))
    .to("activemq:line-item-queue")
.end();

// Expression: bean returns a value -> becomes the new body
.setBody(method(mapper, "toDto"))

// Expression: bean returns an endpoint URI / key
.recipientList(method(router, "pickEndpoint"))
```

Signatures (from `BuilderSupport`):

```java
method(Object bean)
method(Object bean, String methodName)
method(Class<?> beanType)
method(Class<?> beanType, String methodName)
```

`method(bean)` without a method name relies on Camel's bean binding to pick a
method from the exchange; with overloads it's safer to pass the name explicitly.

### `.bean(...)` — a processing step

Use it when you want to **run logic on the exchange** (read body/headers, call a
service, set headers, return a new body). It is a processor, not a value.

```java
.bean(deciderBean)                    // invoke bean (auto-picked method)
.bean(deciderBean, "deciderBean")     // explicit method
.bean(OrderService.class, "process")  // create/lookup by class
.bean("orderService", "process")      // by Spring bean name
```

The method's return value becomes the new body (a `void` method leaves it as is).

### Key distinction

| | `method(bean, "m")` | `.bean(bean, "m")` |
| --- | --- | --- |
| Role | Expression / Predicate | Processor (EIP) |
| Used by | `when`, `filter`, `split`, `setBody`, `transform`, `recipientList`, `routingSlip`, ... | route steps |
| Return value | Consumed by the EIP (bool/collection/value) | Becomes the exchange body |
| Typical question | "What value does this produce?" | "What should this step do to the exchange?" |

### Examples from this repo

`ChoiceBasedRouting` uses `when` with a predicate, so `method(...)` is correct:

```java
.when(method(deciderBean))
```

`when` needs a boolean condition; the bean's return value drives the routing
decision. `.bean(deciderBean)` there would be wrong — it is a processor, not a
condition.

CSV split uses an expression directly:

```java
from("file:.../csv")
    .unmarshal().csv()          // body = List<List<String>>
    .split(body())              // expression: split the outer List
        .convertBodyTo(String.class)
        .to("activemq:csv-processor")
    .end();
```

If the collection came from a bean instead:

```java
.split(method(csvService, "rows"))   // bean returns List<List<String>>
    .convertBodyTo(String.class)
    .to("activemq:csv-processor")
.end();
```

### Rule of thumb

- Need a **condition, collection, key, or value** → `method(...)` inside the EIP.
- Need to **do something to the message** as a step → `.bean(...)`.
- If a bean should process each split fragment, put `.bean(...)` **inside** the
  split block, not in the split expression:

```java
.split(body())
    .bean(rowProcessor, "handleRow")   // runs once per fragment
.end();
```

## Configuration from `application.properties` & logging

Example: `ConfigExample.java` + `camel-microservice-a/src/main/resources/application.properties`.

### Reading properties in a route

```java
@Component
public class ConfigExample extends RouteBuilder {
    @Override
    public void configure() {
        from("timer:timer-example?period=5000")
                .setBody(constant("config example tick"))
                .to("{{logger-path}}");   // "log:example-log" from properties
    }
}
```

```properties
logger-path=log:example-log
```

The important distinction:

| Syntax | Name | When it is evaluated | Used for |
| --- | --- | --- | --- |
| `{{logger-path}}` | property placeholder | **once, at startup** | config (URIs, options, names) |
| `${body}`, `${header.x}` | Simple expression | **every exchange** | message data |

Rules of thumb:

- Configuration → `{{...}}`. Message data → `${...}`.
- A default is written after a colon: `{{logger-path:log:fallback}}`.
- If a key has no default and is missing, route startup fails fast.
- The value comes from Spring's `Environment`, so `application.properties`,
  environment variables, and command-line arguments all work.
- Placeholders also work in `from("{{input-endpoint}}")` and component options.

### The `log:` endpoint

`log:example-log` logs the exchange to a logger named `example-log`. It is a
**producer-only** component, so no `from("log:...")` route is needed. The default
level is `INFO`; change it per endpoint (`log:example-log?level=DEBUG`) or via
logging config (`logging.level.example-log=DEBUG`).

### Common property prefixes

| Prefix | Purpose | Example |
| --- | --- | --- |
| `camel.component.<name>.<option>` | configure a Camel component | `camel.component.kafka.brokers=localhost:9092` |
| `camel.main.*` | Camel runtime / standalone options | `camel.main.main-run-controller=true` |
| `spring.*` | Spring Boot and starter options | `spring.activemq.broker-url=tcp://...` |
| anything else | your own keys used via `{{...}}` | `logger-path=log:example-log` |

### Standalone vs Spring Boot web

| Setting | What it does | Needed in this project? |
| --- | --- | --- |
| `camel.main.main-run-controller=true` | keeps the JVM alive when there is no web server | no — `spring-boot-starter-webmvc`/Tomcat already keeps it alive |
| `camel.main.duration-max-seconds=60` | stops the Camel context/routes after 60 seconds (a web server may keep the JVM alive) | only for demos/tests |

> **Camel 4 note:** the old `camel.springboot.*` keys (for example
> `camel.springboot.main-run-controller`) are **not read** by Camel 4.x — use
> `camel.main.*` instead. The option is also `duration-max-seconds` (plural).

### Logging

```properties
# Most to least verbose: TRACE < DEBUG < INFO < WARN < ERROR
logging.level.root=INFO

# Package/class-level loggers: the most specific one wins
logging.level.org.springframework=INFO
logging.level.org.apache.camel.spring.boot=INFO

# Camel internals: DEBUG/TRACE is very chatty, use only while debugging
logging.level.org.apache.camel.impl=DEBUG

# Tune the "log:" component logger name
logging.level.example-log=DEBUG
```

- Prefer setting a **package** logger (`org.apache.camel.impl`) over the root
  logger, so unrelated libraries keep their normal level.
- Leave `org.apache.camel.impl` at `INFO` day to day; switch to `DEBUG` only to
  inspect endpoint/consumer internals.
- The `logging.level.<name>` name can be a package (`org.apache.camel`) or an
  exact logger name (`example-log`).

