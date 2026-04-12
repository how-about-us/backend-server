package com.howaboutus.backend;

import com.howaboutus.backend.support.AbstractPostgresContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HowAboutUsBackendApplicationTests extends AbstractPostgresContainerTest {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 로드된다")
    void contextLoads() {
    }

}
