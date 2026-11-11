package com.maraberto.realtime.api;

/**
 * Destinos STOMP do SOS.
 *
 * <p>São filas <strong>por usuário</strong> ({@code /user/{userId}/...}): o
 * alerta chega apenas a quem foi escolhido pelo banco, com o JWT validado no
 * frame {@code CONNECT}. Diferente de um tópico aberto, a autorização deixa de
 * ser cosmética.
 *
 * <p>Há dois nomes porque o broker fala línguas diferentes nas duas pontas:
 * o servidor publica com {@code convertAndSendToUser(userId, SOS_QUEUE, ...)} e
 * o cliente assina o <em>destino de usuário</em> {@code /user/queue/sos}, que o
 * {@code UserDestinationMessageHandler} reescreve para a fila da sessão. Assinar
 * {@code /queue/sos} no cliente não recebe nada.
 */
public final class SosDestinations {

    /** Prefixo de destino por usuário configurado no broker. */
    public static final String USER_PREFIX = "/user";

    /** Destino usado pelo servidor ao publicar para um usuário. */
    public static final String SOS_QUEUE = "/queue/sos";

    /** Destino que o cliente autenticado assina. */
    public static final String SOS_SUBSCRIPTION = USER_PREFIX + SOS_QUEUE;

    private SosDestinations() {
    }
}
