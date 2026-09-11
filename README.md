# HA Notifier

App Android simples, estilo "chat", que recebe notificações do seu Home Assistant
**direto pela rede local** (Wi-Fi de casa) — sem depender do Telegram nem da internet.
Fotos das câmeras e mensagens dos sensores aparecem na tela, indo do mais antigo pro
mais novo, exatamente como um bot de chat.

Continua usando o Telegram normalmente para quando você estiver fora de casa — este
app é o complemento local, que funciona mesmo se a internet cair.

## Como funciona

O app mantém uma conexão permanente (WebSocket) com a API do seu Home Assistant,
igual o app oficial faz. Ele escuta um evento customizado chamado `mobile_notify`.
Você adiciona esse evento nas suas automações do HA (além do que já existe pro
Telegram), e o app recebe, guarda no histórico local e mostra a notificação.

## Passo 1 — Gerar o Token de acesso no Home Assistant

1. No HA, clique no seu usuário (canto inferior esquerdo) → **Segurança**
2. Role até **Tokens de acesso de longa duração** → **Criar token**
3. Dê um nome (ex: "HA Notifier") e copie o token gerado — você só vê ele uma vez

## Passo 2 — Descobrir o IP local do seu Home Assistant

Configurações → Sistema → Rede (ou no seu roteador). Normalmente algo como
`192.168.31.XX`. É esse IP que vai no campo "Host" do app.

## Passo 3 — Abrir e compilar o projeto

1. Baixe/extraia esta pasta
2. Abra o **Android Studio** → *Open* → selecione a pasta `HaNotifier`
3. Deixe o Gradle sincronizar (baixa as dependências automaticamente — precisa de
   internet só nesse momento, no seu computador)
4. Conecte seu celular via USB (com "depuração USB" ativada) ou use um emulador
5. Clique em ▶ (Run) — ou gere o APK em **Build → Build Bundle(s) / APK(s) → Build APK(s)**
6. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`

## Passo 4 — Configurar o app

Abra o app → toque no ⚙ no canto superior direito → preencha:
- **Host**: o IP do seu HA (ex: `192.168.31.100`)
- **Porta**: `8123` (padrão)
- **Token**: o token gerado no Passo 1

Salve. O app já tenta conectar e mostra o status no topo da tela.

## Passo 5 — Adaptar suas automações no Home Assistant

Pegue as automações que hoje mandam mensagem pro Telegram (câmeras, sensores) e
adicione **mais uma ação**, disparando o evento customizado. Exemplo:

```yaml
automation:
  - alias: "Notificar movimento - Câmera Rua"
    trigger:
      - platform: state
        entity_id: binary_sensor.movimento_rua
        to: "on"
    action:
      # ação que você já tem, mantém funcionando fora de casa
      - service: telegram_bot.send_photo
        data:
          url: "{{ state_attr('camera.rua', 'entity_picture') }}"
          caption: "Movimento detectado na rua"

      # ação nova: dispara pro app local
      - service: event.fire
        data:
          event_type: mobile_notify
          event_data:
            title: "Câmera Rua"
            message: "Movimento detectado"
            image_url: "{{ state_attr('camera.rua', 'entity_picture') }}"
```

Repita esse padrão (adicionando o bloco `event.fire`) em cada automação que hoje
usa Telegram. Para sensores sem foto, basta omitir o `image_url`.

> Dica: se `service: event.fire` não existir na sua versão do HA, use
> `service: python_script.fire_event` ou o helper **Developer Tools → Events →
> Fire event** para testar manualmente primeiro.

## Limitações importantes

- **Precisa estar na mesma rede Wi-Fi do Home Assistant** (ou de uma VPN tipo
  Tailscale/WireGuard, caso queira que funcione fora de casa também) — não existe
  notificação sem nenhuma rede.
- O app roda um serviço em primeiro plano (aparece uma notificação fixa "HA
  Notifier - conectado ✔") para o Android não matar a conexão em segundo plano.
- Se o Android matar o app por economia de bateria, vá em Configurações do
  celular → Apps → HA Notifier → Bateria → "Sem restrições".

## Estrutura do projeto

```
HaNotifier/
├── app/src/main/java/com/example/hanotifier/
│   ├── MainActivity.kt          → tela principal (lista tipo chat)
│   ├── SettingsActivity.kt      → tela de configuração (host/porta/token)
│   ├── HaWebSocketService.kt    → conexão permanente com o HA
│   ├── NotificationEntity.kt    → modelo de dados
│   ├── NotificationDao.kt       → acesso ao banco local (Room)
│   ├── AppDatabase.kt
│   ├── NotificationAdapter.kt   → adapter da lista
│   ├── Prefs.kt                 → salva host/token
│   └── BootReceiver.kt          → reconecta ao ligar o celular
└── app/src/main/res/            → layouts, cores, strings
```
