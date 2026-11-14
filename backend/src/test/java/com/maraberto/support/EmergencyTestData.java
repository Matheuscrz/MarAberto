package com.maraberto.support;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Massa de teste do despacho do SOS.
 *
 * <p>Os testes usam SQL direto porque o cadastro de praias/postos ainda não tem
 * API pública e o objetivo aqui é validar a seleção espacial, não o CRUD.
 */
public final class EmergencyTestData {

    private EmergencyTestData() {
    }

    /**
     * Limpa apenas o estado do despacho entre testes.
     *
     * <p>A JVM compartilha um único PostGIS e o {@code max-recipients} do KNN
     * poderia trazer plantões deixados por outro teste. Operadores, praias e
     * postos ficam: apagá-los quebraria a {@code auth_sessions} de quem já fez
     * login.
     */
    public static void clearDispatchState(JdbcTemplate jdbc) {
        // A ocorrência aberta no ack referencia o chamado: sai primeiro.
        jdbc.update("DELETE FROM occurrence_events");
        jdbc.update("DELETE FROM occurrences");
        jdbc.update("DELETE FROM emergency_outbox_events");
        jdbc.update("DELETE FROM emergency_dispatches");
        jdbc.update("DELETE FROM emergency_calls");
        jdbc.update("DELETE FROM shift_sessions");
    }

    public static UUID createOperator(JdbcTemplate jdbc, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO operational_users
                    (id, name, email, email_bidx, cpf, cpf_bidx, password_hash, role, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                """,
                id,
                "Operador de teste",
                "operador-" + id + "@teste.dev",
                "email-" + id,
                "00000000000",
                "cpf-" + id,
                "hash-de-teste",
                role);
        return id;
    }

    public static UUID createPost(JdbcTemplate jdbc, double latitude, double longitude, Integer radiusMeters) {
        UUID beachId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO beaches (id, name, state_code, boundary)
                VALUES (?, ?, 'AL', ST_Multi(ST_GeomFromText(
                    'POLYGON((-38 -11, -37 -11, -37 -10, -38 -10, -38 -11))', 4326)))
                """, beachId, "Praia de teste " + beachId);

        UUID postId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lifeguard_posts (id, beach_id, name, location, active, coverage_radius_m)
                VALUES (?, ?, ?, CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography), TRUE, ?)
                """, postId, beachId, "Posto de teste " + postId, longitude, latitude, radiusMeters);
        return postId;
    }

    public static UUID createMobileBase(
            JdbcTemplate jdbc, double latitude, double longitude, Integer radiusMeters) {
        UUID baseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO mobile_bases (id, name, location, active, coverage_radius_m)
                VALUES (?, ?, CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography), TRUE, ?)
                """, baseId, "Base de teste " + baseId, longitude, latitude, radiusMeters);
        return baseId;
    }

    public static UUID openPostShift(JdbcTemplate jdbc, UUID userId, UUID postId) {
        UUID shiftId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO shift_sessions (id, user_id, post_id, started_at)
                VALUES (?, ?, ?, NOW())
                """, shiftId, userId, postId);
        return shiftId;
    }

    public static UUID openMobileShift(
            JdbcTemplate jdbc, UUID userId, UUID baseId, Double latitude, Double longitude) {
        UUID shiftId = UUID.randomUUID();
        boolean hasPosition = latitude != null && longitude != null;
        jdbc.update("""
                INSERT INTO shift_sessions
                    (id, user_id, mobile_base_id, started_at, last_position, last_position_at)
                VALUES (?, ?, ?, NOW(),
                        CASE WHEN ? THEN CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography)
                             ELSE NULL END,
                        CASE WHEN ? THEN NOW() ELSE NULL END)
                """, shiftId, userId, baseId, hasPosition, longitude, latitude, hasPosition);
        return shiftId;
    }

    /**
     * Registra a última posição conhecida do plantão, como o
     * {@code PUT /api/v1/dispatch/shifts/current/position} faria. É a posição que
     * o despacho usa para decidir o raio — a estrutura só entra quando ela não
     * existe.
     */
    public static void reportPosition(
            JdbcTemplate jdbc, UUID shiftId, double latitude, double longitude, Instant at) {
        jdbc.update("""
                UPDATE shift_sessions
                SET last_position = CAST(ST_SetSRID(ST_MakePoint(?, ?), 4326) AS geography),
                    last_position_at = ?,
                    updated_at = NOW()
                WHERE id = ?
                """, longitude, latitude, OffsetDateTime.ofInstant(at, ZoneOffset.UTC), shiftId);
    }
}
