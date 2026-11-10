import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@/app/index.css';
import App from '@/app/App';
import { installAuthBridge } from '@/features/auth';
import { registerServiceWorker } from '@/shared/lib/pwa';

// Liga o cliente HTTP à sessão antes de qualquer requisição (refresh no boot).
installAuthBridge();

registerServiceWorker();

const container = document.getElementById('root');

if (!container) {
  throw new Error('Elemento #root não encontrado');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
