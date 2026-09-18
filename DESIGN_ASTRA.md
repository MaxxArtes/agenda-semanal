> Atualização 18/09: o controle segmentado "Toda semana / Só este dia" do cabeçalho foi REMOVIDO; o alcance é escolhido ao concluir cada operação sobre uma rotina (ver DESIGN_ASTRA_ALCANCE.md). O contrato "soltar salva" vale para avulsos e exceções; para rotinas, soltar abre a folha de alcance.

**Especificação de redesign — Agenda Semanal**
Base: imagem fornecida, `AgendaScreen.kt`, `Modelo.kt` e `GoogleAgenda.kt`. Medidas em dp; tipografia em sp. Os 1080×2400 pixels da captura não equivalem às dimensões de layout do Android.

**1. Diagnóstico**

- O topo ocupa aproximadamente um quarto da tela com controles concorrentes: título, datas extensas, três botões contornados, alcance, saída e mensagem. A agenda virou conteúdo secundário.
- Contornos em quase tudo eliminam a hierarquia. A seleção do dia deveria dominar; navegação e saída deveriam recuar.
- “Toda semana / Só este dia” parece alternar a visualização. Na realidade, controla operações que podem modificar uma série inteira. Falta explicitar essa consequência.
- “Sair” ocupa espaço permanente de uma tarefa cotidiana com uma ação rara.
- A faixa de dias já identifica a data; repetir “Quinta” na grade desperdiça altura. O cabeçalho atual de 28dp ainda tenta acomodar duas linhas.
- Blocos de 30 minutos têm apenas 30dp no celular e 24dp no tablet. O código esconde seus horários e permite duas linhas de título: conteúdo e espaço são incompatíveis.
- Vários fundos de categoria usam texto claro com contraste fraco. A categoria não justifica sacrificar leitura.
- As alças de 16dp exigem precisão excessiva. Arrastar imediatamente o bloco selecionado também disputa o gesto com a rolagem.
- A barra contextual está fora da rolagem vertical, mas fica acima da grade e esconde ações atrás de rolagem horizontal. O nome do compromisso empurra os comandos.
- “Adicionar” cobre horários e futuros compromissos. A grade não deve funcionar como suporte para um botão sobreposto.
- “Semana vazia” não explica se apenas o dia está vazio. Sucesso, carregamento e falha disputam a mesma linha sem ação de recuperação.

**2. Sistema visual**

| Papel | Valor |
|---|---|
| Fundo | `#10141C` |
| Superfície da grade | `#181F2B` |
| Superfície elevada: barra e editor | `#232D3D` |
| Linha de meia hora / hora cheia | `#283345` / `#43516A` |
| Texto principal / secundário | `#F3F5FA` / `#B5C0D3` |
| Acento / texto sobre acento | `#8AA4FF` / `#10141C` |
| Perigo e linha da hora atual | `#FF707B` |

- Usar fonte padrão do sistema Android; pesos 400, 500 e 600. Sem fonte externa.
- Escala: título 20/24, título do editor 22/28, corpo e botões 14/20, título do bloco 14/18, metadados 12/16, número do dia 18/22. Formato: tamanho/entrelinha.
- Raios: blocos 8, controles 12, topo do sheet 24. Grade sem moldura arredondada envolvendo toda a área.
- Espaçamentos: 4, 8, 12, 16 e 24; margem lateral 12 no celular e 24 no tablet.
- Meia hora: **64dp no celular; 72dp no tablet**. Aceitar menos horas simultâneas para tornar os compromissos legíveis e manipuláveis.
- Com fonte ampliada, aumentar uniformemente a altura de todas as meias horas conforme a altura medida do conteúdo; nunca aumentar apenas um bloco e falsear sua duração.
- Preservar exatamente `Categoria.cor`, `chave` e `colorId` das 11 categorias. Não aplicar transparência ao fundo dos blocos.
- Ajustar apenas `Categoria.texto`: escolher entre `#10141C` e `#FFFFFF` pelo maior contraste calculado, exigindo pelo menos 4,5:1. Horários usam a mesma cor, sem redução de opacidade.
- Todo controle tem alvo mínimo de 48×48dp, inclusive ícones e alças; tamanho visual pode ser menor.

**3. Layout e estados**

**Cabeçalho e navegação**
- Primeira linha, 48dp: “Agenda Semanal” à esquerda; menu de três pontos à direita, contendo conta e “Sair”.
- Segunda linha, 48dp: seta anterior, intervalo abreviado “14–20 set. 2026”, “Hoje”, seta seguinte. Setas são ícones sem cápsulas contornadas.
- Intervalos entre meses: “28 set.–4 out. 2026”; entre anos, explicitar ambos os anos.
- Abaixo, legenda “Aplicar mudanças a” e controle segmentado “Toda semana / Só este dia”, com 48dp de altura.
- Na seleção, cópia e edição, repetir o alcance efetivo: “Série inteira” ou “Só 17 set.”. Exceções e eventos avulsos continuam restritos à data, conforme a lógica existente.

**Faixa de dias**
- Sete células de 48×56dp no mínimo, com abreviação e número; sem contorno em todos os dias.
- Dia selecionado: fundo de acento e texto escuro. Hoje: sublinhado de acento; manter o sublinhado contrastante quando também estiver selecionado.
- Se sete alvos não couberem, permitir rolagem horizontal apenas nessa faixa e manter o selecionado visível.
- Cabeçalho, alcance e faixa permanecem fixos; somente a grade rola verticalmente.

**Grade**
- Celular abaixo de 600dp: um dia. A partir de 600dp: sete colunas, preservando a visão semanal.
- Na visão semanal, largura mínima de coluna de 104dp; quando necessário, rolagem horizontal sincronizada entre cabeçalho e conteúdo, com coluna de horas fixa.
- Coluna de horas com 48dp; rótulos de 12sp nas horas cheias. Linhas de 1dp, diferenciadas pela cor.
- Remover o cabeçalho interno redundante no celular. No tablet, cabeçalho fixo com dia e data, altura mínima de 48dp.
- Preservar 05:00–24:00 e passo de 30 minutos. Mostrar o encerramento como “24:00”, evitando confusão com o início do dia.
- Primeira abertura em hoje: posicionar aproximadamente uma hora antes do horário atual; outros dias começam às 05:00. Preservar rolagem nas trocas de dia e após salvar.
- Linha atual vermelha de 2dp com ponto na coluna de horas, desenhada acima dos blocos; atualizar por minuto e na retomada do app.
- Usar `America/Cuiaba` para hoje e hora atual, coerente com `GoogleAgenda.kt`; atualizar também na virada do dia.

**Bloco e seleção**
- Texto alinhado à esquerda, padding horizontal de 8dp e vertical de 4dp. Intervalo de 2dp entre blocos adjacentes.
- Em 30 minutos: título com até duas linhas de 18sp de entrelinha; horário “08:00–08:30” em linha própria de 16sp. A altura proposta comporta ambos.
- Títulos arbitrariamente longos não cabem integralmente em largura finita: usar reticências apenas após duas linhas; nunca cortar o horário. Exibir nome integral no editor e na descrição acessível.
- Reservar posição para “•” junto ao título, fora da área truncável; descrição acessível: “Só nesta data”.
- Selecionado: contorno externo de acento de 2dp com separação escura de 1dp; preservar a cor da categoria.
- Alças visuais de 24×6dp, superior à esquerda e inferior à direita, cada uma com alvo de 48×48dp. Mantê-las dentro dos limites horizontais do bloco, sem recorte pelo cartão.
- A separação horizontal evita sobreposição dos alvos em blocos curtos. Alças têm prioridade de toque sobre o corpo e os blocos vizinhos.
- Durante movimento ou extensão, mostrar horário provisório em etiqueta acima do bloco, sem cobrir seu título.

**Barra inferior**
- Usar área fixa abaixo da grade, acima da navegação do sistema, com espaço reservado no layout. Nenhum botão cobre horários.
- Estado normal: barra de 64dp com “+ Adicionar”. Abrir no dia visível; no tablet, no último dia tocado, inicialmente hoje; manter horário padrão 08:00–09:00.
- Selecionado: resumo de nome, horário e alcance; primeira linha de comandos “Copiar”, “Duplicar”, “Editar”; segunda “Voltar à rotina”, quando aplicável, e “Desmarcar”.
- Comandos com altura mínima de 48dp, sem rolagem horizontal. Permitir quebra de rótulo e crescimento vertical com fonte ampliada.
- “Voltar à rotina” aparece somente quando `unico && serie != null`. “Duplicar” mantém a regra de inserir imediatamente depois; se não couber, desabilitar e explicar o motivo.
- Copiado: “Copiado: [nome]”, alcance efetivo, instrução “Toque numa hora vazia para colar” e “Parar”. Manter cópia ativa após colar.
- Colagem próxima à meia-noite preserva duração e ajusta o início para caber; comunicar o intervalo resultante.

**Editor**
- Substituir `AlertDialog` por `ModalBottomSheet`, inicialmente expandido: o formulário precisa de largura, rolagem e espaço para teclado.
- No tablet, centralizar o sheet com largura máxima de 560dp. Respeitar teclado e barras do sistema.
- Ordem: título, nome, dia/data, categoria com amostra de cor e nome, início/fim, alcance efetivo.
- Início e fim usam seleção em passos de 30 minutos; duração aparece abaixo. Erros ficam junto ao campo correspondente.
- Rodapé fixo: “Cancelar” e “Salvar”; “Remover” separado no corpo, em perigo. Confirmar remoção indicando data ou série inteira.
- Para avulso/exceção, mostrar alcance de data como informação fixa. Para recorrente, explicar: “Toda semana altera a série inteira, inclusive ocorrências passadas”.
- Manter o editor e seus valores durante salvamento; fechar somente após sucesso. Falha mantém o formulário disponível.

**Vazio, carregamento e erro**
- Após carga bem-sucedida: “Sem compromissos nesta quinta. Toque em um horário ou em Adicionar.” Se toda a semana estiver vazia, dizer “Sua semana está vazia”.
- Exibir a orientação em faixa discreta acima da grade, sem bloquear horários e sem ilustração grande.
- Carga inicial: estrutura da grade com “Carregando semana…” e indicador; impedir criação até conhecer os dados.
- Atualização: manter dados da mesma semana visíveis e indicar progresso discretamente. Nunca mostrar dados antigos como se pertencessem à nova semana.
- Salvamento: bloquear novas mutações até concluir; manter leitura e rolagem. Mostrar “Salvando…” na barra.
- Erro de leitura: faixa persistente “Não foi possível carregar” com “Tentar novamente”; erro de autorização com “Autorizar”.
- Se a escrita ocorreu e apenas a releitura falhou, informar “Alteração enviada; não foi possível atualizar”. Repetir somente a leitura, evitando duplicação.
- Não exibir exceções técnicas na interface; registrar detalhes para diagnóstico. “Sincronizado” deve ser confirmação breve, não uma linha permanente.

**4. Contrato de gestos**

- Primeiro toque no bloco seleciona; segundo toque no mesmo bloco abre o editor, sem exigir velocidade de duplo toque.
- Arrasto vertical imediato no corpo rola a grade, mesmo com seleção. Para mover, pressionar o bloco selecionado por 350ms sem superar o `touchSlop`; vibrar e capturar o gesto.
- Alças aparecem somente na seleção; arrastar após o `touchSlop` redimensiona imediatamente, sem espera adicional.
- Ao mover ou esticar, bloquear rolagem comum e troca de dia; encaixar em 30 minutos, mostrar prévia e salvar uma vez ao soltar.
- Movimento mantém duração; extensão mantém pelo menos 30 minutos. Limitar à faixa 05:00–24:00 e aos dias da semana exibida.
- Nas bordas verticais de 48dp, aplicar rolagem automática durante manipulação, de 60 a 240dp/s conforme a proximidade da borda.
- No celular, deslizar horizontalmente a grade troca dia somente sem seleção e sem gesto de manipulação; exigir predominância horizontal de 1,5× e deslocamento final de 64dp.
- Movimento predominantemente vertical fica com a rolagem; travar o eixo após reconhecê-lo. Nas extremidades da semana, não avançar automaticamente para outra semana.
- No tablet, gesto horizontal comum rola as colunas; movimento entre dias exige a pressão longa no bloco selecionado.
- Toque vazio com seleção apenas desmarca; com cópia cola; sem ambos abre criação. Preservar essa ordem.
- Cancelamento restaura a posição anterior sem gravar. Oferecer ações acessíveis “Mover”, “Alterar início” e “Alterar fim” pelos campos do editor.

**5. Implementação priorizada**

- **P0 — `AgendaScreen.kt / Grade e BlocoView`:** corrigir altura dos intervalos, título+horário, alvos das alças, arbitragem dos gestos e prévia de movimento; a prévia deve coincidir exatamente com o resultado gravado.
- **P0 — `AgendaScreen.kt / AgendaScreen`:** substituir botão sobreposto e barra horizontal por `BarraInferiorAgenda`; explicitar alcance real em seleção, cópia e editor.
- **P0 — `Modelo.kt / Categoria.texto`:** corrigir contraste mantendo as 11 cores, chaves e `colorId`.
- **P0 — `AgendaScreen.kt / executa` e `GoogleAgenda.kt / mensagem`:** separar carga, escrita e atualização; distinguir falhas, preservar rascunho e impedir reenvio acidental após escrita concluída.
- **P1 — `AgendaScreen.kt / AgendaScreen`:** extrair `CabecalhoAgenda`, `SeletorAlcance` e `FaixaDias`; aplicar hierarquia compacta e menu de conta.
- **P1 — `AgendaScreen.kt / editor e Seletor`:** extrair `EditorCompromissoSheet`; aplicar formulário, validação, rodapé e confirmação de remoção.
- **P1 — `AgendaScreen.kt / Grade`:** cabeçalho fixo no tablet, rolagem preservada, largura mínima de coluna e estados de vazio/carga/erro.
- **P1 — `AgendaScreen.kt / Grade` e `GoogleAgenda.kt / fuso`:** compartilhar referência de fuso; atualizar hoje e linha atual corretamente.
- **P2 — `AgendaScreen.kt / Grade, BlocoView e novos controles`:** finalizar fonte ampliada, TalkBack, vibração e transições curtas; validar em 360dp, no Xiaomi da captura e em tablet, incluindo compromissos contíguos de 30 minutos.
