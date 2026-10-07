# Estação QR Offline

Aplicativo Android industrial 100% offline para liberar uma estação por revisora e validar repetidamente um único código de peça por sessão.

## Compatibilidade

- Android 5.0 (API 21) ou superior, incluindo versões atuais.
- Leitura QR pela câmera com ZXing incorporado ao APK. Não baixa modelos e não requer Google Play Services.
- Banco Room local. O aplicativo não solicita permissão de internet.

## Primeiro uso

1. Abra o aplicativo e autorize a câmera.
2. Toque em **ADMINISTRAÇÃO**.
3. No primeiro acesso, crie um PIN administrativo com pelo menos 4 dígitos.
4. Cadastre as peças com código e descrição.
5. Cadastre as revisoras e abra o QR de cada uma. Imprima ou fotografe o QR para uso na estação.
6. Volte ao leitor e leia o QR assinado de uma revisora.
7. Leia uma peça cadastrada. A primeira peça define o código esperado da sessão.
8. As leituras seguintes devem ter o mesmo código. Para trocar o código, encerre a sessão com o PIN e faça uma nova liberação.

## Segurança dos QRs de revisora

O QR é assinado por HMAC-SHA256 com uma chave exclusiva gerada no aparelho. Uma alteração manual no conteúdo invalida a assinatura. A revisora também precisa permanecer ativa no banco local.

Os QRs administrativos são vinculados ao aparelho que os criou. Após reinstalar o app ou apagar os dados, cadastre as revisoras e gere novos QRs.

## Modo quiosque

Sem Device Owner, o app usa tela cheia imersiva e solicita o modo de fixação de tela do Android quando o quiosque é ativado. O fabricante do aparelho pode permitir que o usuário saia dessa fixação por uma combinação de botões.

Para bloqueio total, o aparelho precisa ser provisionado como **Device Owner**. Isso normalmente exige aparelho restaurado, sem contas configuradas. Com ADB habilitado, use:

```text
adb shell dpm set-device-owner br.com.estacaoqr.offline/.kiosk.QrDeviceAdminReceiver
```

Depois abra **Administração → Quiosque e segurança** e ative o modo quiosque. A saída fica disponível na mesma tela, depois da autenticação por PIN na área administrativa.

## Teste funcional sugerido

1. Cadastre `P001 / Engrenagem A` e `P002 / Engrenagem B`.
2. Cadastre uma revisora e gere o QR assinado.
3. Leia o QR da revisora: a estação deve indicar que foi liberada.
4. Leia `P001`: deve aparecer **PEÇA LIBERADA ✔** em verde.
5. Leia `P001` novamente: deve continuar verde.
6. Leia `P002`: deve aparecer **PEÇA DIFERENTE ✖** em vermelho, com a lida e a esperada, além do alerta sonoro.
7. Leia um código não cadastrado: deve informar que não está cadastrado.
8. Consulte os logs e confirme revisora, data/hora, códigos esperado/lido e resultado.
9. Encerre a sessão com o PIN e confirme que um novo QR de revisora é exigido.

## Compilação

Abra a pasta no Android Studio ou execute `gradlew.bat assembleDebug` com JDK 17/21 e Android SDK instalado. O APK de depuração é criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Backups

A pasta `beckup` contém cópias feitas antes de alterações em arquivos existentes durante o desenvolvimento. Ela não é incluída no ZIP enxuto de entrega.
