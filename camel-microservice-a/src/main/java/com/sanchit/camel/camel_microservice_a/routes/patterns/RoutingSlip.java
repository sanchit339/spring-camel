package com.sanchit.camel.camel_microservice_a.routes.patterns;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class RoutingSlip extends RouteBuilder {

    // The slip: endpoints are visited in this exact order, like stops on an itinerary.
    // Prefix the stops (slip-*) so they do not clash with direct: endpoints used by
    // other routes - two routes cannot consume the same direct: endpoint.
    private static final String SLIP = "direct:slip-validate,direct:slip-transform,direct:slip-send";

    @Override
    public void configure() throws Exception {

        // includeMetadata=true is required in Camel 4.x for CamelTimerCounter
        // to be set at all (the timer's metadata option defaults to false).
        from("timer:msgSender?period=1000&includeMetadata=true")
                .routeId("slip-route")
                // Give every tick a simple message to carry through the slip.
                // CamelTimerCounter is an exchange property (the tick number), so we read
                // it with ${exchangeProperty...}, not ${header...}.
                .setBody(simple("message-${exchangeProperty.CamelTimerCounter}"))
                // Put the slip in a header. routingSlip reads it once, before routing starts.
                .setHeader("mySlip", constant(SLIP))
                .routingSlip(header("mySlip"));

        // 1st stop: validate
        from("direct:slip-validate")
                .log("1. validate: ${body}");

        // 2nd stop: simple string transformation
        from("direct:slip-transform")
                .setBody(simple("${body} -> transformed"))
                .log("2. transform: ${body}");

        // 3rd stop: hand off to a dedicated endpoint
        from("direct:slip-send")
                .log("3. send: ${body}")
                .to("log:dummyMsgSend");
    }
}
