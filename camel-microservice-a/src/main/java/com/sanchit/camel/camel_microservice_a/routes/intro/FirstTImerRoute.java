package com.sanchit.camel.camel_microservice_a.routes.intro;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//@Component // disabled just for the File Router Execution
public class FirstTImerRoute extends RouteBuilder {

    @Autowired
    private GetCurrentTimeBean getCurrentTimeBean;

    @Autowired
    private LogSomeMessage logSomeMessage;

    @Override
    public void configure() throws Exception {
        from("timer:first-timer") // Route definition - from where the input if coming from - and Its Name
              // .transform().constant("My Constant Message") // Basic Transformation
                .routeId("The first route") // giving custom names to the route
                .bean(getCurrentTimeBean, "getCurrentTime") // as per the camel bean naming
                // Processing vs Transformation
                // Processing - No Change in Body | Transformation - Change in Body

                // ---  as String is passed from the above transformation so the process will take only the String Arg
                .bean(logSomeMessage, "process") // by default the message will be passed in the parameter
                // OR using custom processor
                .process(new CustomProcessor()) // takes in a Processor
                .log("${body}"); // Cannot persist so - logging

    }
}


// Ideally in the application this will have it's own file

@Component
class GetCurrentTimeBean{
    public String getCurrentTime(){
        return "Time now is " + LocalDateTime.now();
    }
}

@Component
class LogSomeMessage{
    public void process(int message){
        System.out.println(message);
    }
}


class CustomProcessor implements Processor{
    @Override
    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getMessage().getBody());
    }
}