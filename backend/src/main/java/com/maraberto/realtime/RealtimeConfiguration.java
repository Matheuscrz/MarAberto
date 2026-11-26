package com.maraberto.realtime;

import com.maraberto.realtime.infra.PushProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class RealtimeConfiguration {
}
