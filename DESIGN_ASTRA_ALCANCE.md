**1. Recomendo (b): escolher o alcance ao concluir a ação sobre uma rotina.**
O alcance pertence à operação; um modo global é fácil de esquecer e pode alterar semanas passadas.
(a) apenas desloca esse risco para a barra; (c) torna o gesto dependente de uma configuração distante.
A pergunta acrescenta um toque, mas evita que um arrasto local vire uma alteração histórica silenciosa.

Ao soltar, manter a prévia e abrir uma folha curta, **antes de alterar os dados locais e enfileirar**:
- Título: **“Aplicar movimento a…”**
- Ações: **“Mover só 19 set.”**, **“Mover toda semana”** e **“Cancelar”**.
- Aviso junto à opção semanal: **“Altera a série inteira, inclusive semanas passadas.”**

Nas alças, usar **“Alterar horário só 19 set.”** / **“Alterar horário toda semana”**.
Fechar a folha cancela e restaura a posição; gesto sem mudança não abre pergunta.
Avulsos e exceções continuam salvando ao soltar, com alcance restrito à ocorrência.
Após escolher, atualizar imediatamente e enviar pela fila existente, sem esperar a rede.
Isso muda deliberadamente o contrato de “soltar salva” para rotinas; precisa constar na spec.

**2. Barra do selecionado: informar, sem outro seletor persistente.**
Logo abaixo de nome/horário e acima de “Copiar / Duplicar / Editar”:
- Rotina: **“Rotina semanal · Alcance definido ao aplicar”**.
- Avulso/exceção: **“Mudanças: só 19 set.”**.

Texto secundário de 12sp, sem cápsula; informação estática não precisa virar botão.
Todos os comandos e opções da folha: alvo mínimo **48×48dp**, crescendo com fonte ampliada.
No modo cópia: **“Copiado: [nome]”** e **“Toque num horário para escolher como colar”**.
Ao colar ou duplicar, escolher **“Só 19 set.”** / **“Toda semana”**, indicando a data de destino.
Essas ações criam outro compromisso: não devem sugerir que modificam a série de origem.

**3. Assistente: ausência de alcance significa “só nesta data”.**
Criação vira avulso; alteração/remoção afeta somente a ocorrência indicada.
A prévia deve explicitar a data; alcance semanal exige intenção explícita no pedido.
**O código já usa esse padrão:** `Assistente.kt` interpreta alcance ausente como `"dia"`.
Em `AgendaScreen`, o assistente passa `a.alcance` explicitamente; não herda o seletor global.

**4. P0 — retirar só a interface seria uma regressão perigosa.**

- [AgendaScreen.kt / AgendaScreen](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/AgendaScreen.kt:110): remover a linha do cabeçalho **e a dependência do estado global `alcance`**. Deixá-lo oculto em `"semana"` cria uma armadilha.
- `AgendaScreen / aplicar, criar, remover`: exigir alcance explícito em cada chamada; nenhum padrão semanal implícito. O editor mantém seu campo local, inicialmente “Só <data>”.
- `AgendaScreen / Grade / BlocoView`: encaminhar movimento e extensão para uma operação pendente; novo `ConfirmarAlcanceSheet` decide antes da mutação e do enfileiramento. Guardar ocorrência original e destino.
- `AgendaScreen / barra inferior`: extrair `BarraInferiorAgenda`; aplicar o mesmo fluxo a duplicação e colagem. Hoje é possível exibir alcance de data no selecionado e duplicar usando o global semanal: **a interface promete uma coisa e executa outra**.
- `Assistente.kt`: validar valores de alcance; desconhecido não pode cair em recorrência por ser diferente de `"dia"`.
- [DESIGN_ASTRA.md](/opt/agenda-semanal/DESIGN_ASTRA.md): corrigir cabeçalho, barra e contrato de gestos. Verificar cancelamento sem escrita, confirmação com uma única escrita, exceção preservando a série e alcance semanal explícito.
