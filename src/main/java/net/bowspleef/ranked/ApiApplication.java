package net.bowspleef.ranked;

import net.bowspleef.ranked.config.ClassicEloProperties;
import net.bowspleef.ranked.config.EloProperties;
import net.bowspleef.ranked.config.WebhookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({EloProperties.class, ClassicEloProperties.class, WebhookProperties.class})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
