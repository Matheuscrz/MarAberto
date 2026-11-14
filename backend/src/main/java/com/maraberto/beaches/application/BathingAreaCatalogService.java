package com.maraberto.beaches.application;

import com.maraberto.beaches.api.BathingAreaOption;
import com.maraberto.beaches.infra.BathingAreaCatalogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leitura das áreas ativas para os formulários da coordenação. */
@Service
public class BathingAreaCatalogService {

    private final BathingAreaCatalogRepository repository;

    public BathingAreaCatalogService(BathingAreaCatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BathingAreaOption> activeAreas() {
        return repository.findActive();
    }
}
