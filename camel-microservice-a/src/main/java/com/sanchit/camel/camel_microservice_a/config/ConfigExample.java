package com.sanchit.camel.camel_microservice_a.config;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Example: read configuration from application.properties inside a Camel route.
 *
 * <p>Two different syntaxes appear in routes - do not mix them up:
 *
 * <pre>
 * {{property-name}}   config placeholder, resolved ONCE at startup
 * ${expression}       runtime message expression, evaluated for every exchange
 * </pre>
 */
@Component // Spring Boot finds RouteBuilder beans automatically; no extra wiring.
public class ConfigExample extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Timer default period is 1000 ms; 5000 keeps the demo log quieter.
        from("timer:timer-example?period=5000")
                .routeId("config-example-route")
                // A timer tick has a null body, so give it something to log.
                .setBody(constant("config example tick"))
                .log("${body}")
                // {{logger-path}} is replaced with the value of "logger-path"
                // from application.properties, i.e. "log:example-log".
                // Use {{logger-path:log:fallback}} to supply a default value.
                // "log:" is a producer-only component: it writes the exchange to
                // the logger named "example-log" (no from("log:...") route needed).
                .to("{{logger-path}}");
    }
}
