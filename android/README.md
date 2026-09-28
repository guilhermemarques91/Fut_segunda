# Fut Segunda — Ao Vivo (app Android)

App nativo (Kotlin + Jetpack Compose) para marcar gol e controlar os tempos
da partida direto do celular, sem precisar do navegador. Fala com o mesmo
backend do `frontend/index.html` (`api/api.php`), usando três actions novas
(`live_goal_add`, `live_goal_undo`, `live_period`) adicionadas nesta mesma
mudança — veja o "Fase 0" no plano.

> ✅ Projeto compilado e verificado de verdade nesta máquina (Android SDK
> `cmdline-tools` + Gradle 8.7 instalados só pra isso) — `assembleDebug` e
> `assembleRelease` passam limpos. O wrapper (`gradlew`/`gradlew.bat`) já
> está no repo, então não precisa gerar nada na primeira abertura.

## Abrir e rodar

1. Instale o **Android Studio** (Ladybug ou mais recente) — ou use só o
   `gradlew` da linha de comando (veja abaixo), sem precisar do Studio.
2. Abra a pasta `android/` como projeto (File → Open) e espere o "Gradle
   Sync" (primeira vez baixa as dependências, pode demorar um pouco).
3. Rode num emulador ou celular físico com o botão ▶ (login com um usuário
   já cadastrado no app — mesma tabela `users` do backend).

## Gerar o APK para instalar direto (sem Play Store)

```bash
cd android
./gradlew copyReleaseApk
```

(No Windows sem `JAVA_HOME`/`ANDROID_HOME` configurados globalmente, defina
antes: `$env:JAVA_HOME`/`$env:ANDROID_HOME` apontando pro JDK 17 e pro SDK.)

`copyReleaseApk` já roda o `assembleRelease` por baixo e copia o resultado
pra `builds/app-fut-android-<versionName>.apk` (na raiz do repo, fora do
`android/`) — não precisa mais caçar dentro de
`app/build/outputs/apk/release/`. Tem o equivalente `copyDebugApk` pra
build de debug (`builds/app-fut-android-<versionName>-debug.apk`).

O APK sai assinado com a **keystore de debug** (configurado assim de propósito em
`app/build.gradle.kts` — sem isso o Android recusa instalar um APK sem
assinatura nenhuma). Pra instalar, copie o arquivo pro celular e abra,
habilitando "Instalar apps de fontes desconhecidas" quando pedir. Antes de
trocar de celular (ou distribuir pra mais gente), vale gerar uma keystore de
verdade (`keytool -genkey ...`) e configurar um `signingConfig` próprio —
sem isso, qualquer um com o projeto consegue gerar um APK que se atualiza
por cima do seu, já que a chave de debug é a mesma em qualquer máquina com
Android SDK.

## Publicar uma atualização

1. Suba a versão em `app/build.gradle.kts` (`versionCode` — sempre +1 — e
   `versionName`, ex: `2` / `"1.1"`).
2. `./gradlew assembleRelease`.
3. No painel web, logado como admin: **Config (⚙️) → card "Apps — Builds
   para Download"** → selecione o `.apk`, informe a mesma versão/build e
   publique.
4. Todo celular com o app aberto (ou dentro de ~6h, via checagem em segundo
   plano) recebe uma notificação + um banner dentro do app oferecendo
   "Atualizar" — o link abre no navegador, baixa o apk e oferece instalar
   por cima (efeito parecido com uma atualização normal de app).

Isso depende do backend (`api/api.php`, `api/download_release.php`) estar
publicado no servidor — ver `network/ApiClient.kt` pra confirmar a URL.

## Onde mexer se mudar algo no backend

- `network/ApiModels.kt` — formato dos dados (campos exatamente iguais ao
  que `frontend/index.html` já usa: `homePlayers`, `awayPlayers`, `goalLog`,
  `periodo`, etc.).
- `network/ApiService.kt` — lista de actions chamadas.
- `network/ApiClient.kt` — URL base e a API key fixa (`ServerConfig`).
- `data/LiveEventRepository.kt` — fila offline + idempotência por
  `clientEventId` (não mexer sem entender a Fase 0 do backend).

## Duas abas

- **Ao vivo** — nativo (Compose), é a tela feita pra marcar gol/tempo com
  fila offline. É a melhoria de verdade sobre usar o navegador.
- **Painel completo** — carrega o `frontend/index.html` de verdade dentro
  de um WebView (Dashboard, Jogadores, Finanças, Rodada/Times, Tira Gosto,
  Presença etc.), já logado com o mesmo token do app (`WebPanelScreen.kt`
  injeta `fut_token`/`fut_role`/`fut_username` no `localStorage` do
  WebView e recarrega uma vez). Reescrever tudo isso em Kotlin seria muito
  mais trabalho pra zero ganho — aqui é só reaproveitar o painel que já
  existe e funciona.

## O que este app assume

- **A partida já foi iniciada pelo painel web** (aba Rodada → "Iniciar
  Partida"), que é quem define os jogadores de cada time (`homePlayers`/
  `awayPlayers`). O app aqui só marca gol e controla o cronômetro de uma
  partida já ativa — ele não cria escalação nova.
- Login é o mesmo da tabela `users` do backend (usuário/senha já existentes).
- Sem conexão, os toques (gol, desfazer, início/fim de tempo) ficam numa
  fila local (Room) e são reenviados automaticamente quando a internet
  voltar — nunca duplicam, porque cada evento tem um ID único conferido
  pelo servidor.

## Teste manual sugerido

1. Logar com um usuário existente.
2. Com uma partida ativa no painel web, marcar um gol pelo app e confirmar
   que aparece no painel web/`?action=public` em poucos segundos.
3. Desfazer o gol pelo app e confirmar que some no painel web.
4. Ligar o modo avião no celular, marcar 2-3 gols, desligar o modo avião e
   confirmar que todos chegam exatamente uma vez (sem duplicar) no painel
   web.
5. Testar os botões de período (iniciar/encerrar 1ºT, iniciar 2ºT, encerrar
   partida) e conferir que o cronômetro do painel web reflete a mudança.
