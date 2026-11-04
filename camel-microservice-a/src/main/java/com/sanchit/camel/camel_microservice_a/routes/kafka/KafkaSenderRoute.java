package com.sanchit.camel.camel_microservice_a.routes.kafka;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class KafkaSenderRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("file:camel-microservice-a/files/json")
                .log("${body}")
                .to("kafka:kafkaReceiver");
    }
}
