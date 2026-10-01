# Lean Inception: Cofre

> Decisões de produto do Cofre no formato Lean Inception (Paulo Caroli):
> visão → escopo → personas → jornadas → funcionalidades → sequenciamento → MVP.

---

## 1. Visão do produto

**Para** cooperativas de crédito, fintechs em fase de protótipo e equipes que estudam sistemas bancários
**cujo** problema é ter um núcleo bancário confiável (saldo que nunca fica errado, Pix no padrão do Banco Central, controle de acesso por papel) sem montar um core bancário inteiro,
**o Cofre** é um banco digital com app do cliente, balcão da agência, backoffice do gerente, API e terminal
**que** abre contas, movimenta dinheiro por Pix e transferência, oferece cheque especial, caixinhas e cartão virtual, e registra cada ação numa auditoria.
**Diferente de** exemplos didáticos que usam `double`, não têm login e deixam o saldo ficar negativo sem regra,
**o nosso produto** tem regras de dinheiro no domínio, perfis com permissões testadas, Pix com BR Code real e testes rodando em H2 e PostgreSQL.

## 2. O produto É / NÃO É / FAZ / NÃO FAZ

| É | NÃO É |
|---|---|
| Um núcleo bancário com app, balcão e backoffice | Um banco real ou um sistema homologado pelo Banco Central |
| Um Pix entre contas do Cofre no padrão BR Code | Um participante do SPI (não liquida Pix com outros bancos) |
| Um sistema com perfis e auditoria | Um aplicativo nativo de celular (é web responsivo) |

| FAZ | NÃO FAZ |
|---|---|
| Pix por chave, QR Code e copia e cola, com limite noturno | TED, boletos ou Pix com outros bancos |
| Cheque especial com juros diários | Empréstimos parcelados ou financiamentos |
| Caixinhas com meta | Investimentos com rendimento |
| Cartão virtual com CVV dinâmico | Autorização de compras de cartão |
| Auditoria de todas as ações | Guardar senha ou CVV em texto |

## 3. Objetivos do produto

1. **Saldo sempre correto:** nenhuma operação passa do limite ou perde centavos (`BigDecimal`, transação única, concorrência otimista, idempotência).
2. **Cada um no seu papel:** o cliente só vê o que é dele; o caixa só opera o balcão; o gerente administra, e tudo fica auditado.
3. **Pix de verdade:** chaves, BR Code com CRC16, confirmação do destinatário e limites por horário, como no regulamento.
4. **Fácil de avaliar:** sobe com um comando, já com clientes, funcionários e seis meses de movimentação.

## 4. Personas

### Mario, cliente (34 anos)
- **Comportamento:** recebe salário por Pix, paga tudo pelo celular, guarda dinheiro para trocar de carro.
- **Necessidades:** ver o saldo rápido, pagar com QR Code, conferir para quem está mandando o dinheiro.

### João, cliente no aperto (41 anos)
- **Comportamento:** usa o cheque especial no fim do mês.
- **Necessidades:** saber quanto do limite está usando e quanto está pagando de juros.

### Antônio, caixa da agência (58 anos)
- **Comportamento:** atende no balcão; prefere o terminal, rápido e sem distrações.
- **Necessidades:** achar a conta pelo nome ou CPF, depositar e sacar sem erro.

### Carla, gerente (45 anos)
- **Comportamento:** abre contas, define limites, age rápido em suspeita de fraude.
- **Necessidades:** indicadores da agência, bloqueio com motivo, histórico de quem fez o quê.

## 5. Jornadas

**Mario paga a aula de violão por QR Code**
1. A professora gera a cobrança no app dela (QR Code de R$ 120,00).
2. Mario cola o código, o Cofre mostra "Beatriz Lima, CPF ***.508.249-**" e o valor.
3. Mario confirma e recebe o comprovante com a autenticação da transação.

**Carla abre a conta de um cliente novo**
1. Informa nome, CPF, e-mail, agência, depósito inicial e limite.
2. O Cofre mostra "Olá Rafael, obrigado por criar uma conta em nosso banco..." e uma senha provisória.
3. No primeiro acesso, o cliente é obrigado a criar uma senha própria.

**Carla bloqueia uma conta por suspeita de fraude**
1. Encontra a conta, clica em Bloquear e informa o motivo.
2. A conta continua recebendo, mas nenhum Pix, transferência ou saque é aceito.
3. A ação fica na auditoria, com o motivo e a origem.

## 6. Funcionalidades e revisão técnica

| Funcionalidade | Esforço | Valor de negócio | Valor de UX |
|---|---|---|---|
| Contas, depósito, saque, extrato | E | $$$ | ♥♥♥ |
| Transferência atômica e idempotente | EE | $$$ | ♥♥ |
| Login com perfis (cliente, caixa, gerente) | EEE | $$$ | ♥♥ |
| Bloqueio após senhas erradas, senha provisória | EE | $$$ | ♥ |
| Cheque especial com juros diários | EE | $$$ | ♥♥ |
| Pix: chaves e envio com confirmação | EE | $$$ | ♥♥♥ |
| Pix: BR Code (QR Code e copia e cola) | EEE | $$ | ♥♥♥ |
| Pix: limite noturno | E | $$ | ♥ |
| Comprovantes | E | $$ | ♥♥♥ |
| Caixinhas | EE | $$ | ♥♥♥ |
| Cartão virtual com CVV dinâmico | EE | $$ | ♥♥♥ |
| Backoffice do gerente e auditoria | EEE | $$$ | ♥♥ |
| Terminal da agência | EE | $ | ♥♥ |
| PostgreSQL, Flyway e Docker | EE | $$ | ♥ |
| Notificações por e-mail | EE | $$ | ♥♥ |
| Autenticação em dois fatores | EEE | $$$ | ♥ |

## 7. Sequenciamento em ondas

| Onda | Funcionalidades | Situação |
|---|---|---|
| 1 | Contas, depósito, saque, extrato, erros padronizados | Entregue |
| 2 | Transferência atômica, site responsivo, dados de demonstração | Entregue |
| 3 | Terminal, atalho de um clique, CI | Entregue |
| 4 | Encerramento, filtro do extrato, CSV | Entregue |
| 5 | Login com perfis, cheque especial com juros, backoffice e auditoria | Entregue |
| 6 | Pix completo (chaves, BR Code, limites, comprovantes), caixinhas, cartão virtual | Entregue |
| 7 | PostgreSQL com Flyway, Docker Compose, OpenAPI, novo visual | Entregue |
| 8 | Conta poupança (herança de contas, rendimento no aniversário, abertura pelo app) | Entregue |
| 9 | Notificações por e-mail, autenticação em dois fatores, limites de Pix ajustáveis pelo cliente | Próxima |

## 8. MVP

**Hipótese:** um banco digital com regras de dinheiro no servidor, Pix no padrão do Banco Central e papéis bem separados (cliente, caixa e gerente) atende o dia a dia de uma cooperativa pequena.

**Ondas 1 a 6.** Validado quando:
- nenhuma operação resulta em saldo além do limite ou em diferença de centavos (garantido por testes, inclusive concorrentes);
- um cliente não consegue ver nem movimentar a conta de outro (testado na API);
- um BR Code gerado pelo Cofre é idêntico ao exemplo da documentação oficial do Pix.
