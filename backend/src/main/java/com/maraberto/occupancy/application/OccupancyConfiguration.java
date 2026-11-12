package com.maraberto.occupancy.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Habilita os parâmetros do indicador de ocupação (RF10/RN06). */
@Configuration
@EnableConfigurationProperties(OccupancyProperties.class)
public class OccupancyConfiguration {
}
