A pinça faz sentido, mas exige um **modo de visão geral**. Apenas reduzir `alturaMeia` tornaria texto, alças e seleção inutilizáveis. A spec atual precisa abandonar a obrigação de mostrar nome e horário em qualquer escala.

**1. Escala**
- Mínimo dinâmico: **altura útil da grade ÷ 38**, descontando cabeçalho do tablet e áreas reservadas. Com 600dp: **15,8dp por meia hora**; não arredondar para 16dp se isso gerar rolagem.
- Padrão: **64dp no celular / 72dp no tablet**, ajustado para fonte ampliada.
- Máximo recomendado: **2× o padrão — 128dp / 144dp**. Mais que isso aumenta muito a navegação para pouco ganho.
- Pinça **contínua**, com limites; sem saltos obrigatórios para “encaixes”. Oferecer referências “Dia inteiro”, “Padrão” e “Detalhe” no controle de escala.
- Persistir a última escala entre sessões, separadamente por apresentação celular/tablet. Para “Dia inteiro”, persistir o modo e recalcular conforme a janela.
- Trocar de dia preserva escala e posição temporal; restauração não deve ser sobrescrita pela rolagem inicial para “agora”.

**2. Conteúdo e toque**
As faixas abaixo usam a altura total do bloco, com fonte normal; fonte ampliada exige medir o conteúdo e subir os limites.

| Altura do bloco | Conteúdo |
|---|---|
| Menos de 24dp | Cor, contorno de seleção; sem texto interno. |
| 24–43dp | Nome em uma linha, com reticências. |
| 44–63dp | Nome em uma linha + horário completo. |
| A partir de 64dp | Nome em até duas linhas + horário completo. |

- Manter nome **14sp/18sp** e horário **12sp/16sp**. Não encolher fonte junto com a grade; se não couber, retirar conteúdo.
- Reduzir padding e raio nos blocos pequenos; manter separação entre compromissos. Texto completo permanece na descrição acessível e no resumo da seleção.
- **Não existe como dar 48dp exclusivos a cada bloco de 16dp contíguo.** Ampliar áreas invisíveis sobre os vizinhos só torna o toque imprevisível.
- Na visão geral, resolver o toque na `Grade`: considerar candidatos numa região de 48dp; havendo vários, abrir uma lista com nome, horário e linhas de pelo menos 48dp.
- Um candidato: selecionar e mostrar os detalhes na barra inferior. Vários: selecionar pela lista. **Sem zoom automático ao selecionar.**
- Oferecer “Ampliar para ajustar”, levando ao padrão com o compromisso visível. Não aumentar apenas o bloco: isso falsearia sua duração.
- Ocultar alças e desabilitar manipulação direta abaixo da escala padrão; edição por formulário continua disponível. Na escala padrão, manter alças de 48dp e toque longo.
- Comprimir também a informação da régua: reduzir frequência dos rótulos quando necessário, sem reduzir a fonte; preservar os limites 05:00 e 24:00.

**3. Gestos**
- Reconhecedor de pinça no viewport da grade: **um dedo não é consumido por ele**; continua disponível para rolagem, toque e troca de dia.
- Segundo dedo cancela toque e espera de toque longo; ultrapassado o limiar de pinça, capturar zoom e suspender rolagem e navegação horizontal.
- Se já houver movimento/redimensionamento, segundo dedo cancela a prévia **sem salvar** e inicia o reconhecimento da pinça.
- Depois da pinça, exigir que todos os dedos saiam antes de aceitar outro gesto; o dedo restante não pode mover nem trocar o dia.
- Ancorar na hora sob o centro dos dedos: recalcular escala e deslocamento juntos, mantendo essa hora sob o centro atual. Limitar nas extremidades, onde preservar a âncora pode ser impossível.
- Um dedo horizontal: manter regra de predominância 1,5×, deslocamento de 64dp e ausência de seleção. Decidir o eixo antes de consumir o arrasto.
- Zoom altera somente apresentação; encaixe temporal continua em 30 minutos.

**4. Atalho**
- Recomendo **botão “Dia inteiro” no cabeçalho**, com alvo de 48dp; nesse modo, passa a “Padrão”. Disponibilizar “Detalhe” no mesmo controle.
- Não recomendo toque duplo: conflita com o contrato existente de segundo toque para editar e é pouco descobrível. O botão também torna o recurso acessível sem pinça.

**5. P0 — onde mexer e crítica**
- [AgendaScreen.kt — Grade](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/AgendaScreen.kt:463): escala única para desenho, coordenadas de toque, rolagem e conversão de arrasto; viewport medido e âncora preservada.
- [AgendaScreen.kt — BlocoView](/opt/agenda-semanal/app/src/main/java/br/maxymus/agenda/AgendaScreen.kt:536): conteúdo por altura, seleção compacta e alças condicionais. Hoje a `Column` sempre pede duas linhas + horário: reduzir altura simplesmente recorta conteúdo.
- `AgendaScreen.kt / AgendaScreen`: estado persistente, botão e resolução de candidatos. Reservar a barra antes de calcular “Dia inteiro”; seleção não pode provocar mudanças sucessivas de escala.
- **Os detectores atuais estão fragmentados entre pai, corpo e alças.** Acrescentar mais um detector sem arbitragem explícita é receita para troca de dia ou gravação acidental durante pinça.
- [DESIGN_ASTRA.md](/opt/agenda-semanal/DESIGN_ASTRA.md): registrar a exceção de visão geral e validar blocos contíguos de 30 minutos, fonte ampliada, extremos da grade e segundo dedo durante manipulação. **Esses critérios são P0, não acabamento.**
