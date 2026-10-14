package org.pdfweb;

import org.pdfweb.config.PdfWebProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(PdfWebProperties.class)
@EnableAsync
public class PdfWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfWebApplication.class, args);
    }
}
