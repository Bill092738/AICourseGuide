package com.courseguide;

import com.courseguide.controllers.ProgressController;
import com.courseguide.controllers.RecommendationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the full application context against in-memory H2 to verify that all
 * beans (controllers, services, CORS config) wire together.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:contexttest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.minimum-idle=0",
})
class AppContextTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(RecommendationController.class)).isNotNull();
        assertThat(context.getBean(ProgressController.class)).isNotNull();
        assertThat(context.containsBean("corsConfigurer")).isTrue();
    }
}
