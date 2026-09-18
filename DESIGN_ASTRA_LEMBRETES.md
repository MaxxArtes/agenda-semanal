Recomendo **Nenhum por padrão** e aprovação do recurso condicionada à confiabilidade do agendamento. O plano atual tem dois buracos graves: os lembretes acabam após sete dias sem abrir o app, e tela cheia exige uma autorização distinta de alarme exato.

1. **Editor: lembrete junto do horário**

   No `ModalBottomSheet` atual, inserir **depois de “Duração” e antes de “Vale para”**.
   Rótulo **“Lembrete”**; segmentado **“Nenhum / Notificação / Alarme”**, com alvos mínimos de 48dp.
   Abaixo, seletor **“Quando”**, oculto em Nenhum.
   Notificação: **“No horário”, “5 min antes”, “10 min antes”, “15 min antes”, “30 min antes”, “1 h antes”**.
   Alarme: **“No horário”, “5 min antes”, “10 min antes”, “30 min antes”**.
   Ao ativar um modo pela primeira vez, sugerir 10 minutos, sem salvar automaticamente.
   Ajuda: **“Avisa neste aparelho.”** ou **“Toca som de despertador neste aparelho.”**
   Ao trocar de modo com antecedência incompatível, exigir nova escolha; não arredondar silenciosamente.
   Manter o alcance local existente: **“Vale para” governa todos os campos, inclusive o lembrete**.
   “Só este dia” grava exceção; “Todas as semanas” altera o mestre, preservando substituições explícitas das exceções.
   Avulsos/exceções continuam restritos à data; **“Voltar à rotina” também restaura a herança do lembrete**.
   Não ressuscitar o seletor global removido pelo adendo de `DESIGN_ASTRA.md`.

2. **Grade e barra do selecionado**

   Em `BlocoView`, sino para Notificação e despertador para Alarme; **16dp**, na cor `Categoria.texto`.
   Reservar espaço à direita do título, sem disputar com o ponto de exceção ou com as alças.
   Exibir apenas com altura do bloco **≥44dp** e largura suficiente; abaixo disso, esconder o ícone.
   Usar dimensões disponíveis, não apenas o fator de zoom: um bloco longo ainda comporta informação.
   Nenhum não mostra ícone; TalkBack sempre anuncia modo, antecedência e eventual bloqueio.
   Na barra, após o alcance: **“Notificação 10 min antes · Trocar”**, **“Alarme no horário · Trocar”** ou **“Sem lembrete · Adicionar”**.
   A ação abre o mesmo editor, posicionado em Lembrete; alvo de 48dp, sem outro seletor permanente.

3. **Notificação e alarme**

   Notificação — título: **“Teclado em 10 min”**; corpo: **“20:30–21:00 · Agenda Semanal”**.
   Com antecedência zero: **“Teclado começa agora”**; se atrasada, não anunciar falsamente “em 10 min”.
   Ações: **“Abrir”** e **“Adiar 5 min”**; tocar no cartão também abre a ocorrência.
   Adiar significa cinco minutos a partir do toque, apenas neste aparelho; não muda o compromisso nem a série.
   Canal **“Lembretes”**, importância **DEFAULT**, som breve, sem repetição contínua; respeitar ajustes do usuário.
   Alarme — superfície `#10141C`, hora atual grande, nome completo, horário do compromisso e categoria com amostra de cor.
   Exemplo: **“20:20”**, **“Teclado”**, **“Começa às 20:30 · termina às 21:00”**.
   Botões empilhados, largura total, altura mínima **64dp**: **“Parar”** primário; **“Adiar 5 min”** secundário.
   Canal **“Alarmes”**, importância **HIGH**; som de despertador pelo volume de alarme, sem aumentar o volume à força.
   Bloqueado: mostrar sobre a tela de bloqueio quando autorizado; Parar/Adiar funcionam sem desbloquear, Abrir exige desbloqueio.
   Desbloqueado: aceitar a apresentação destacada escolhida pelo sistema, sem forçar abertura da Activity. [Android](https://developer.android.com/develop/ui/compose/notifications/create-notification)
   Limitar cada disparo a **2 minutos**; depois, parar som/vibração e deixar **“Alarme não atendido: Teclado”**, silencioso, com “Abrir”.
   Não repetir automaticamente; Parar encerra só aquele disparo, mantendo as próximas semanas.

4. **Permissões e estados reais**

   Android 13+: pedir notificações ao **salvar o primeiro lembrete ativo**, depois da escolha de alcance; nunca no login.
   Explicação: **“Permita notificações para receber os lembretes que você escolher.”**
   Se negada: **“Notificações bloqueadas. O lembrete foi salvo, mas os avisos estão desativados neste aparelho.”** Ação **“Abrir Configurações”**.
   Preservar a escolha e mostrar o bloqueio no editor e na barra; não reapresentar o pedido a cada salvamento.
   Sem autorização de precisão: **“Alarme pode atrasar. Permitir em Configurações”** — isso pressupõe implementar fallback inexato.
   Se notificações também usam agendamento exato, mostrar nelas **“O lembrete pode atrasar.”** [Alarmes exatos](https://developer.android.com/develop/background-work/services/alarms)
   **Tela cheia é uma terceira verificação**, especialmente no Android 14+: **“Tela cheia desativada. O alarme será exibido como notificação.”**
   Oferecer **“Permitir tela cheia”** e verificar novamente ao retornar; não prometer aprovação automática por ser uma agenda. [Android 14](https://developer.android.com/about/versions/14/behavior-changes-14)

5. **Padrão: silêncio**

   **Nenhum para novos compromissos e registros antigos sem propriedade.**
   Com 79 blocos semanais, notificação automática produziria fadiga e levaria ao bloqueio do aplicativo inteiro.
   Alarme exige escolha deliberada. Copiar/duplicar também deve começar sem lembrete, informando **“Novo compromisso sem lembrete”** na confirmação.
   Mover ou redimensionar preserva o lembrete existente e recalcula seu disparo.

6. **P0: o que impede lançar com segurança**

   **[Modelo.kt](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/Modelo.kt)** — adicionar modo/antecedência validados; distinguir ausência herdada de **Nenhum explícito** numa exceção.
   **[GoogleAgenda.kt](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/GoogleAgenda.kt)** — adaptar `paraBloco`, `corpo`, criação, alterações e `voltarRotina`; preservar propriedades e resolver herança sem presumir que a API faça tudo.
   Propriedade privada apenas armazena dados: não cria aviso no Google. Definir também os lembretes nativos para evitar duplicação e silêncio falso em “Nenhum”. [Google Calendar](https://developers.google.com/workspace/calendar/api/concepts/reminders)
   **[AgendaScreen.kt](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/AgendaScreen.kt)** — transportar lembrete por `Edicao`, `criar`, `aplicar` e fila; extrair `EditorCompromissoSheet`, `CampoLembrete` e `BarraInferiorAgenda`.
   O código atual salva otimisticamente: **agendamento local precisa acompanhar a mutação e sobreviver ao processo morrer**, sem esperar apenas outra carga da semana.
   **Novos `AgendadorLembretes.kt` e armazenamento persistente** — janela móvel desde agora, independente da semana visível; renovação automática, reconciliação e recuperação offline no boot.
   Carregar semana e reiniciar **não bastam**: após sete dias sem ambos, o plano fica mudo. A consulta precisa incluir eventos além da borda cuja antecedência cai dentro da janela.
   Usar identidade estável por conta/calendário/ocorrência; reconciliar IDs temporários; cancelar disparos antigos ao editar, excluir, restaurar rotina ou sair.
   **Novos receptor, serviço e `AlarmeActivity`/`TelaAlarme`, mais manifesto** — controlar som, adiamento, permissões e disparos simultâneos fora do Compose.
   Validar série versus exceção, reboot offline, app encerrado, Doze, permissões revogadas e adiamento após edição, incluindo Xiaomi.
   **Sem esses P0, o desenho promete um despertador e entrega uma tentativa de aviso.**
