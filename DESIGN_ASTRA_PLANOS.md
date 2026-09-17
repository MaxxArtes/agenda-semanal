A escolha deve aparecer no primeiro toque em **“Pedir”**, dentro da própria folha do assistente. A agenda não precisa ganhar uma vitrine de planos. O problema principal é separar três decisões: enviar dados, pagar pela interpretação e aplicar mudanças.

**1. Entrada e plano atual**

- Primeiro acesso: abrir folha expandida “Escolha como usar o assistente”, sem opção pré-selecionada.
- Após escolher: abrir “Pedir ao assistente”, preservando qualquer rascunho.
- Próximos acessos: abrir diretamente o formulário.
- Abaixo do título, mostrar o estado e a ação “Trocar”:
  - “Gratuito · pode usar dados para treino”
  - “Pago · saldo R$ 8,35”
  - “Chave própria · cobrança no OpenRouter”
- Não escrever “treina modelos”: transforma uma possibilidade em certeza.
- Menu da agenda: item “Assistente” abre gerenciamento de plano, saldo, chave e histórico.
- Mostrar o e-mail Google nessa área e na recarga; não ocupar o cabeçalho da agenda.
- Consultar plano e saldo no servidor. Durante a consulta: “Carregando seu plano…”.
- Nunca trocar automaticamente para gratuito por falta de saldo ou falha do pago.
- Voz apenas preenche o campo para revisão; não envia nem cobra automaticamente.

**2. Folha de planos**

- Dois cartões verticais, nesta ordem: “Gratuito” e “Pago”; opção de chave abaixo.
- Mesma hierarquia visual, sem selo “Recomendado”, promoção ou plano previamente marcado.
- Cartão selecionado: contorno de acento, indicador de seleção e texto “Selecionado”.
- Tocar no cartão seleciona; o botão inferior confirma a escolha.

**Cartão “Gratuito”**
- “R$ 0”
- “Modelos gratuitos do OpenRouter.”
- “Limite diário compartilhado entre quem usa o plano gratuito.”
- “Os provedores podem usar os dados enviados para treinar modelos.”
- Ao selecionar, expandir o consentimento no próprio cartão.
- Aviso: “Enviamos seu pedido e os dados da semana exibida para interpretar o que você quer mudar. Os provedores podem usar esses dados para treinar modelos.”
- Checkbox inicialmente vazio: “Li e concordo com esse uso dos dados.”
- Botão “Concordo”, habilitado somente após marcar; alternativa “Voltar”.
- Registrar aceite por conta, com data e versão do aviso; não repetir em cada aparelho.
- Após aceite, ativar gratuito e abrir o formulário. Nenhum pedido é enviado nessa etapa.
- Mudança material no uso dos dados exige novo aceite antes de outro envio.

**Cartão “Pago”**
- “R$ 0,05 por pedido”
- “Claude Haiku 4.5 · sem uso dos dados para treino.”
- “Sem limite diário. Usa seu saldo pré-pago.”
- “Recarga por Pix. Sem cartão e sem mensalidade.”
- Botão “Usar pago”; selecionar não gera cobrança.
- Com saldo suficiente, abrir formulário; com saldo insuficiente, abrir recarga, permitindo voltar.

**Opção “Usar minha chave do OpenRouter”**
- “Os pedidos são cobrados na sua conta do OpenRouter.”
- “Uso dos dados e limites dependem do modelo, do provedor e das suas configurações.”
- Abrir etapa com campo mascarado, “Mostrar chave”, “Salvar e usar” e “Cancelar”.
- Explicar: “A chave é salva neste aparelho e enviada ao servidor do app para fazer os pedidos.”
- Chave existente não deve ser apagada ao trocar de plano; oferecer “Remover chave” separadamente.
- Em outro aparelho, pedir a chave novamente quando esse modo estiver ativo.

**3. Recarga Pix**

- Usar etapas dentro da mesma folha expandida; não empilhar vários modais.
- Título “Adicionar crédito”; abaixo, e-mail da conta e saldo atual.
- Valores em controles de seleção: “R$ 5”, “R$ 10”, “R$ 20”; começar sem seleção.
- Complementos: “100 pedidos”, “200 pedidos”, “400 pedidos”, pelo preço atual.
- Botão após seleção: “Gerar Pix de R$ 10”.
- Enquanto gera: “Gerando Pix…”; bloquear toque repetido.
- Próxima etapa: “Pagar R$ 10 por Pix”, destinatário e conta que receberá o crédito.
- QR de aproximadamente 240dp, preto sobre branco, com margem livre; não aplicar a paleta escura ao código.
- Botão evidente “Copiar código Pix”; retorno “Código copiado”.
- Apoio: “Abra seu banco e cole o código na opção Pix Copia e Cola.”
- Mostrar “Expira em 14:32”, calculado pela expiração informada pelo servidor.
- Estado persistente: “Aguardando pagamento. A confirmação é automática.”
- Webhook atualiza o servidor; o app consulta esse estado enquanto a etapa está aberta e ao retornar do banco.
- Fechar não cancela a cobrança: permitir retomar “Pix pendente” em Assistente.
- Sucesso somente após confirmação do servidor: “Pagamento confirmado”.
- Exibir “R$ 10 adicionados · saldo R$ 18,35” e botão “Voltar ao pedido”.
- Expiração confirmada: “Este Pix expirou” + “Gerar outro”.
- Falha ao gerar: “Não foi possível gerar o Pix” + “Gerar outro”.
- Falha ao consultar: “Não conseguimos verificar o pagamento” + “Verificar novamente”; não incentivar outro pagamento.
- Se o tempo acabar durante uma consulta pendente, verificar antes de declarar expiração. Crédito confirmado depois continua sendo reconhecido.

**4. Bloqueios**

- Pago com menos de R$ 0,05: “Saldo insuficiente. Cada pedido custa R$ 0,05.”
- Ação principal “Adicionar crédito”; secundária “Trocar plano”. Preservar o pedido.
- Gratuito esgotado: “O limite diário compartilhado do gratuito foi atingido.”
- Mostrar próxima liberação somente com informação do servidor, incluindo horário e fuso.
- Ação “Ver plano pago”; apoio “R$ 0,05 por pedido, com saldo pré-pago”.
- Sem internet: “Sem conexão. Seu pedido foi mantido.” + “Tentar novamente”.
- Não enviar pedidos automaticamente quando a conexão voltar.
- Se houve envio sem resposta: “Verificando seu pedido…”; consultar o mesmo pedido antes de permitir reenvio.
- Saldo não atualizado deve aparecer como “Último saldo conhecido”, sem habilitar cobrança com base apenas no cache.

**5. Cobrança e histórico**

- **Decisão proposta:** cobrar pela interpretação concluída, antes da aplicação na agenda.
- Botão de envio: “Entender · R$ 0,05”.
- Apoio: “Cobramos quando a interpretação fica pronta, mesmo se você não aplicar as mudanças.”
- Uma interpretação com várias ações custa R$ 0,05 no total.
- Pergunta de esclarecimento também é resposta: informar “Cada novo envio custa R$ 0,05”.
- Falha técnica ou resposta inválida não deve consumir saldo; reserva eventual deve ser liberada.
- Confirmação: “Este pedido custou R$ 0,05”; botão “Aplicar (2)”.
- **Rejeitar “Aplicar (2) · R$ 0,05”** nesse contrato: sugere que desistir evita uma cobrança já realizada.
- Aplicar ou repetir uma sincronização não consulta novamente o modelo nem cobra outra vez.
- Histórico mínimo é P0: saldo sem explicação de entradas e saídas é insuficiente.
- Acesso “Histórico” na área Assistente, junto do saldo.
- Lista com data/hora, “Recarga Pix +R$ 10,00”, “Pedido −R$ 0,05”, “Estorno +R$ 0,05” e situação.
- Não reproduzir textos dos pedidos no extrato por padrão; detalhe mostra identificador e estado da operação.

**6. P0/P1 e crítica da implementação**

- **P0 — `AgendaScreen.kt / AgendaScreen`:** encaminhar “Pedir” e menu para um único fluxo, preservando rascunho e resposta.
- **P0 — `AssistenteSheet.kt / AssistenteSheet`, `EstadoPlano`, `ConfirmacaoAcoes`:** plano visível, preço antes do envio, formulário rolável e rodapé fixo.
- **P0 — `PlanosAssistenteSheet.kt / CartaoPlano`, `ConsentimentoGratuito`, `ChavePropria`:** escolha explícita, consentimento persistido e configuração da chave.
- **P0 — `RecargaPixSheet.kt / SeletorRecarga`, `PagamentoPix`, `ResultadoRecarga`:** geração, retomada, expiração e confirmação real.
- **P0 — `HistoricoAssistente.kt / HistoricoAssistente`:** extrato básico com recargas, débitos e estornos.
- **P0 — `Assistente.kt` e novo `AssistenteViewModel.kt`:** estado por conta, erros específicos, identificadores de operação e recuperação após interrupção; servidor decide saldo, aceite e cobrança.
- **P0 — sistema visual compartilhado:** extrair os tokens privados de `AgendaScreen.kt`; evitar cores duplicadas nos novos arquivos.
- Fundo `#10141C`; cartões `#181F2B`; folha `#232D3D`; texto `#F3F5FA` / `#B5C0D3`.
- Acento `#8AA4FF` com texto `#10141C`; erro `#FF707B`; contorno neutro `#43516A`.
- Títulos 22/28; corpo e botões 14/20; metadados 12/16. Aviso de consentimento é corpo, não nota minúscula.
- Raios 12dp nos controles e 24dp no topo; espaçamentos 4/8/12/16/24; alvos mínimos de 48dp; largura máxima 560dp.
- **P1 — `HistoricoAssistente.kt`:** filtros e detalhes ampliados; **`PagamentoPix`**: compartilhamento do código pelo Android.

O código atual envia também os compromissos da semana: consentir apenas com “o texto do pedido” esconderia parte do envio. A frase “a chave fica só neste aparelho” também é enganosa: `Assistente.pedir` a transmite ao servidor. E a resposta permanece aplicável mesmo após editar o campo; isso precisa ser invalidado. Acrescentar cartões de preço sem corrigir esses três pontos produziria uma interface comercialmente clara e operacionalmente desonesta.
