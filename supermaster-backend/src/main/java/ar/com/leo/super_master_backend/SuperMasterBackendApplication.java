package ar.com.leo.super_master_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SuperMasterBackendApplication {

    public static void main(String[] args) {
        // Configurar zona horaria de Argentina para toda la JVM
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

        SpringApplication.run(SuperMasterBackendApplication.class, args);
    }

}
