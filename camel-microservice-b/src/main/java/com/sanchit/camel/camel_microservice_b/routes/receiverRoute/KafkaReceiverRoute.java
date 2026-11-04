package com.sanchit.camel.camel_microservice_b.routes.receiverRoute;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class KafkaReceiverRoute extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("kafka:kafkaReceiver")
                .to("log:msg-from-kafka");
    }
}
