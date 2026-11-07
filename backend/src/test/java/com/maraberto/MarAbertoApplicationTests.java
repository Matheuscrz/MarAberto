package com.maraberto;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.media.api.ObjectStoragePort;
import com.maraberto.media.infra.StubObjectStorage;
import com.maraberto.support.PostgisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootTest
class MarAbertoApplicationTests extends PostgisIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void wiresStubObjectStorageByDefault() {
        assertThat(applicationContext.getBean(ObjectStoragePort.class))
                .isInstanceOf(StubObjectStorage.class);
    }

    @Test
    void configuresWebSocketBroker() {
        assertThat(applicationContext.getBean(SimpMessagingTemplate.class)).isNotNull();
    }
}
