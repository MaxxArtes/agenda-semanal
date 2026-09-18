A tela transforma uma limitação da grade em dúvida sobre a consulta. **09:20 é uma informação precisa.** Cobrar outra rodada para escolher um horário errado é uma falha de produto.

**1. Resposta e fichas tocáveis**

- Após interpretar, recolher o teclado, retirar os exemplos e reduzir o pedido original a duas linhas expansíveis.
- Corpo rolável; rodapé fixo com “Fechar” e **“Aplicar”**, acima da navegação do sistema.
- Ordem: proposta → suposições → sugestões opcionais. A proposta precisa funcionar sem escolher fichas.
- Exemplo de cartão:
  - “Consulta médica”
  - “Sábado, 19/09/2026 · 09:20–10:00”
  - “Local: FÁCIL · Rua Comandante Costa, 1585”
  - “Fim sugerido: 10:00. Sem deslocamento reservado.”
- Abaixo: **“Quer ajustar?”**, seguido do aviso de consumo e das fichas.
- Máximo de **6 fichas**, em até dois grupos; quebra de linha, sem carrossel horizontal.
- Prioridade: resolver dúvida essencial → ajustar término → reservar deslocamento.
- Grupo “Término”: “Até 10:00”, “Até 10:30”, “Até 11:00”.
- O término já proposto aparece como indicação estática “Até 10:00 · proposto”; não cobrar por repetir o mesmo estado.
- Grupo “Deslocamento”: “30 min antes e depois”, “1 h antes”, “Sem deslocamento”.
- “Sem deslocamento” também fica estático quando já corresponde à proposta.
- Fichas com contorno neutro, texto de acento, alvo mínimo **48×48dp**, espaçamento de 8dp e texto sem truncamento.
- “Aplicar” é o único botão preenchido de acento; apoio: “Salva esta proposta sem nova rodada.”
- Tocar numa ficha envia imediatamente o refinamento; não salva na agenda.
- Exemplo de `pedido`: “Mantenha a consulta em 19/09/2026 às 09:20 e altere somente o término para 10:30.”

**2. Processamento e substituição**

- Na ficha tocada, mostrar indicador e “Ajustando…”; acima do cartão, “Ajustando término para 10:30…”.
- Manter a proposta anterior visível durante a espera; não apagar `resposta` ao enviar.
- Bloquear fichas, envio e “Aplicar” durante o processamento, evitando aplicar uma versão enquanto outra chega.
- A resposta nova substitui o cartão inteiro, com todas as ações finais; nunca acrescentar ações cumulativamente.
- Mostrar uma linha curta: “Último ajuste: término às 10:30”.
- Manter somente a última proposta expandida; “Ver conversa” abre pedidos e respostas anteriores, sem botões de aplicação.
- Separar “Ver conversa” do “Histórico de cobranças”; são informações diferentes.
- Em falha confirmada, restaurar os controles da proposta anterior: “Não foi possível ajustar. A proposta anterior foi mantida.”
- Se o resultado do envio for desconhecido, consultar a mesma operação antes de liberar reenvio.
- Refinamentos sucessivos preservam escolhas anteriores; trocar o término não pode apagar o deslocamento escolhido.

**3. Consumo sem poluição**

- Pago, uma linha imediatamente antes das fichas: **“Cada ajuste custa R$ 0,05.”**
- Apoio compartilhado: “Cobrado quando a resposta fica pronta, mesmo sem aplicar.”
- Gratuito: **“Cada ajuste usa 1 dos 30 pedidos diários.”** Cabeçalho: “2 de 30 usados hoje”.
- Preço, limite e contador vêm do servidor; não fixar esses números no app.
- Não repetir preço em cada ficha nem abrir confirmação a cada toque; incluir o consumo na descrição acessível.
- Ao concluir, atualizar saldo/contador e mostrar “Ajuste concluído · R$ 0,05” ou “Ajuste concluído · 1 pedido usado”.
- Falha técnica ou resposta inválida não consome rodada; saldo insuficiente bloqueia refinamentos, mas permite aplicar a proposta pronta.

**4. Regra para o prompt do servidor**

- **PROPOR** quando houver uma interpretação principal sustentada pelo pedido e os dados ausentes admitirem padrões explícitos e reversíveis.
- Retornar ações completas e válidas, suposições no resumo, `pergunta` nula e sugestões opcionais.
- Só início informado: preservar o início e sugerir término; nunca interromper apenas por faltar duração.
- Padrão proposto: duração mínima de 30 minutos, com término na próxima meia hora que satisfaça isso; 09:20 resulta em 10:00.
- Informar “Fim sugerido”; não apresentar duração inferida como duração confirmada pelo estabelecimento.
- Local externo: propor o compromisso sem deslocamento e oferecer sua inclusão. Não presumir origem, trânsito ou duração de trajeto.
- Reunião só recebe sugestões de deslocamento se houver indicação de encontro presencial ou endereço.
- Dia com interpretação dominante: propor, explicitar a data e oferecer alternativas plausíveis.
- **PERGUNTAR**, com ações vazias, quando escolher implicaria inventar informação essencial: nenhum dia identificável, datas contraditórias, vários eventos possíveis para excluir ou alcance de alteração indefinido.
- Fazer uma pergunta objetiva e oferecer até três respostas tocáveis, com datas completas: “Sábado, 19/09” e “Sábado, 26/09”.
- Nesse caso, “Aplicar” não aparece: não existe proposta válida. Essa é a exceção necessária à aplicação imediata.
- Nunca perguntar se 09:20 significa 09:00 ou 09:30. Recomendo revisar a especificação anterior para aceitar minutos exatos; a grade pode continuar marcada a cada 30 minutos.
- Cada refinamento recebe o pedido e a resposta completos da rodada anterior, além do contexto acumulado; devolver uma proposta completa que preserve decisões já tomadas.

**5. Deslocamento**

Criar blocos separados “Deslocamento — ida” e “Deslocamento — volta”, na categoria `desloc`.
Isso preserva o horário real da consulta e permite editar ou remover cada trecho sem alterar sua duração.
Calcular antes do início/depois do término, mostrar os três intervalos na prévia e sinalizar conflitos sem mover outros compromissos automaticamente.

**6. P0/P1 por arquivo**

- **P0 — `AssistenteSheet.kt / AssistenteSheet`:** extrair `RespostaAssistente`, `SugestoesAssistente` e `RodapeAssistente`; corpo rolável, fichas de 48dp e aplicação independente dos ajustes opcionais.
- **P0 — `Assistente.kt / Resposta, pedir`:** ler `sugestoes`, enviar contexto completo e validar ações; o atual par pedido/pergunta é insuficiente.
- **P0 — novo `AssistenteViewModel.kt`:** controlar versão da proposta, refinamento pendente e identificador de operação; preservar estado e impedir cobrança duplicada.
- **P0 — prompt/validador do servidor:** implementar PERGUNTAR/PROPOR e proposta completa; arquivo do servidor não localizado neste repo.
- **P0 — `Assistente.kt`, `AgendaScreen.kt / Grade, BlocoView` e `GoogleAgenda.kt`:** garantir minutos exatos na interpretação, prévia, edição e gravação; não resolver apenas no texto.
- **P0 — `AssistenteSheet.kt / PlanosEtapa, HistoricoEtapa`:** explicitar que refinamento é rodada cobrada e atualizar consumo após cada conclusão.
- **P1 — novo `ConversaAssistente.kt / ConversaAssistente`:** consulta das rodadas anteriores e diferenças resumidas entre propostas.

Hoje o formulário domina a tela, a resposta desaparece ao reenviar e o contexto só sobrevive quando não há ações. Acrescentar chips sem corrigir esses estados produziria um fluxo mais rápido para perder informação e gastar rodadas.
