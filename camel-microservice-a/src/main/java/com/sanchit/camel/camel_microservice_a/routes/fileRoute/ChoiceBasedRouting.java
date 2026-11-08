package com.sanchit.camel.camel_microservice_a.routes.fileRoute;

import org.apache.camel.Body;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChoiceBasedRouting extends RouteBuilder {

    @Autowired
    private DeciderBean deciderBean;

    @Override
    public void configure() throws Exception {
        from("file:camel-microservice-a/files/input")
                .routeId("Choice-based-routing-route")
                // Transformed as to use the contains keyword
                .transform().body(String.class)
                .choice()
                    // simple allows you to add condition in the simple lang Like its - DSL (Domain Specific Language)
                    .when(simple("${file:ext} ends with 'xml'"))
                        .log("Starting Processing of XML file")
                    .when(simple("${body} contains 'USD'"))
                        .log("File has USD records")
                    .when(method(deciderBean))
                        .log("Decider bean has done it's work")
                    .otherwise()
                        .log("not an XML file")
                .end()
                // replace the logging route to the - ROUTE - log-route
                .to("direct://log-route")
                .to("file:camel-microservice-a/files/output");

            from("direct:log-route")
                    .log("${messageHistory}")
                    .log("Processing of the file finished");
    }
}


@Component
class DeciderBean{

    Logger logger = LoggerFactory.getLogger(DeciderBean.class);

    public boolean deciderBean(@Body String body, @Headers Map<String, String> headers,
                               @ExchangeProperties Map<String, String> exchangeProperties){
        logger.info("DeciderBean {} {} " , body , headers);
        boolean check = body.contains("USD");
        if(check){
            logger.info("Trades HAHA in USD");
        } else {
            logger.info("Domestic trader ");
        }
        return true;
    }
}