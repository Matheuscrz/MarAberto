import { apiRequest } from "@/shared/lib/api-client";
import type {
  BathingAreaOption,
  ImportMode,
  ImportReport,
} from "@/features/management/types";

export interface ImportBathingAreasInput {
  readonly file: File;
  readonly state: string;
  readonly dryRun: boolean;
  readonly confirm: boolean;
  readonly mode: ImportMode;
}

/**
 * Envia o KML do QGIS para a coordenação.
 *
 * <p>`dryRun` é o padrão do servidor: a primeira chamada analisa e devolve o
 * diff sem escrever. Aplicar exige `dryRun=false` **e** `confirm=true`, para
 * que um cliente distraído não substitua o mapa por engano.
 */
export async function importBathingAreas(
  input: ImportBathingAreasInput,
): Promise<ImportReport> {
  const form = new FormData();
  form.append("file", input.file);

  const query = new URLSearchParams({
    state: input.state,
    dryRun: String(input.dryRun),
    confirm: String(input.confirm),
    mode: input.mode,
  });

  return apiRequest<ImportReport>(
    `/api/v1/management/bathing-areas/import?${query.toString()}`,
    { method: "POST", body: form },
    // Uma importação maior pode passar do timeout padrão das telas.
    60000,
  );
}

/**
 * Áreas ativas para escolher num formulário da coordenação.
 *
 * <p>Diferente da lista pública, que exige posição, é a relação completa — é o
 * que a triagem usa para identificar uma área existente.
 */
export async function fetchBathingAreaOptions(): Promise<
  readonly BathingAreaOption[]
> {
  return apiRequest<readonly BathingAreaOption[]>(
    "/api/v1/management/bathing-areas",
    {},
  );
}
