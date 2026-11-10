import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

/**
 * Estado de interface do MarAberto.
 *
 * Regra de segurança: este store guarda apenas preferências não sensíveis.
 * Nunca persista coordenadas, `clientRequestId`, dados de saúde, CPF, e-mail ou
 * telefone. A localização do SOS vive só na memória da página e é descartada
 * ao sair. O Zustand não é usado para dados vindos do servidor: esses são
 * sempre revalidados e sanitizados na renderização.
 */
export interface AppState {
  selectedBeachId: string | null;
  installPromptDismissed: boolean;
  setSelectedBeach: (beachId: string | null) => void;
  dismissInstallPrompt: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      selectedBeachId: null,
      installPromptDismissed: false,
      setSelectedBeach: (beachId) => set({ selectedBeachId: beachId }),
      dismissInstallPrompt: () => set({ installPromptDismissed: true }),
    }),
    {
      name: 'maraberto:preferences',
      storage: createJSONStorage(() => localStorage),
      // Apenas estes campos são persistidos; qualquer campo futuro precisa ser
      // adicionado explicitamente e revisado quanto a dados pessoais.
      partialize: (state) => ({
        selectedBeachId: state.selectedBeachId,
        installPromptDismissed: state.installPromptDismissed,
      }),
      version: 1,
    },
  ),
);
