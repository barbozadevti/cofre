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
| 9 a 12 | Segunda rodada: Copiloto e Escudo de juros, antifraude explicável, Traga seu salário, visão executiva (detalhes abaixo) | Entregue |
| 13 | Empréstimo pessoal com CET e tabela Price | Próxima |

## 8. MVP

**Hipótese:** um banco digital com regras de dinheiro no servidor, Pix no padrão do Banco Central e papéis bem separados (cliente, caixa e gerente) atende o dia a dia de uma cooperativa pequena.

**Ondas 1 a 6.** Validado quando:
- nenhuma operação resulta em saldo além do limite ou em diferença de centavos (garantido por testes, inclusive concorrentes);
- um cliente não consegue ver nem movimentar a conta de outro (testado na API);
- um BR Code gerado pelo Cofre é idêntico ao exemplo da documentação oficial do Pix.


---

# Segunda rodada (setembro de 2026): o que um CEO de banco olharia

A primeira rodada construiu o núcleo bancário. A segunda parte de outra pergunta: **o que faria a diretoria de um banco
prestar atenção neste produto?** As respostas viraram quatro ondas, escolhidas por valor de negócio e não só por técnica.

## 9. Visão da segunda rodada

**Para** a diretoria de um banco digital que precisa crescer sem aumentar perda por fraude nem perder clientes para a concorrência,
**cujo** problema é que o cliente usa o banco como conta secundária, paga juros de cheque especial tendo dinheiro guardado e cai em golpes de Pix,
**o Cofre** passa a ser o banco que **avisa antes do aperto e protege o cliente do próprio produto mais caro**,
**que** prevê o saldo, cobre o negativo com a poupança, explica por que um Pix parece golpe e traz o salário para dentro,
**diferente de** bancos que lucram com o descuido do cliente,
**o nosso produto** troca uma receita pequena de juros por retenção, confiança e salário na conta (que é o que vira conta principal).

## 10. Personas novas

### Ricardo, CEO (52 anos)
- **Comportamento:** olha os números na segunda-feira de manhã, em 5 minutos, antes da reunião de diretoria.
- **Necessidades:** custódia, resultado de juros, risco de crédito, perdas por fraude e concentração da carteira, com uma leitura em texto do que mudou.

### Beatriz, cliente que trouxe o salário (29 anos)
- **Comportamento:** recebia no banco da empresa e só usava o Cofre para Pix.
- **Necessidades:** mudar o salário sem falar com o RH e guardar uma parte antes de gastar.

### Ana, cliente alvo de golpe (38 anos)
- **Comportamento:** faz Pix pequenos e frequentes; recebe a mensagem de um "parente com número novo".
- **Necessidades:** um aviso claro, no momento certo, que não a trate como suspeita.

## 11. Jornadas novas

**Mario não paga juros tendo dinheiro guardado (Copiloto + Escudo)**
1. O Copiloto aprende a rotina de Mario: salário no dia 5, aluguel no dia 6, compras no dia 9.
2. Uma parcela do seguro chega antes do salário e deixaria a conta negativa.
3. Com o Escudo ligado, a poupança cobre na hora. No fim do mês, o Copiloto mostra quanto ele deixou de pagar de juros.

**Ana desiste de um golpe (antifraude explicável)**
1. Ana tenta mandar R$ 990,00 à noite para alguém que nunca pagou; a média dos Pix dela é R$ 223,00.
2. O Cofre não envia e mostra a nota 80, com os três motivos e uma dica sobre golpes.
3. Ana desiste. Na visão executiva, a desistência conta como golpe evitado.

**Beatriz traz o salário (portabilidade + Pague-se primeiro)**
1. Informa o banco de origem, a empresa, o CNPJ e o salário. Acompanha a linha do tempo do pedido.
2. Em 3 dias, a portabilidade é concluída; no dia 5, o salário cai no Cofre.
3. Assim que cai, 10% vão para a poupança, antes de qualquer gasto.

**Ricardo prepara a reunião de diretoria (visão executiva)**
1. Entra com o perfil Diretoria e lê a "Leitura do período".
2. Vê que a margem de juros está negativa porque a carteira de crédito é pequena: a próxima onda é empréstimo pessoal.
3. Vê o custo do Escudo (juros renunciados) ao lado dos golpes evitados e dos salários trazidos.

## 12. Funcionalidades da segunda rodada

| Funcionalidade | Esforço | Valor de negócio | Valor de UX | Por que o CEO se importa |
|---|---|---|---|---|
| Previsão de saldo para 30 dias (Copiloto) | EE | $$ | ♥♥♥ | Engajamento: o cliente abre o app para planejar, não só para pagar |
| Escudo de juros (poupança cobre o negativo) | EE | $$$ | ♥♥♥ | Retenção e confiança; diferencial de marca |
| Antifraude explicável no Pix | EE | $$$ | ♥♥ | Perda por fraude é custo direto e risco de imagem |
| Portabilidade de salário | EE | $$$ | ♥♥ | Salário na conta transforma conta secundária em principal |
| Pague-se primeiro | E | $$ | ♥♥♥ | Aumenta a captação em poupança |
| Visão executiva (perfil Diretoria) | EE | $$$ | ♥ | Decisão rápida com os números certos |
| Empréstimo pessoal com CET e tabela Price | EEE | $$$ | ♥♥ | Principal alavanca de receita (a margem atual é negativa) |
| Investimentos (CDB com liquidez diária) | EEE | $$ | ♥♥ | Captação de prazo mais longo |
| Pix agendado e Pix Automático | EE | $$ | ♥♥ | Contas recorrentes no banco aumentam o uso |
| Autenticação em dois fatores e notificações | EEE | $$$ | ♥ | Segurança exigida em produção |

## 13. Sequenciamento da segunda rodada

| Onda | Funcionalidades | Situação |
|---|---|---|
| 9 | Copiloto (previsão de 30 dias) e Escudo de juros | Entregue |
| 10 | Antifraude explicável no Pix (nota de 0 a 100, retenção auditada, confirmação reforçada) | Entregue |
| 11 | Visão executiva com perfil Diretoria | Entregue |
| 12 | Traga seu salário: portabilidade e Pague-se primeiro | Entregue |
| 13 | Empréstimo pessoal com CET e tabela Price | Próxima |
| 14 | Investimentos, Pix agendado e Pix Automático | Futuro |
| 15 | Dois fatores, notificações e limites de Pix ajustáveis | Futuro |

## 14. MVP da segunda rodada

**Hipótese:** um banco que avisa antes do aperto, protege o cliente dos juros e de golpes e facilita trazer o salário
retém mais clientes e vira conta principal, mesmo abrindo mão de parte da receita de cheque especial.

**Ondas 9 a 12.** Validado quando:
- o Escudo nunca cobre com dinheiro da conta que acabou de receber (guardar na poupança usando o limite não é desfeito) e cobre só o que falta;
- o Copiloto acerta as datas e os saldos de uma rotina calculada à mão (teste com números fixos);
- um Pix com nota alta nunca sai sem confirmação, e a retenção fica auditada mesmo sem envio;
- a visão executiva fecha: custódia = corrente + poupança + caixinhas, margem = juros − rendimento.

**Métricas para acompanhar (o que a visão executiva já mostra):** golpes evitados, juros renunciados pelo Escudo,
salários trazidos (folha mensal), custódia em poupança e concentração dos 5 maiores clientes.
