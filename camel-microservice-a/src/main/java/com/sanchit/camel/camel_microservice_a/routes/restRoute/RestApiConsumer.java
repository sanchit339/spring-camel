package com.sanchit.camel.camel_microservice_a.routes.restRoute;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

//@Component
public class RestApiConsumer extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        restConfiguration().host("localhost").port(8000);

        from("timer:rest-api-consumer?period=1000")
                .setHeader("from" , () -> "EUR")
                .setHeader("to",() -> "INR")
                .log("${body}")
                .to("rest:get:/currency-exchange/from/USD/to/INR")
                .log("${body}");
    }
}
