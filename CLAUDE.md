# Estação QR Offline — contexto do projeto

App Android (Kotlin, Views + ViewBinding, Room) para estação industrial 100% offline:
a revisora libera a estação lendo seu QR assinado (HMAC) e depois a estação valida
repetidamente um único código de peça por sessão. Usado em um **Redmi Pad 2** (HyperOS),
em modo paisagem e modo quiosque.

- Pacote: `br.com.estacaoqr.offline` · minSdk 21 · targetSdk/compileSdk 35 · AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9
- Leitura de QR: `scanner/QrCameraView.kt` (Camera1 + ZXing core embarcado, sem Play Services)
- Comunicação com o usuário: em **português do Brasil**.

## Compilação

- Não há Android SDK local garantido: o APK é compilado pelo GitHub Actions
  (`.github/workflows/build-apk.yml`) a cada push no ramo `estacao-qr` do repositório
  `wlczikadobaile-creator/teste`.
- Cada build publica uma **release** (`apk-N`) com `EstacaoQROffline-debug.apk`.
  Baixar com `gh api repos/wlczikadobaile-creator/teste/releases` → `browser_download_url`
  (o download de artifacts do Actions é bloqueado no ambiente da nuvem; releases funcionam).
- Assinatura fixa em `app/estacaoqr-debug.keystore` (senha `android`, alias `androiddebugkey`).
  **Não trocar**: permite instalar novas versões por cima sem apagar os dados.
- A cada mudança entregue, incrementar `versionCode`/`versionName` em `app/build.gradle.kts`.

## Histórico de versões

- 1.0.0 — versão original recebida.
- 1.1.0 — câmera em center-crop sem distorção (decodifica o quadro inteiro, não só a área
  visível); 4 toques rápidos na câmera alternam traseira/frontal (salvo em `AppSettings.useFrontCamera`).
- 1.2.0 — ícone do app é um QR code com o texto "by: Wallace" (vetores em `res/mipmap` e
  `res/drawable/ic_launcher_*`; manifest usa `@mipmap/ic_launcher`).
- 1.3.0 — quiosque com Device Owner: `setLockTaskFeatures(NONE)` e barra de status desativada
  (sem aviso "deslize para desafixar"); saída só pela tela de configurações com PIN.
- 1.4.0 — opção "Tela sempre ligada": após 5 min sem uso o brilho cai para 5%; o primeiro
  toque só restaura o brilho (`ui/ScreenDimmer.kt`, `AppSettings.screenAlwaysOn`, padrão ligado).

## Observações

- Sem Device Owner, o Android sempre mostra o aviso de desafixar a tela — não é removível pelo app.
  Provisionar: `adb shell dpm set-device-owner br.com.estacaoqr.offline/.kiosk.QrDeviceAdminReceiver`
  (exige aparelho sem contas Google/Xiaomi).
- A pasta `beckup/` do projeto original não está no repositório.
