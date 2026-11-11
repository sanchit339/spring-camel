package com.sanchit.camel.camel_microservice_a.routes.patterns;

import com.sanchit.camel.camel_microservice_a.dto.CurrencyExchange;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperties;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;


@Component
public class DynamicRoutePattern extends RouteBuilder {

    @Autowired
    private DynamicRouterBean dynamicRouterBean;

    @Override
    public void configure() throws Exception {
        // Trigger the dynamic router with alternating normal/high-value messages.
        // includeMetadata=true is required in Camel 4.x: without it the timer does
        // not set CamelTimerCounter or the other timer properties/headers at all.
        from("timer:dynamicRouterTrigger?period=5000&includeMetadata=true")
                .routeId("dynamic-router-trigger")
                .process(exchange -> {
                    // CamelTimerCounter = "which tick is this?".
                    // The timer component stores it on the *Exchange* as a property,
                    // NOT on the message as a header. It starts at 1 and goes up by 1
                    // on every period (so here: 1, 2, 3, ... every 5 seconds).
                    Long counter = exchange.getProperty(Exchange.TIMER_COUNTER, Long.class);
                    BigDecimal multiple = counter % 2 == 0
                            ? BigDecimal.valueOf(10)
                            : BigDecimal.valueOf(60);
                    exchange.getIn().setBody(new CurrencyExchange(counter, "USD", "INR", multiple));
                })
                // Hand the exchange to the "direct:start" route below.
                // "direct:" is in-memory and synchronous: the consumer route runs in the
                // SAME thread (no queue, no broker), and this route waits for it to finish.
                .to("direct:start");

        from("direct:start")
                .routeId("dynamic-router-route")
                .dynamicRouter(method(dynamicRouterBean, "next"));

        from("direct:validate")
                .log("1. Validate ${body}");

        from("direct:audit")
                .log("2a. audit (High Value) : ${body}");

        from("direct:enrich")
                .log("2b. enrich (normal) : ${body}");

        from("direct:send")
                .log("3. Send ${body}");
    }
}


@Component
class DynamicRouterBean{
    public String next(CurrencyExchange body, @ExchangeProperties Map<String, Object> properties){
        // Keep state on the Exchange: the bean is shared between concurrent messages.
        Object current = properties.get("dynamicRouter.step");
        int step = current == null ? 0 : (Integer) current;
        properties.put("dynamicRouter.step", step + 1);

        return switch (step){
            case 0 -> "direct:validate";
            case 1 -> body.getConversionMultiple().compareTo(new BigDecimal("50")) > 0 ? "direct:audit" : "direct:enrich";
            case 2 -> "direct:send";
            default -> null; // no more hops -> routing ends
        };
    }
}
