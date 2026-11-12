import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'org.maraberto.app',
  appName: 'MarAberto',
  webDir: 'dist',
  plugins: {
    // Splash com a arte da marca: o `splash.png` resolvido por densidade e
    // orientação (incluindo `night`) é desenhado em tela cheia pelo plugin
    // oficial; no Android 12+ o sistema ainda mostra o ícone antes.
    SplashScreen: {
      launchShowDuration: 1200,
      // O splash sai por código (`hideNativeSplash`) depois do primeiro render,
      // em vez de um tempo fixo que expõe tela branca em boot lento.
      launchAutoHide: false,
      androidScaleType: 'CENTER_CROP',
      backgroundColor: '#fefefe',
      showSpinner: false,
    },
  },
};

export default config;
