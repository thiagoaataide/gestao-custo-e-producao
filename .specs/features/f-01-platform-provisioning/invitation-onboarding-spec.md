# F-01 — Cadastro pelo convite e ativação

**Status:** Especificação vinculante; T25 implementada localmente, com gate Maven e UAT de e-mail publicada pendentes.
**Decisão:** [ADR-026](../../../docs/adr/ADR-026-invitation-scoped-signup.md)

## Objetivo

Permitir que a pessoa destinatária conclua um convite mesmo sem possuir conta
Supabase Auth antes do convite. O cadastro cria a conta de autenticação; a
membership operacional só nasce depois da verificação do e-mail e do aceite
explícito.

## Jornada

1. A pessoa abre um convite válido. A aplicação não o aceita por abrir o link.
2. Se não houver sessão, ela pode entrar com uma conta existente ou iniciar
   cadastro usando o e-mail do convite, exibido sem possibilidade de alteração.
3. O cadastro envia um código OTP ao endereço convidado, e a pessoa o informa
   na tela para verificar o e-mail antes de aceitar. A regra vale para qualquer
   provedor/domínio, sem exceções. Uma identidade Auth existente que já tenha
   esse e-mail marcado como verificado pelo Supabase satisfaz a mesma validação;
   identidade não verificada precisa concluir o OTP.
4. A aplicação preserva o convite enquanto a pessoa troca da conta que já
   estiver autenticada para a conta do destinatário. A sessão Gmail do owner
   nunca serve como identidade implícita para aceitar um convite Outlook.
5. Após autenticar, a aplicação verifica no perfil Supabase que o e-mail está
   confirmado e corresponde ao convite. Só então apresenta a confirmação
   explícita de aceite.
6. No aceite, a aplicação cria/associa `ExternalIdentity`, ativa uma única
   membership `TENANT_USER`, marca o convite como aceito e audita tudo na mesma
   transação PostgreSQL.

## Estados e garantias

| Estado | Resultado |
| --- | --- |
| Convite válido, sem conta Auth | Permite cadastro apenas para o e-mail convidado; nenhum acesso operacional é criado. |
| Cadastro criado, e-mail ainda não verificado | Conta Auth pode existir; convite permanece pendente e operações do tenant continuam bloqueadas. |
| OTP inválido/expirado | Não confirma o e-mail nem altera o convite; permite reiniciar a verificação conforme o contrato suportado. |
| Sessão autenticada com outro e-mail | Não aceita o convite; preserva o contexto para sair e autenticar/cadastrar o destinatário. |
| E-mail confirmado e correspondente | Permite mostrar o convite e pedir ação explícita de aceite. |
| Aceite válido | Cria/associa identidade de domínio e membership, aceita convite e registra auditoria atomicamente. |
| Convite expirado/revogado ou e-mail incompatível | Não cria membership nem expõe dados de outro tenant; oferece solicitar novo convite. |
| Falha ao persistir aceite | Rollback completo dos registros e estados de domínio; a conta Supabase Auth pode permanecer sem membership. |

## Fronteiras

- O Supabase Auth mantém credenciais, subject e confirmação de e-mail.
- O cadastro não cria `ExternalIdentity` nem membership na base de domínio.
- O produto só oferece cadastro a partir da jornada de convite. Uma conta Auth
  criada fora do produto pode existir no provedor, mas continua sem identidade
  de domínio e sem autorização até concluir um convite correspondente.
- A aplicação é autoridade para associar subject, aceitar convite e autorizar o
  tenant; um registro Auth isolado não autoriza acesso.
- Não há cadastro geral, criação de tenant pelo usuário, mudança de papéis,
  vínculo com mais de um tenant ou acesso operacional antes do aceite.
- A UI de Membros definida em `ui-spec.md` continua sendo a superfície
  administrativa de convite. T25 cobre a jornada pública de cadastro/aceite,
  fora do escopo específico de T24.

## Dependência operacional

Configurar SMTP próprio no Supabase Auth e alterar o template **Confirm sign up**
para enviar `{{ .Token }}` (OTP) antes da UAT de produção. Confirmar e-mail
precisa permanecer habilitado. O remetente padrão hospedado é limitado e não é
uma garantia de entrega a destinatários externos. A entrega opcional do link
administrativo de convite é outro fluxo e não substitui a verificação Auth.
