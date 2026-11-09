package com.sanchit.camel.camel_microservice_a.routes.patterns;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;


//@Component
public class EipPatternsRouter extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:multicast-timer?period=1000")
                .multicast()
                // this sends a copy of the msg to given routes ...
                .to("log:KafkaQueue", "log:differentMicroservice" , "log:others");
    }
}
