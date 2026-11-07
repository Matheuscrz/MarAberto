/**
 * Módulo emergency: SOS — criação, despacho, ack, escalonamento e desfecho.
 * Persistir antes de notificar (Transactional Outbox); ack obrigatório.
 *
 * <p>Eventos publicados: {@code EmergencyCallCreated}, {@code …Acknowledged},
 * {@code …Escalated}, {@code …Resolved}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Emergency")
package com.maraberto.emergency;
