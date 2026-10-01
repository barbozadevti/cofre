<p align="center">
  <img src="assets/cofre.png" alt="" width="84">
</p>

<h1 align="center">Cofre</h1>

<p align="center">
  <b>O banco que avisa antes do aperto e protege o cliente dos juros e dos golpes.</b><br>
  Banco digital em Java 21 + Spring Boot 4 com orientação a objetos: corrente e poupança, Pix com QR Code (BR Code),<br>
  Copiloto financeiro, Escudo de juros, antifraude explicável, portabilidade de salário e uma visão executiva para a diretoria.
</p>

<p align="center">
  <a href="https://github.com/barbozadevti/cofre/actions/workflows/ci.yml"><img src="https://github.com/barbozadevti/cofre/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-21-d9b56d" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1-d9b56d" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/testes-144-d9b56d" alt="144 testes">
  <img src="https://img.shields.io/badge/PostgreSQL%20%7C%20H2-Flyway-d9b56d" alt="PostgreSQL e H2 com Flyway">
</p>

![Copiloto financeiro e Escudo de juros na tela inicial do cliente](docs/telas/02-inicio.png)

## Experimente em 1 minuto

```bash
docker compose up --build
```

Abra **http://localhost:5230** e use os botões de demonstração da tela de login (senha de todos: `Cofre@2026`):

| Perfil | Login | O que ver |
|---|---|---|
| Cliente | `mario@cofre.dev` | Pix por chave, QR Code, copia e cola, caixinhas, cartão virtual |
| Cliente no limite | `joao@cofre.dev` | Cheque especial em uso e juros cobrados por dia |
| Caixa | `caixa@cofre.dev` | Depósito e saque em espécie no balcão |
| Gerente | `gerente@cofre.dev` | Indicadores, abertura de conta, limites, bloqueios e auditoria |
| Salário portado | `beatriz@cofre.dev` | Portabilidade de salário concluída e Pague-se primeiro |
| **Diretoria (CEO)** | `diretoria@cofre.dev` | Visão executiva: custódia, margem, crédito, fraude, Escudo e concentração |

Sem Docker: `mvn spring-boot:run` (JDK 21 + Maven; o banco H2 é criado em `~/.cofre`). No Windows, o atalho `Abrir Cofre.cmd` faz tudo e abre o navegador. A documentação interativa da API fica em **/swagger-ui.html**.

## O diferencial: um banco do lado do cliente

Bancos costumam ganhar dinheiro com o descuido do cliente: o cheque especial de 8% ao mês cobrado de quem tem dinheiro
parado na poupança, o golpe que só é percebido depois. O Cofre faz o contrário, e mostra para a diretoria quanto isso custa e quanto rende.

| | O que faz | Como funciona |
|---|---|---|
| **Copiloto** | Prevê o saldo dos próximos 30 dias e avisa *antes* de a conta ficar negativa | Reconhece o que se repete todo mês (salário, aluguel, escola) nos últimos 3 meses e projeta. É uma função pura, testada com números calculados à mão |
| **Escudo de juros** | Se a corrente ficaria negativa, a poupança do cliente cobre na hora, sem juros | Roda na mesma transação do Pix, saque ou transferência, e de novo na rotina diária antes dos juros. Mostra a economia: 8% de juros evitados − 0,5% que a poupança deixou de render |
| **Antifraude explicável** | Pix com cara de golpe não sai sem confirmação | Nota de 0 a 100 com os motivos: destinatário novo, valor 3× acima da média, madrugada, conta de destino recém-aberta, rajada de envios, Pix que esvazia a conta. Responde HTTP 428; a retenção fica na auditoria |
| **Traga seu salário** | Portabilidade de salário com linha do tempo | Pedido simulado, andamento calculado pelo relógio (sem rotina para "mudar status") e salário creditado no dia do pagamento, de forma idempotente |
| **Pague-se primeiro** | Uma porcentagem de cada salário vai para a poupança assim que cai | Vale para o salário portado e para Pix com "salário" na mensagem |
| **Visão executiva** | O banco inteiro em uma tela, para a diretoria | Perfil próprio (só leitura): custódia, margem de juros, carteira de crédito, Pix, golpes evitados, custo do Escudo, salários trazidos, concentração e uma leitura em texto |

Uma nota honesta: bancos nos Estados Unidos oferecem transferência automática da poupança para cobrir saldo negativo,
geralmente com tarifa. O diferencial do Cofre é a combinação: **prever antes, proteger de graça e mostrar ao cliente quanto ele economizou**.

<table>
  <tr>
    <td><img src="docs/telas/18-copiloto.png" alt="Copiloto: previsão de saldo e Escudo de juros"></td>
    <td><img src="docs/telas/19-antifraude.png" alt="Antifraude: nota de risco 80 com os motivos"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/20-salario.png" alt="Traga seu salário: portabilidade concluída e Pague-se primeiro"></td>
    <td><img src="docs/telas/21-salario-em-analise.png" alt="Portabilidade em análise com linha do tempo"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/22-diretoria.png" alt="Visão executiva da diretoria"></td>
    <td><img src="docs/telas/23-diretoria-riscos.png" alt="Visão executiva: crédito, antifraude, Escudo e concentração"></td>
  </tr>
</table>

<p align="center"><img src="docs/telas/24-celular-copiloto.png" alt="Copiloto no celular" width="260"></p>

## O que tem dentro

### App do cliente
- **Pix** por CPF, e-mail, celular ou chave aleatória, com **confirmação do destinatário** (nome e CPF mascarado) antes de enviar.
- **Cobrança com QR Code** no padrão **BR Code (EMV) do Banco Central**, com CRC16, e pagamento por **copia e cola**.
- **Limites do Pix por período**: R$ 1.000,00 das 20h às 6h, como na regra do Banco Central, e R$ 20.000,00 de dia.
- **Comprovantes** com autenticação no formato *end-to-end ID* do Pix (32 caracteres), prontos para imprimir ou salvar em PDF.
- **Cheque especial**: o saldo pode ficar negativo até o limite, com **juros de 8% ao mês cobrados por dia** por uma rotina agendada e idempotente.
- **Conta poupança** aberta pelo próprio app: rende **0,5% ao mês no aniversário** (o dia da abertura; contas abertas de 29 a 31 rendem no dia 1, como na regra real), não tem cheque especial e tem atalhos para **guardar e resgatar**.
- **Caixinhas** com meta e progresso. Só dinheiro próprio vai para a caixinha, nunca o limite.
- **Cartão virtual** com número válido pelo algoritmo de Luhn e **CVV dinâmico** que muda a cada 5 minutos (HMAC-SHA256; o CVV não é guardado em lugar nenhum).
- **Extrato** por período, com saldo anterior, totais e **exportação CSV** pronta para o Excel.
- **Modo privacidade** (ícone do olho) para esconder os valores na tela.

### Agência
- **Caixa**: busca por nome, CPF ou conta; depósito e saque em espécie.
- **Gerente**: painel de indicadores (custódia, limite concedido e em uso, volume de Pix do dia), abertura de conta com **senha provisória** (troca obrigatória no primeiro acesso), limite do cheque especial, bloqueio com motivo, encerramento, redefinição de senha e **auditoria** de todas as ações.
- **Terminal da agência**: `java -jar cofre.jar --terminal`, com login de caixa ou gerente. É a evolução do desafio original ([java-conta-terminal](https://github.com/barbozadevti/java-conta-terminal)) e mantém as mesmas perguntas na abertura de conta.

<table>
  <tr>
    <td><img src="docs/telas/16-poupanca.png" alt="Conta poupança com rendimento previsto"></td>
    <td><img src="docs/telas/17-extrato-poupanca.png" alt="Extrato da poupança com o rendimento do mês"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/03-pix-destinatario.png" alt="Pix com confirmação do destinatário"></td>
    <td><img src="docs/telas/04-pix-qrcode.png" alt="Cobrança com QR Code"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/05-comprovante.png" alt="Comprovante"></td>
    <td><img src="docs/telas/06-cartao.png" alt="Cartão virtual com CVV dinâmico"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/08-cheque-especial.png" alt="Cheque especial com juros"></td>
    <td><img src="docs/telas/07-caixinhas.png" alt="Caixinhas"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/09-gerente-painel.png" alt="Painel do gerente"></td>
    <td><img src="docs/telas/10-gerente-conta.png" alt="Gestão de conta pelo gerente"></td>
  </tr>
  <tr>
    <td><img src="docs/telas/12-balcao.png" alt="Balcão do caixa"></td>
    <td><img src="docs/telas/11-auditoria.png" alt="Auditoria"></td>
  </tr>
</table>

<p align="center">
  <img src="docs/telas/13-celular-inicio.png" alt="Celular: início" width="260">
  <img src="docs/telas/14-celular-pix.png" alt="Celular: Pix" width="260">
  <img src="docs/telas/01-login.png" alt="Login" width="420">
</p>

## Orientação a objetos: corrente e poupança

O Cofre nasceu de dois desafios de Java: a [conta no terminal](https://github.com/barbozadevti/java-conta-terminal) e o [banco digital com os pilares da orientação a objetos](https://github.com/barbozadevti/java-banco-digital-poo). Do segundo vieram os dois tipos de conta, agora com JPA:

```mermaid
classDiagram
    direction LR
    class Conta {
        <<abstract>>
        -saldo: BigDecimal
        -situacao: SituacaoConta
        +getTipo()* TipoConta
        +getLimite()* BigDecimal
        +disponivel() BigDecimal
        +creditar(...) Lancamento
        +debitar(...) Lancamento
        +encerrar(quando) void
        #aoEncerrar() void
    }
    class ContaCorrente {
        -limite: BigDecimal
        +definirLimite(valor) void
    }
    class ContaPoupanca {
        +diaDeAniversario(fuso) int
        +fazAniversario(dia, fuso) boolean
        +render(id, quando) Optional~Lancamento~
    }
    Conta <|-- ContaCorrente
    Conta <|-- ContaPoupanca
    Conta *-- Lancamento
```

| Pilar | No código |
|---|---|
| **Abstração** | `Conta` é abstrata e diz o que toda conta tem (saldo, situação, extrato); não existe "conta genérica". |
| **Encapsulamento** | O saldo só muda por `creditar` e `debitar`, que validam a operação e devolvem o lançamento do extrato. |
| **Herança** | `ContaCorrente` e `ContaPoupanca` herdam numeração, extrato, bloqueio e encerramento. No banco, as duas ficam na mesma tabela, diferenciadas pela coluna `tipo` (herança JPA em tabela única, migração `V2`). |
| **Polimorfismo** | `getLimite()` é o cheque especial na corrente e zero na poupança. Por isso `disponivel()`, Pix, saque e transferência funcionam nas duas sem nenhum `if` de tipo. O encerramento chama o gancho `aoEncerrar()`, que zera o limite só na corrente. |

Onde o tipo importa de verdade (limite do cheque especial, cartão de débito, campos da poupança na API), o código usa *pattern matching* do Java 21 (`conta instanceof ContaCorrente corrente`). O rendimento roda na mesma rotina diária dos juros e é idempotente: o id `REND-<conta>-<ano><mês>` impede pagar duas vezes no mesmo mês.

## Decisões de engenharia

| Problema | Decisão |
|---|---|
| Centavos que somem | `BigDecimal` com 2 casas em todo o domínio. Um teste confere que 10 depósitos de R$ 0,10 dão exatamente R$ 1,00. |
| Saldo e extrato divergindo | Só a entidade `Conta` altera o saldo, e cada alteração devolve o `Lancamento` correspondente, com o saldo após a operação. |
| Dois saques ao mesmo tempo | Controle de concorrência otimista (`@Version`). Um teste dispara 20 saques simultâneos e confere que o saldo nunca passa do limite nem perde lançamentos. |
| Clique duplo no "Enviar Pix" | Cabeçalho **`Idempotency-Key`**: a mesma chave devolve o comprovante da primeira operação, garantido por uma restrição única no banco. |
| Transferência pela metade | Débito e crédito na mesma transação: ou as duas contas mudam, ou nenhuma. |
| Descobrir contas de outros clientes | Conta alheia responde **404** (e não 403), para não revelar que o número existe. |
| Regras de acesso espalhadas | O perfil é checado nos serviços, e não só nas rotas, então vale igual no site, na API e no terminal. |
| Dois tipos de conta sem duplicar código | `Conta` abstrata com herança JPA em tabela única; o comportamento que muda (limite, disponível, encerramento) fica em métodos sobrescritos. |
| Juros cobrados duas vezes | O id da transação de juros é derivado da conta e do dia (`JUROS-10004-8-20260926`): rodar a rotina de novo não duplica. |
| Senha fraca ou vazada | BCrypt, política de senha, **bloqueio de 15 minutos após 5 erros**, mensagem única para "usuário não existe" e "senha errada" (com o mesmo tempo de resposta) e troca obrigatória da senha provisória. |
| Sessão ou JWT? | **Sessão com cookie HttpOnly + SameSite e CSRF no padrão SPA.** Para um app servido pelo próprio backend, é mais seguro que guardar JWT no navegador e permite encerrar a sessão no servidor. |
| XSS e clickjacking | Content Security Policy sem scripts nem estilos inline, `X-Frame-Options: DENY`, `Cache-Control: no-store` nas respostas da API. |
| QR Code de demonstração lido por um banco real | Os dados de exemplo usam só chaves de e-mail e aleatórias, e a tela avisa para pagar com outra conta do Cofre. |

## Arquitetura

```mermaid
flowchart LR
  subgraph Canais
    WEB[Site<br>JS sem framework]
    API[API REST<br>OpenAPI]
    TERM[Terminal<br>da agência]
  end
  subgraph Spring Boot
    SEG[Spring Security<br>sessão + CSRF + perfis]
    SRV[Serviços<br>Conta · Pix · Caixinha · Cartão · Gerência]
    DOM[Domínio<br>Conta · Lançamento · Dinheiro · BR Code]
    AUD[Auditoria]
    JOB[Juros e rendimentos<br>@Scheduled]
  end
  DB[(PostgreSQL ou H2<br>migrações Flyway)]
  WEB --> SEG --> SRV
  API --> SEG
  TERM --> SRV
  SRV --> DOM
  SRV --> AUD
  JOB --> SRV
  SRV --> DB
```

```
src/main/java/dev/barboza/cofre/
├── dominio/     Conta (abstrata), ContaCorrente, ContaPoupanca, Lancamento, Cliente, Dinheiro, Cpf, NumeroConta
├── servico/     ContaService, GerenciaService, PainelService, JurosChequeEspecial, RendimentoPoupanca, Acesso
├── copiloto/    PrevisaoDeSaldo (função pura) e CopilotoService (Escudo de juros)
├── salario/     Portabilidade de salário, CNPJ, Pague-se primeiro
├── diretoria/   Visão executiva
├── pix/         PixService, RiscoPix (antifraude), ChavePix, BrCode (EMV + CRC16), QrCodeSvg
├── caixinha/    Caixinha e serviço
├── cartao/      Cartão virtual, Luhn e CVV dinâmico
├── seguranca/   Usuário, perfis, login com bloqueio, Spring Security
├── auditoria/   Registro de eventos
├── api/         Controllers REST, DTOs, CSV e erros em problem+json
├── terminal/    Terminal da agência
└── config/      Dados de demonstração, agendador, OpenAPI
src/main/resources/
├── db/migration/   Migrações Flyway
└── static/         Site (HTML, CSS e módulos JavaScript)
```

## Testes

```bash
mvn verify
```

**144 testes**, rodando com o idioma da máquina em português para pegar qualquer formatação que dependa dele. No CI, a mesma bateria roda também contra o **PostgreSQL**, e um terceiro job sobe o **Docker Compose** e faz login de verdade.

| Área | Exemplos do que é verificado |
|---|---|
| Domínio | Cheque especial, bloqueio, encerramento, juros, CPF, celular, dígito da conta, formato do id de transação |
| Poupança | Disponível sem limite, aniversário (inclusive 29 a 31 virando dia 1), rendimento com arredondamento bancário, crédito idempotente, uma poupança por cliente, sem limite e sem cartão |
| BR Code | Gera **exatamente** o exemplo da documentação do Pix (CRC `1D3D`), lê, e recusa código alterado |
| Pix | Chaves (tipos, limite de 5, duplicidade), consulta com CPF mascarado, limite noturno com relógio controlado, copia e cola |
| Segurança | 401 sem login, 403 sem CSRF, cada perfil só na sua área, 404 para conta alheia, bloqueio após 5 senhas erradas, trava da senha provisória |
| API | Login real com sessão, idempotência do Pix, `problem+json`, CSV byte a byte |
| Concorrência | 20 saques simultâneos na mesma conta |
| Terminal | Roteiros de caixa e gerente, abertura com as perguntas do desafio original |
| Copiloto e Escudo | Recorrências reconhecidas só quando se repetem no mesmo período do mês, dia 31 em mês de 30 dias, cobertura na hora, cobertura parcial, nunca desfaz o "guardar", rotina diária |
| Antifraude | Cada fator da nota com o peso certo, Pix retido não debita e fica auditado, confirmado sai |
| Salário | CNPJ, andamento pelo tempo, cancelamento, crédito no dia certo uma vez por mês, Pague-se primeiro |
| Visão executiva | Os números fecham (custódia, margem) e só a diretoria vê |
| Demonstração | Seis meses de histórico, poupanças rendendo, Escudo em ação, salário portado e um cliente no cheque especial com juros |

Os valores esperados de CPF, dígito verificador e BR Code vêm de fontes independentes (documentação do Banco Central e uma implementação separada), não do próprio código testado.

## API

51 rotas, documentadas em `/swagger-ui.html`. Os erros seguem a RFC 9457 (`application/problem+json`), em português:

| Situação | Status |
|---|---|
| Sem login | 401 |
| Perfil sem permissão ou sem token CSRF | 403 |
| Conta, chave, caixinha ou comprovante inexistente (ou de outro cliente) | 404 |
| Duas operações simultâneas na mesma conta | 409 |
| Acesso bloqueado por tentativas | 423 |
| Regra de negócio (saldo, limite do Pix, conta bloqueada...) | 422 |
| Pix com nota de risco alta: precisa de confirmação (`X-Confirmacao-Risco: confirmo`) | 428 |

![Swagger](docs/telas/15-swagger.png)

## Produto

As decisões de escopo e a ordem das entregas estão na [Lean Inception](docs/lean-inception.md). A segunda rodada (ondas 9 a 12: Copiloto, antifraude, visão executiva e salário) partiu da pergunta "o que um CEO de banco olharia?". A próxima onda é empréstimo pessoal com CET e tabela Price, a alavanca de receita que a própria visão executiva aponta.
