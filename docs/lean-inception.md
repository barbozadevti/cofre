# Lean Inception: Cofre

> Decisões de produto do Cofre no formato Lean Inception (Paulo Caroli):
> visão → escopo → personas → jornadas → funcionalidades → sequenciamento → MVP.

---

## 1. Visão do produto

**Para** pequenas cooperativas, fintechs em fase de protótipo e quem estuda sistemas bancários
**cujo** problema é ter um núcleo de conta corrente confiável (saldo que nunca fica errado, extrato que explica cada centavo) sem montar um core bancário inteiro,
**o Cofre** é um banco digital com API REST, site e terminal
**que** abre contas, movimenta dinheiro (depósito, saque, transferência) e mostra o extrato com o saldo após cada operação.
**Diferente de** exemplos didáticos que usam `double` e deixam o saldo ficar negativo,
**o nosso produto** usa valores decimais exatos, regras de negócio no domínio, transações atômicas e testes automatizados em todas as camadas.

## 2. O produto É / NÃO É / FAZ / NÃO FAZ

| É | NÃO É |
|---|---|
| Um núcleo de conta corrente com API REST | Um banco real ou um sistema homologado pelo Banco Central |
| Um site para operar as contas | Um aplicativo de celular nativo |
| Um terminal (console) para operar as mesmas contas | Um sistema com login de clientes (ainda) |

| FAZ | NÃO FAZ |
|---|---|
| Abre conta com agência, número e dígito verificador | Integração com PIX, TED ou boletos reais |
| Deposita, saca e transfere com validação de saldo | Cheque especial, juros ou tarifas (ainda) |
| Mostra extrato com saldo após cada lançamento | Investimentos ou cartões |
| Mantém site e terminal sobre os mesmos dados | Guarda dados sensíveis como senha ou CPF |

## 3. Objetivos do produto

1. **Saldo sempre correto:** nenhuma operação deixa o saldo negativo ou perde centavos (`BigDecimal`, transação única por operação, controle de concorrência otimista).
2. **Extrato que explica:** cada lançamento mostra tipo, valor, contraparte e saldo resultante.
3. **Mesmo núcleo, vários canais:** site, API e terminal usam o mesmo serviço de domínio.
4. **Fácil de avaliar:** sobe com um clique, já com contas de demonstração, sem instalar banco de dados.

## 4. Personas

### Carla, gerente de uma cooperativa de crédito (41 anos)
- **Comportamento:** atende no balcão, abre contas e registra depósitos em dinheiro.
- **Necessidades:** uma tela simples, com o saldo sempre visível e mensagens claras quando algo não pode ser feito.

### Diego, desenvolvedor de uma fintech (29 anos)
- **Comportamento:** integra sistemas por API e lê a documentação antes do código.
- **Necessidades:** endpoints previsíveis, erros no padrão `application/problem+json` e regras de saldo no servidor.

### Seu Antônio, caixa da cooperativa (58 anos)
- **Comportamento:** usa o terminal da agência, que é rápido e não depende de navegador.
- **Necessidades:** um menu numerado, que peça um dado por vez e não trave quando ele digita errado.

## 5. Jornadas

**Carla abre uma conta**
1. Clica em "Abrir conta", informa o nome do cliente, a agência e o depósito inicial.
2. O Cofre gera o número com dígito verificador e mostra "Olá Mario Andrade, obrigado por criar uma conta...".
3. A conta aparece na lista, já com o lançamento de abertura no extrato.

**Diego integra um saque**
1. Chama `POST /api/contas/{numero}/saques` com o valor.
2. Sem saldo, recebe `422` com `"Saldo insuficiente: disponível R$ 80,00, solicitado R$ 100,00"`.
3. Com saldo, recebe a conta atualizada e o lançamento aparece no extrato.

**Seu Antônio registra uma transferência pelo terminal**
1. Escolhe a opção 4 no menu, digita a conta de origem, a de destino e o valor.
2. Digita "abc" no valor por engano; o terminal explica e pede de novo.
3. Confirma e vê os dois saldos atualizados.

## 6. Funcionalidades e revisão técnica

| Funcionalidade | Esforço | Valor de negócio | Valor de UX |
|---|---|---|---|
| Abrir conta (agência, número com DV, saldo inicial) | E | $$$ | ♥♥♥ |
| Depositar | E | $$$ | ♥♥ |
| Sacar com validação de saldo | E | $$$ | ♥♥ |
| Transferir entre contas (atômico) | EE | $$$ | ♥♥ |
| Extrato com saldo após cada lançamento | EE | $$$ | ♥♥♥ |
| Erros padronizados (problem+json) em português | E | $$ | ♥♥ |
| Site responsivo (claro e escuro) | EE | $$ | ♥♥♥ |
| Modo terminal com menu | EE | $$ | ♥♥ |
| Contas de demonstração na primeira execução | E | $ | ♥♥♥ |
| Encerrar conta (só com saldo zero) | E | $$ | ♥ |
| Filtro do extrato por período | E | $$ | ♥♥ |
| Exportar extrato em CSV | E | $ | ♥♥ |
| Limite de cheque especial | EE | $$ | ♥ |
| Login e perfis (gerente, caixa, cliente) | EEE | $$$ | ♥♥ |
| PIX simulado (chaves) | EEE | $$ | ♥♥♥ |

## 7. Sequenciamento em ondas

| Onda | Funcionalidades | Situação |
|---|---|---|
| 1 | Abrir conta, depositar, sacar, extrato, API com erros padronizados | Entregue |
| 2 | Transferência atômica, site responsivo, contas de demonstração | Entregue |
| 3 | Modo terminal, atalho de um clique, CI | Entregue |
| 4 | Encerrar conta, filtro do extrato por período, exportar CSV | Entregue |
| 5 | Limite de cheque especial, login e perfis | Próxima |
| 6 | PIX simulado com chaves | Futuro |

## 8. MVP

**Hipótese:** um núcleo de conta corrente com regras de saldo no servidor, extrato explicativo e dois canais (site e terminal) é suficiente para uma cooperativa pequena registrar o dia a dia do balcão.

**Ondas 1 a 3.** Validado quando:
- nenhuma operação resulta em saldo negativo ou em diferença de centavos (garantido por testes);
- uma transferência que falha não altera nenhuma das duas contas;
- a mesma conta aparece igual no site e no terminal.
