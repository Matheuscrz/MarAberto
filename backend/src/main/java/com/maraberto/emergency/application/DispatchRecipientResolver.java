package com.maraberto.emergency.application;

import com.maraberto.identity.api.OperatorDirectory;
import com.maraberto.operations.api.EligibleResponder;
import com.maraberto.operations.api.EligibleRespondersPort;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolve quem deve ser notificado, com degradação garantida.
 *
 * <p>A consulta espacial e a busca da coordenação rodam em transações próprias
 * (REQUIRES_NEW): um erro de SQL não pode abortar a transação que grava o SOS.
 * Se nada for encontrado, a lista fica vazia e o chamado é salvo do mesmo jeito
 * (RN03/RN19).
 */
@Service
public class DispatchRecipientResolver {

    private static final Logger log = LoggerFactory.getLogger(DispatchRecipientResolver.class);

    private final EligibleRespondersPort eligibleResponders;
    private final OperatorDirectory operatorDirectory;
    private final TransactionTemplate requiresNew;

    public DispatchRecipientResolver(
            EligibleRespondersPort eligibleResponders,
            OperatorDirectory operatorDirectory,
            PlatformTransactionManager transactionManager) {
        this.eligibleResponders = eligibleResponders;
        this.operatorDirectory = operatorDirectory;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<EligibleResponder> resolve(double latitude, double longitude) {
        try {
            List<EligibleResponder> eligible = requiresNew.execute(
                    status -> eligibleResponders.findEligible(latitude, longitude));
            if (eligible != null && !eligible.isEmpty()) {
                return eligible;
            }
        } catch (RuntimeException exception) {
            log.warn("Seleção espacial de destinatários do SOS falhou; acionando a coordenação: {}",
                    exception.getClass().getSimpleName());
        }

        try {
            List<UUID> coordinators = requiresNew.execute(
                    status -> operatorDirectory.activeCoordinationUserIds());
            if (coordinators != null) {
                return coordinators.stream().map(EligibleResponder::coordination).toList();
            }
        } catch (RuntimeException exception) {
            log.warn("Busca da coordenação ativa falhou; o SOS fica sem destinatário: {}",
                    exception.getClass().getSimpleName());
        }
        return List.of();
    }
}
