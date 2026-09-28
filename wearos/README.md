# Fut Segunda — app para Galaxy Watch 4 (Wear OS 3)

App standalone (não depende do celular por perto) para marcar gol e
controlar os tempos direto do pulso. Fala com o mesmo backend PHP
(`api/api.php`) usando a rede própria do relógio (WiFi ou LTE, se o modelo
tiver). Sem conexão na hora, os toques ficam numa fila local e são
reenviados automaticamente depois — sem duplicar gol.

> ✅ Projeto compilado e verificado de verdade nesta máquina (mesmo SDK/
> Gradle do app Android) — `assembleDebug` e `assembleRelease` passam
> limpos. Falta só testar de verdade num Galaxy Watch4 físico (emulador
> Wear OS ou o relógio real via ADB) — build verde não garante que o
> RemoteInput/WorkManager se comportam igual no hardware.

## Por que "direto do relógio" e não via celular

Você escolheu o relógio falar direto com o servidor (WiFi/LTE do próprio
Watch4), sem precisar do celular por perto durante o jogo. Isso é mais
robusto (não depende do Bluetooth ficar conectado o jogo inteiro), mas
exige que o relógio tenha uma rede WiFi conhecida configurada no local do
jogo (ou seja a variante LTE) — sem isso, os gols ficam na fila local e só
sobem quando o relógio pegar rede de novo (ex.: WiFi de casa).

## Abrir e testar

1. Android Studio → abrir a pasta `wearos/`.
2. Emulador: crie um "Wear OS Large Round" (API 30+) no Device Manager pra
   testar a interface antes de ir pro relógio físico.
3. Relógio físico (Galaxy Watch4):
   - No relógio: Configurações → Sobre → tocar 5x em "Número da versão" pra
     ativar Opções do desenvolvedor.
   - Configurações → Opções do desenvolvedor → ativar "Depuração ADB" e
     "Depurar via Wi-Fi" (relógio e PC na mesma rede).
   - No PC: `adb connect <ip-do-relogio>:5555`, depois rodar pelo Android
     Studio normalmente (ele lista o relógio como dispositivo).

## Gerar e instalar o APK direto (sem Play Store)

```bash
cd wearos
./gradlew assembleRelease
adb connect <ip-do-relogio>:5555
adb install app/build/outputs/apk/release/app-release.apk
```

Wear OS não deixa instalar um `.apk` "tocando no arquivo" como no Android
normal — o caminho de sideload é sempre via `adb install` (por isso o
relógio precisa estar com "Depurar via Wi-Fi" ativado toda vez que for
reinstalar uma versão nova).

## Aviso de nova versão

O relógio também consulta o painel (mesmo card "Apps — Builds para
Download", aba do Watch) e mostra um chip no topo da tela avisando "Nova
versão X — atualize via ADB" quando existe build mais nova publicada. Só o
aviso — diferente do celular, não tem como "tocar e instalar" no Wear OS,
então quem atualiza é você mesmo (subir a versão em `build.gradle.kts`,
`assembleRelease`, `adb install -r`).

## Limitações assumidas de propósito (tela pequena)

- **Login**: feito uma vez (token válido 30 dias) usando o seletor nativo
  de texto do Wear OS (voz ou teclado), não um formulário normal.
- **Gol com artilheiro**: tocar no "+1" abre a lista de jogadores daquele
  time (rolável, `ScalingLazyColumn`) pra escolher quem marcou; "Não sei
  quem marcou" manda o gol sem artilheiro (`scorerId` nulo), ajustável
  depois pelo painel web ou pelo app Android. Sem assistência nem gol
  contra no relógio — tela pequena demais pra isso, e dá pra completar
  esses detalhes depois nos outros dois apps.
- **Não inicia partida nova** — assume que a partida já foi iniciada pelo
  painel web (mesma premissa do app Android).

## Teste manual sugerido

1. Logar no relógio.
2. Com WiFi ligado e uma partida ativa, marcar um gol e conferir que
   aparece no painel web em poucos segundos.
3. Desligar o WiFi do relógio, marcar 2-3 gols, religar o WiFi e confirmar
   que todos chegam exatamente uma vez no painel web (sem duplicar).
4. Testar "Iniciar 1ºT" / "Encerrar 1ºT" / "Iniciar 2ºT" / "Encerrar
   partida" e conferir que o cronômetro do painel web acompanha.
