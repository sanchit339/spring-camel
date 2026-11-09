package com.sanchit.camel.camel_microservice_b.routes.receiverRoute;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//@Component
public class ReceiverRoute extends RouteBuilder {

    @Autowired
    private CustomCurrencyProcessor customCurrencyProcessor;

    @Override
    public void configure() throws Exception {
        from("activemq:inbound-queue")
                .log("${body}")
                .unmarshal().json(JsonLibrary.Jackson, CurrencyExchange.class) // will get a CurrencyExchange obj
                .bean(customCurrencyProcessor)
                .to("log:my-custom-log-for-received-msg");

        from("activemq:csv-processor")
                // Camel maps the JMS message to a Java body. Capture the actual
                // runtime type before logging so we can see what arrived.
                .process(exchange -> {
                    Object body = exchange.getIn().getBody();
                    String bodyType = body == null ? "null" : body.getClass().getName();
                    exchange.getIn().setHeader("BodyType", bodyType);
                })
                .log("Received from csv-processor -> type: ${header.BodyType}, body: ${body}")
                .to("log:received-message from active mq");

    }
}


@Component
class CustomCurrencyProcessor{
    private static final Logger logger = LoggerFactory.getLogger(CustomCurrencyProcessor.class);

    public void process(CurrencyExchange currencyExchange){
        logger.info("Processing the Conversion " + currencyExchange.getConversionMultiple() + " is the current conversion rate from USD to INR");
    }
}