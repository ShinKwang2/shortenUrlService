package kr.co.shortenurlservice.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class StartupLogger implements ApplicationRunner {

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String[] activeProfiles = environment.getActiveProfiles();
        String port = environment.getProperty("server.port");
        String databaseAddress = environment.getProperty("database.address") != null ? environment.getProperty("database.address") : "no-database";
        log.info("[STARTUP] Application started profiles: [{}] server.port: {} database.address: {}",
                value("profiles", activeProfiles[0]), value("server.port", port), value("database.address", databaseAddress));
    }
}
