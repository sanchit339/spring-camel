package com.sanchit.camel.camel_microservice_a.routes.patterns;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


//@Component
public class CsvSplitter extends RouteBuilder {

    @Autowired
    private CsvSplitterUtil csvSplitterUtil;

    @Override
    public void configure() throws Exception {
//        from("file:camel-microservice-a/files/csv")
//                .unmarshal().csv()
//                .split((body()))
//                .convertBodyTo(String.class)
//                .to("activemq:csv-processor");

        // New Message per ","
        from("file:camel-microservice-a/files/csv")
                .convertBodyTo(String.class)
                .split(body(), ",")
                .split(method(csvSplitterUtil))
                .to("activemq:csv-processor");
    }
}


@Component
class CsvSplitterUtil{
    public List<String> splitInput(String body){
        // add custom split logic and return the list
        return List.of("a", "b", "c");
    }
}