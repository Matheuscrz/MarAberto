const PREFIX = 'maraberto:';

/**
 * Preferências não sensíveis (praia selecionada, tema). Nunca guarde tokens,
 * coordenadas, CPF ou qualquer dado pessoal no armazenamento do navegador.
 */
export function readPreference(key: string): string | null {
  try {
    return window.localStorage.getItem(PREFIX + key);
  } catch {
    return null;
  }
}

export function writePreference(key: string, value: string): void {
  try {
    window.localStorage.setItem(PREFIX + key, value);
  } catch {
    // Armazenamento indisponível (modo privado): a aplicação segue sem persistir.
  }
}
