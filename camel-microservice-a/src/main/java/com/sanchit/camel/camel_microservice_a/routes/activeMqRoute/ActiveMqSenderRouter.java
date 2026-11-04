package com.sanchit.camel.camel_microservice_a.routes.activeMqRoute;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

//@Component
public class ActiveMqSenderRouter extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        from("timer:activemq-timer?period=10000") //per sec
                .transform().constant("New Message for ActiveMq")
                .log("${body}")
                .to("activemq:inbound-queue"); // you can see on the dashboard / queue section
    }
}
