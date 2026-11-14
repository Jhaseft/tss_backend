package com.jhaseft.tss;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Carga el archivo {@code .env} en el entorno de Spring durante los tests. En producción esto lo
 * hace {@code TssApplication.main}, que no se ejecuta con {@code @SpringBootTest}; sin esto, los
 * tests de contexto (contextLoads y los de integración) no resolverían DB_URL/DB_USER/DB_PASSWORD.
 *
 * Registrado en {@code src/test/resources/META-INF/spring.factories}. Solo está en el classpath de
 * test, así que no altera el arranque de la aplicación.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        Map<String, Object> props = new HashMap<>();
        dotenv.entries().forEach(e -> props.put(e.getKey(), e.getValue()));
        if (!props.isEmpty())
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv-test", props));
    }
}
