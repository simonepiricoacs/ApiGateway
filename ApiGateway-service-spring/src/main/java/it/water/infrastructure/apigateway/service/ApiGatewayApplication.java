package it.water.infrastructure.apigateway.service;

import it.water.implementation.spring.annotations.EnableWaterFramework;
import it.water.repository.jpa.spring.RepositoryFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot application entry point for ApiGateway.
 */
@SpringBootApplication
@EnableWaterFramework
@EnableJpaRepositories(basePackages = {"it.water", "it.water.infrastructure.apigateway"}, repositoryFactoryBeanClass = RepositoryFactory.class)
@EntityScan({"it.water", "it.water.infrastructure.apigateway"})
@ComponentScan({"it.water", "it.water.infrastructure.apigateway"})
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
