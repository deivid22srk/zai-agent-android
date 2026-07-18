# Zai Agent

Aplicativo Android nativo em **Jetpack Compose + Material 3 (Material You)** que atua como cliente do
[https://chat.z.ai/](https://chat.z.ai/) no modo **agent**.

O usuário faz login com a própria conta Z.ai diretamente pelo WebView embutido — o app captura o
cookie de sessão `token` e o reutiliza nas chamadas de API (incluindo o streaming SSE do modo agente).
Nenhuma credencial é enviada para servidores terceiros.

## Recursos

- **Login via WebView** — abre `https://chat.z.ai/`, captura o cookie `token` automaticamente
  quando o login é concluído e persiste em `SharedPreferences` privada.
- **Lista de conversas** com busca, criação, renomear e excluir (FAB + overflow menu).
- **Tela de chat** com:
  - streaming SSE em tempo real,
  - botão **Parar** para cancelar o stream,
  - **copiar** resposta,
  - alternância rápida entre **Modo Agente** e **Modo Chat** via FilterChip.
- **Material You** com `dynamicLightColorScheme` / `dynamicDarkColorScheme` no Android 12+ e paleta
  fallback para versões anteriores.
- **Edge-to-edge** com `enableEdgeToEdge` e barras de sistema transparentes.
- **DI manual** via `Application` — sem Hilt/Koin, build mais leve.

## Stack

| Camada           | Lib                                             |
|------------------|-------------------------------------------------|
| UI               | Jetpack Compose BOM 2024.11, Material 3         |
| Navigation       | `androidx.navigation:navigation-compose`        |
| HTTP             | OkHttp 4.12 + `okhttp-sse`                      |
| Serialização     | `kotlinx-serialization`                         |
| Async            | `kotlinx-coroutines` + `Flow`                   |
| Imagens          | Coil                                            |
| Min SDK          | 26 (Android 8.0)                                |
| Target SDK       | 35 (Android 15)                                 |

## Build local

```bash
./gradlew assembleDebug
# ou
./gradlew assembleRelease
```

O APK debug será gerado em `app/build/outputs/apk/debug/`.
O APK release é assinado com o `debug.keystore` commitado no repo (apenas para distribuição
interna — substitua pelo seu próprio keystore antes de publicar).

## CI

O workflow `.github/workflows/build.yml` compila o app a cada push / PR e publica os artefatos
APK e AAB na página de releases do workflow.

## Segurança

- O cookie `token` é a única credencial persistida no dispositivo.
- O `AndroidManifest.xml` força `usesCleartextTraffic="false"` e restringe o tráfego HTTPS ao
  domínio `z.ai` via `network_security_config.xml`.
- O `backup_rules.xml` exclui os cookies do backup automático do Google.
