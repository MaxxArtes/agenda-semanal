# Autoupdate sem toque: estratégia (Astra, 18/09/2026)

Pedido do dono: "gostaria de saber se teria como colocar um autoupdate, sem eu precisar ficar clicando".
Consulta ao Astra (codex gpt-6-astra) em 18/09; resposta integral guardada na sessão. Resumo do que vale.

## Decisão recomendada: Google Play, faixa de teste interno, mantendo a chave atual

Único caminho em que a Play instala sozinha as próximas versões, tira o aviso do Play Protect por app
desconhecido e já deixa a conta registrada como desenvolvedor verificado (regra do Google que começa em
30/09/2026 para lojas participantes e em 2027 para todo app). Promessa correta: "depois da adesão inicial a
Play pode atualizar sozinha, conforme as configurações do aparelho". Não é "nunca mais aparece confirmação".

Correções do Astra sobre o que eu tinha suposto:
- A primeira release em teste interno PASSA por revisão (horas a 7 dias). As seguintes normalmente não.
- Conta pessoal nova não precisa dos 12 testadores/14 dias para a faixa interna (isso é para produção).
  Precisa: US$ 25, verificação de identidade e comprovação de aparelho físico com a conta do dono.
- Exigência de target já é API 36 (desde 31/08/2026), não 35. Hoje o app está em 34.
- App só em faixa interna é dispensado do formulário Segurança dos dados; política de privacidade curta ainda vale.
- Assinatura: importar a chave atual (alias camera) no Play App Signing via PEPK, criar chave de UPLOAD separada
  para o CI. Motivo decisivo: atualizar por cima da 0.13 instalada sem desinstalar. Ressalva: a chave é
  compartilhada com camera-estudo; importar aqui não migra o outro app.
- Distribuído pela Play, o app NÃO pode se atualizar por outro canal: tirar o download de APK do Atualizador
  e trocar por "Ver na Google Play".
- Risco próprio do app: USE_FULL_SCREEN_INTENT pode não ser concedido na instalação pela Play (só alarme/chamada
  como função central). Validar alarme em tela bloqueada depois da migração.
- Escopo OAuth `calendar` é amplo; a Play não verifica OAuth. Estudar `calendar.app.created` depois, não na migração.
- build.gradle.kts cai para assinatura debug quando faltam credenciais: precisa FALHAR, não sair em silêncio.

## Alternativas descartadas
- PackageInstaller com USER_ACTION_NOT_REQUIRED: API oficial (app pode atualizar a si mesmo), mas exige
  REQUEST_INSTALL_PACKAGES (gatilho observado do "app nocivo" em 17/09) e não resolve a verificação de 2026.
- Desligar o Play Protect: desprotege o aparelho e continua exigindo toque.
- Obtainium: faz instalação silenciosa em Android 12+ se ele instalou a versão atual e "atualizações em segundo
  plano" ligadas. Alternativa real para um único dono, sem sair do sideload. Fica como plano B testável.

## Textos (pt-BR, sem emoji) para a versão de transição
| Onde | Texto |
|---|---|
| Menu | Atualizações |
| Aviso | Receba as próximas atualizações pela Google Play. |
| Complemento | Participe do teste e atualize o app pela loja. Não é necessário desinstalar. |
| Ação | Continuar na Google Play |
| Secundária | Agora não |
| Menu após migração | Ver na Google Play |
| Sobre | As atualizações são distribuídas pela Google Play. |
| Falha | Não foi possível abrir a Google Play. Tente novamente. |
O botão leva primeiro ao link de adesão ao teste. "Não é necessário desinstalar" só depois de provar a
atualização sobre a 0.13. Para um único dono, não vale lançar APK intermediário só para anunciar.

## Ordem de execução
1. Preparar release: target 36, AAB, versionCode acima de todo APK distribuído, Atualizador apontando para a Play,
   gradle falhando sem chave.
2. Dono: criar conta Play Console (US$ 25), termos, identidade, aparelho. Só ele.
3. Configurar app no Console: pacote br.maxymus.agenda, App Signing com a chave atual, chave de upload, faixa
   interna com a conta do aparelho.
4. Enviar, esperar a revisão da primeira release, conferir registro do pacote na verificação Android.
5. Dono adere pelo link e atualiza pela loja sobre a 0.13, sem desinstalar.
6. Validar login, agenda, lembretes (tela bloqueada, após reinício), opção de atualização automática.
7. Publicar outra release e ver chegar sozinha. Só aí o objetivo está demonstrado.

## Evidências exigidas antes de dar por fechado
Certificado do APK da Play igual ao instalado; atualização sobre a 0.13 mantendo preferências e calendário;
login e escrita no Google Agenda no artefato da Play; alarme em tela bloqueada após atualização e reinício;
segunda release instalada sem abrir a loja; comportamento real do Play Protect registrado; CI com assinatura
correta e versões crescentes, canal APK nunca acima da Play.
