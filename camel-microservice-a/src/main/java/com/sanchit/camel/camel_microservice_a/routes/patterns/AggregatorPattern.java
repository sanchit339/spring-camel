package com.sanchit.camel.camel_microservice_a.routes.patterns;

import com.sanchit.camel.camel_microservice_a.dto.CurrencyExchange;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

//@Component
public class AggregatorPattern extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("file:camel-microservice-a/files/json")
                .routeId("aggregator-pattern-route")
                .unmarshal().json(CurrencyExchange.class)
                // Correlation key: exchanges with the same body.id are grouped together.
                .aggregate(simple("${body.id}"), new Strategy())
                    // For learning: emit a group after 2 messages, or after 5s of inactivity.
                    .completionSize(2) // number of Message to be Grouped to get the desired output
                    .completionTimeout(5000)
                    .log("Aggregated exchange: ${body}")
                    // Convert the aggregated CurrencyExchange back to JSON (pretty printed).
                    .marshal().json(JsonLibrary.Jackson, true)
                    // Write it to an output folder so the aggregated JSON can be inspected.
                    .setHeader(Exchange.FILE_NAME, simple("aggregated-${header.CamelFileName}"))
                    .to("file:camel-microservice-a/files/json-output")
                .end();
    }
}


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
