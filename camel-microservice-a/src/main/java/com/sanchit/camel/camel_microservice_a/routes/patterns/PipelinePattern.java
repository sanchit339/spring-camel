package com.sanchit.camel.camel_microservice_a.routes.patterns;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;


//@Component
public class PipelinePattern extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("file:camel-microservice-a/files/input")
                .routeId("Choice-based-routing-route")
                // Pipeline EIP - processes the exchange through a sequence of steps, one after the other
                // (unlike multicast/recipientList which send to multiple endpoints).
                // The body/headers set by one step become the input of the next step.
                // Note: chaining steps with "." already runs them sequentially - this explicit
                // .pipeline() just makes the sequential flow visible and groups the steps together.
                .pipeline()
                    // Transformed as to use the contains keyword
                    .transform().body(String.class)
                    .choice()
                        // simple allows you to add condition in the simple lang Like its - DSL (Domain Specific Language)
                        .when(simple("${file:ext} ends with 'xml'"))
                            .log("Starting Processing of XML file")
                        .when(simple("${body} contains 'USD'"))
                            .log("File has USD records")
                        .otherwise()
                            .log("not an XML file")
                    .end() // ends the choice block
                    // replace the logging route to the - ROUTE - log-route
                    .to("direct://log-route")
                .end() // ends the pipeline block
                // after the pipeline finishes, the exchange continues here
                .to("file:camel-microservice-a/files/output");

        from("direct:log-route")
                .log("${messageHistory}")
                .log("Processing of the file finished");
    }
}
