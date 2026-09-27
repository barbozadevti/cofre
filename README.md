<p align="center">
  <img src="assets/cofre.png" alt="" width="84">
</p>

<h1 align="center">Cofre</h1>

<p align="center">
  <b>Banco digital completo em Java 21 + Spring Boot 4</b><br>
  Pix com QR Code (BR Code), cheque especial com juros diários, caixinhas, cartão virtual com CVV dinâmico<br>
  e um backoffice com perfis de <b>cliente</b>, <b>caixa</b> e <b>gerente</b>.
</p>

<p align="center">
  <a href="https://github.com/barbozadevti/cofre/actions/workflows/ci.yml"><img src="https://github.com/barbozadevti/cofre/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/Java-21-d9b56d" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-4.1-d9b56d" alt="Spring Boot 4.1">
  <img src="https://img.shields.io/badge/testes-94-d9b56d" alt="94 testes">
  <img src="https://img.shields.io/badge/PostgreSQL%20%7C%20H2-Flyway-d9b56d" alt="PostgreSQL e H2 com Flyway">
</p>

![Tela inicial do cliente](docs/telas/02-inicio.png)

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

Sem Docker: `mvn spring-boot:run` (JDK 21 + Maven; o banco H2 é criado em `~/.cofre`). No Windows, o atalho `Abrir Cofre.cmd` faz tudo e abre o navegador. A documentação interativa da API fica em **/swagger-ui.html**.

## O que tem dentro

### App do cliente
- **Pix** por CPF, e-mail, celular ou chave aleatória, com **confirmação do destinatário** (nome e CPF mascarado) antes de enviar.
- **Cobrança com QR Code** no padrão **BR Code (EMV) do Banco Central**, com CRC16, e pagamento por **copia e cola**.
- **Limites do Pix por período**: R$ 1.000,00 das 20h às 6h, como na regra do Banco Central, e R$ 20.000,00 de dia.
- **Comprovantes** com autenticação no formato *end-to-end ID* do Pix (32 caracteres), prontos para imprimir ou salvar em PDF.
- **Cheque especial**: o saldo pode ficar negativo até o limite, com **juros de 8% ao mês cobrados por dia** por uma rotina agendada e idempotente.
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
    JOB[Juros diários<br>@Scheduled]
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
├── dominio/     Conta, Lancamento, Cliente, Dinheiro, Cpf, NumeroConta, IdTransacao
├── servico/     ContaService, GerenciaService, PainelService, JurosChequeEspecial, Acesso
├── pix/         PixService, ChavePix, BrCode (EMV + CRC16), QrCodeSvg
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

**94 testes**, rodando com o idioma da máquina em português para pegar qualquer formatação que dependa dele. No CI, a mesma bateria roda também contra o **PostgreSQL**, e um terceiro job sobe o **Docker Compose** e faz login de verdade.

| Área | Exemplos do que é verificado |
|---|---|
| Domínio | Cheque especial, bloqueio, encerramento, juros, CPF, celular, dígito da conta, formato do id de transação |
| BR Code | Gera **exatamente** o exemplo da documentação do Pix (CRC `1D3D`), lê, e recusa código alterado |
| Pix | Chaves (tipos, limite de 5, duplicidade), consulta com CPF mascarado, limite noturno com relógio controlado, copia e cola |
| Segurança | 401 sem login, 403 sem CSRF, cada perfil só na sua área, 404 para conta alheia, bloqueio após 5 senhas erradas, trava da senha provisória |
| API | Login real com sessão, idempotência do Pix, `problem+json`, CSV byte a byte |
| Concorrência | 20 saques simultâneos na mesma conta |
| Terminal | Roteiros de caixa e gerente, abertura com as perguntas do desafio original |
| Demonstração | Seis meses de histórico e um cliente no cheque especial com juros |

Os valores esperados de CPF, dígito verificador e BR Code vêm de fontes independentes (documentação do Banco Central e uma implementação separada), não do próprio código testado.

## API

39 rotas, documentadas em `/swagger-ui.html`. Os erros seguem a RFC 9457 (`application/problem+json`), em português:

| Situação | Status |
|---|---|
| Sem login | 401 |
| Perfil sem permissão ou sem token CSRF | 403 |
| Conta, chave, caixinha ou comprovante inexistente (ou de outro cliente) | 404 |
| Duas operações simultâneas na mesma conta | 409 |
| Acesso bloqueado por tentativas | 423 |
| Regra de negócio (saldo, limite do Pix, conta bloqueada...) | 422 |

![Swagger](docs/telas/15-swagger.png)

## Produto

As decisões de escopo e a ordem das entregas estão na [Lean Inception](docs/lean-inception.md). Das oito ondas planejadas, sete já foram entregues.
