package com.sanchit.camel.camel_microservice_a.routes.fileRoute;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;


@Component
public class FileRouter extends RouteBuilder {
    @Override
    public void configure() throws Exception {

        // takes file from path files/inupts/
        from("file:files/input") // got a .camel dir with the input file copy present in the dir
                .log("${body}")
                .to("file:files/output"); // sends to the files/output
    }
}
