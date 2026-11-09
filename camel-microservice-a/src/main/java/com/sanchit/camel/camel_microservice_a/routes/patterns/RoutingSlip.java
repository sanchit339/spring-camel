package com.sanchit.camel.camel_microservice_a.routes.patterns;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class RoutingSlip extends RouteBuilder {

    // The slip: endpoints are visited in this exact order, like stops on an itinerary.
    private static final String SLIP = "direct:validate,direct:transform,direct:send";

    @Override
    public void configure() throws Exception {

        from("timer:msgSender?period=1000")
                .routeId("slip-route")
                // Give every tick a simple message to carry through the slip.
                .setBody(simple("message-${header.CamelTimerCounter}"))
                // Put the slip in a header. routingSlip reads it once, before routing starts.
                .setHeader("mySlip", constant(SLIP))
                .routingSlip(header("mySlip"));

        // 1st stop: validate
        from("direct:validate")
                .log("1. validate: ${body}");

        // 2nd stop: simple string transformation
        from("direct:transform")
                .setBody(simple("${body} -> transformed"))
                .log("2. transform: ${body}");

        // 3rd stop: hand off to a dedicated endpoint
        from("direct:send")
                .log("3. send: ${body}")
                .to("log:dummyMsgSend");
    }
}
