package dev.barboza.cofre.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Random;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.barboza.cofre.caixinha.Caixinha;
import dev.barboza.cofre.caixinha.CaixinhaService;
import dev.barboza.cofre.dominio.Cliente;
import dev.barboza.cofre.dominio.ClienteRepository;
import dev.barboza.cofre.dominio.Conta;
import dev.barboza.cofre.dominio.ContaCorrente;
import dev.barboza.cofre.dominio.ContaRepository;
import dev.barboza.cofre.dominio.Cpf;
import dev.barboza.cofre.pix.PixService;
import dev.barboza.cofre.pix.TipoChavePix;
import dev.barboza.cofre.seguranca.Perfil;
import dev.barboza.cofre.seguranca.Usuario;
import dev.barboza.cofre.seguranca.UsuarioLogado;
import dev.barboza.cofre.seguranca.UsuarioRepository;
import dev.barboza.cofre.servico.ContaService;
import dev.barboza.cofre.servico.JurosChequeEspecial;
import dev.barboza.cofre.servico.RendimentoPoupanca;

/**
 * Na primeira execução (banco vazio), cria gerente, caixa e 6 clientes (um deles, um mercado) com 6 meses de movimentação:
 * salários, Pix, saques, caixinhas, duas poupanças rendendo todo mês e um cliente no cheque especial. Todos com a senha {@value #SENHA}.
 * Roda antes de o servidor web aceitar requisições, para o site nunca abrir vazio.
 */
@Component
@ConditionalOnProperty(name = "cofre.demo", havingValue = "true")
public class DadosDeDemonstracao implements SmartInitializingSingleton {

    public static final String SENHA = "Cofre@2026";

    private final ContaService contas;
    private final PixService pix;
    private final CaixinhaService caixinhas;
    private final JurosChequeEspecial juros;
    private final RendimentoPoupanca rendimento;
    private final ClienteRepository clientes;
    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final TransactionTemplate transacao;
    private final Clock relogio;

    private final Random sorteio = new Random(2026);
    private String hash;
    private LocalDate hoje;

    public DadosDeDemonstracao(ContaService contas, PixService pix, CaixinhaService caixinhas, JurosChequeEspecial juros,
            RendimentoPoupanca rendimento, ClienteRepository clientes, ContaRepository contaRepository, UsuarioRepository usuarios, PasswordEncoder senhas,
            TransactionTemplate transacao, Clock relogio) {
        this.contas = contas;
        this.pix = pix;
        this.caixinhas = caixinhas;
        this.juros = juros;
        this.rendimento = rendimento;
        this.clientes = clientes;
        this.contaRepository = contaRepository;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (usuarios.count() > 0) {
            return;
        }
        hash = senhas.encode(SENHA);
        hoje = LocalDate.now(relogio.withZone(ContaService.FUSO));
        criar();
    }

    private void criar() {
        Instant inicio = em(200, "09:00");
        usuarios.save(new Usuario("gerente@cofre.dev", "Carla Mendes", hash, Perfil.GERENTE, null, false, inicio));
        usuarios.save(new Usuario("caixa@cofre.dev", "Antônio Ribeiro", hash, Perfil.CAIXA, null, false, inicio));

        Conta helena = cliente("Helena Prado", "418273645", "helena@cofre.dev", "11991234567", "0412", "48000.00", "0", 199);
        Conta mario = cliente("Mario Andrade", "529982247", "mario@cofre.dev", "11987654321", "0678", "237.48", "2000.00", 198);
        Conta ana = cliente("Ana Souza", "731640582", "ana@cofre.dev", "21998765432", "0001", "3500.00", "1500.00", 197);
        Conta joao = cliente("João da Silva", "286405917", "joao@cofre.dev", "31987651234", "0001", "150.00", "1000.00", 196);
        Conta bia = cliente("Beatriz Lima", "613508249", "beatriz@cofre.dev", "41996543210", "0001", "900.00", "500.00", 195);
        Conta mercado = cliente("Mercado Bom Preço", "384019265", "mercado@cofre.dev", null, "0001", "5000.00", "0", 194);

        chave(helena, TipoChavePix.EMAIL, "helena@cofre.dev");
        chave(helena, TipoChavePix.ALEATORIA, null);
        // Só chaves de e-mail e aleatórias: CPF e celular de exemplo poderiam existir no Pix real.
        chave(mario, TipoChavePix.ALEATORIA, null);
        chave(mario, TipoChavePix.EMAIL, "mario@cofre.dev");
        chave(ana, TipoChavePix.EMAIL, "ana@cofre.dev");
        chave(ana, TipoChavePix.ALEATORIA, null);
        chave(joao, TipoChavePix.EMAIL, "joao@cofre.dev");
        chave(bia, TipoChavePix.EMAIL, "beatriz@cofre.dev");
        chave(mercado, TipoChavePix.EMAIL, "mercado@cofre.dev");

        // Poupanças abertas há 6 meses, no dia 3: rendem todo dia 3 e recebem uma parte do salário no dia 6.
        LocalDate aberturaPoupanca = hoje.minusMonths(6).withDayOfMonth(3);
        Conta poupancaAna = poupanca(ana, "2000.00", aberturaPoupanca);
        Conta poupancaHelena = poupanca(helena, "15000.00", aberturaPoupanca);
        Conta poupancaMario = poupanca(mario, "1500.00", aberturaPoupanca);
        // Mario liga o Escudo de juros junto com a poupança: quando a conta fica negativa, a poupança cobre.
        transacao.executeWithoutResult(status -> ((ContaCorrente) contaRepository.findByNumero(mario.getNumero()).orElseThrow())
                .ativarEscudo(aberturaPoupanca.atTime(11, 5).atZone(ContaService.FUSO).toInstant()));

        Caixinha viagem = caixinhas.criarEm(ana, "Viagem para o Chile", new BigDecimal("12000.00"), em(190, "20:00"));
        Caixinha reserva = caixinhas.criarEm(ana, "Reserva de emergência", null, em(190, "20:01"));
        Caixinha carro = caixinhas.criarEm(mario, "Carro novo", new BigDecimal("40000.00"), em(185, "21:30"));

        // Seis meses de rotina.
        for (int mesesAtras = 5; mesesAtras >= 0; mesesAtras--) {
            LocalDate mes = hoje.minusMonths(mesesAtras).withDayOfMonth(1);
            evento(mes, 3, "00:10", () -> rendimento.creditar(mes.withDayOfMonth(3), quando));
            evento(mes, 6, "09:00", () -> contas.transferirEm(ana.getNumero(), poupancaAna.getNumero(), valor(300, 500), "Guardar na poupança", quando));
            evento(mes, 6, "09:05", () -> contas.transferirEm(helena.getNumero(), poupancaHelena.getNumero(), new BigDecimal("1000.00"), "Reserva mensal", quando));
            evento(mes, 2, "10:15", () -> contas.depositarEm(helena.getNumero(), valor(9000, 12500), "Depósito de vendas", quando));
            evento(mes, 5, "08:00", () -> pix.enviarEm(helena.getNumero(), "mario@cofre.dev", new BigDecimal("5200.00"), "Salário", quando));
            evento(mes, 5, "08:30", () -> contas.transferirEm(mario.getNumero(), poupancaMario.getNumero(), new BigDecimal("400.00"), "Reserva", quando));
            evento(mes, 5, "08:01", () -> pix.enviarEm(helena.getNumero(), "joao@cofre.dev", new BigDecimal("3200.00"), "Salário", quando));
            evento(mes, 5, "08:02", () -> pix.enviarEm(helena.getNumero(), "ana@cofre.dev", valor(2400, 3600), "Projeto de design", quando));
            evento(mes, 6, "19:40", () -> pix.enviarEm(mario.getNumero(), "helena@cofre.dev", new BigDecimal("1850.00"), "Aluguel", quando));
            evento(mes, 7, "12:10", () -> pix.enviarEm(joao.getNumero(), "helena@cofre.dev", new BigDecimal("1500.00"), "Aluguel", quando));
            evento(mes, 8, "21:05", () -> caixinhas.guardarEm(viagem.getId(), valor(700, 1100), quando));
            evento(mes, 8, "21:06", () -> caixinhas.guardarEm(reserva.getId(), valor(300, 600), quando));
            evento(mes, 10, "18:22", () -> pix.enviarEm(mario.getNumero(), "beatriz@cofre.dev", valor(60, 180), "Bolo de aniversário", quando));
            evento(mes, 11, "09:30", () -> caixinhas.guardarEm(carro.getId(), valor(500, 900), quando));
            evento(mes, 12, "13:00", () -> contas.sacarEm(mario.getNumero(), valor(200, 400), "Saque no caixa", quando));
            evento(mes, 14, "20:45", () -> pix.enviarEm(ana.getNumero(), "mario@cofre.dev", valor(80, 140), "Pizza de sexta", quando));
            evento(mes, 15, "10:00", () -> pix.enviarEm(bia.getNumero(), "ana@cofre.dev", valor(250, 450), "Aulas de inglês", quando));
            evento(mes, 17, "16:20", () -> pix.enviarEm(joao.getNumero(), "beatriz@cofre.dev", valor(300, 700), "Conserto do carro", quando));
            evento(mes, 18, "11:11", () -> contas.depositarEm(bia.getNumero(), valor(1500, 2200), "Depósito no caixa", quando));
            evento(mes, 20, "19:00", () -> pix.enviarEm(mario.getNumero(), "ana@cofre.dev", valor(150, 260), "Presente da Carol", quando));
            evento(mes, 22, "14:35", () -> pix.enviarEm(joao.getNumero(), "mario@cofre.dev", valor(400, 800), "Parcela da moto", quando));
            evento(mes, 25, "09:45", () -> pix.enviarEm(ana.getNumero(), "helena@cofre.dev", valor(90, 200), "Material de escritório", quando));
            evento(mes, 9, "18:40", () -> pix.enviarEm(mario.getNumero(), "mercado@cofre.dev", valor(480, 820), "Compras do mês", quando));
            evento(mes, 23, "11:15", () -> pix.enviarEm(mario.getNumero(), "mercado@cofre.dev", valor(250, 480), "Feira", quando));
            evento(mes, 12, "19:05", () -> pix.enviarEm(ana.getNumero(), "mercado@cofre.dev", valor(300, 520), "Supermercado", quando));
            evento(mes, 13, "10:20", () -> pix.enviarEm(joao.getNumero(), "mercado@cofre.dev", valor(420, 650), "Supermercado", quando));
            evento(mes, 20, "17:50", () -> pix.enviarEm(bia.getNumero(), "mercado@cofre.dev", valor(180, 360), "Mercado", quando));
            evento(mes, 26, "08:30", () -> pix.enviarEm(mario.getNumero(), "beatriz@cofre.dev", valor(90, 160), "Aula de violão", quando));
            evento(mes, 27, "17:30", () -> contas.sacarEm(joao.getNumero(), valor(250, 500), "Saque no caixa", quando));
        }

        // Hoje cedo, o seguro do carro passa do saldo do Mario: o Escudo cobre a diferença com a poupança, sem juros.
        BigDecimal saldoMario = contaRepository.findByNumero(mario.getNumero()).orElseThrow().getSaldo();
        pix.enviarEm(mario.getNumero(), "helena@cofre.dev", saldoMario.max(BigDecimal.ZERO).add(new BigDecimal("380.00")),
                "Seguro do carro", em(0, "07:30"));

        // João termina usando o cheque especial, com juros cobrados nos últimos dias.
        BigDecimal saldoJoao = contaRepository.findByNumero(joao.getNumero()).orElseThrow().getSaldo();
        BigDecimal ateONegativo = saldoJoao.add(new BigDecimal("412.37"));
        if (ateONegativo.signum() > 0) {
            pix.enviarEm(joao.getNumero(), "beatriz@cofre.dev", ateONegativo, "Conserto da moto", em(3, "15:20"));
        }
        for (int dias = 3; dias >= 1; dias--) {
            juros.cobrar(hoje.minusDays(dias), em(dias - 1, "00:05"));
        }
    }

    private Conta cliente(String nome, String noveDigitos, String email, String telefone, String agencia,
            String deposito, String limite, int diasAtras) {
        Instant quando = em(diasAtras, "10:00");
        return transacao.execute(status -> {
            Cliente cliente = clientes.save(new Cliente(nome, Cpf.completar(noveDigitos), email, telefone, quando));
            usuarios.save(new Usuario(email, nome, hash, Perfil.CLIENTE, cliente, false, quando));
            ContaCorrente conta = contas.abrirPara(cliente, agencia, new BigDecimal(deposito), quando);
            conta.definirLimite(new BigDecimal(limite));
            return conta;
        });
    }

    private Conta poupanca(Conta corrente, String deposito, LocalDate dia) {
        Instant quando = dia.atTime(LocalTime.parse("11:00")).atZone(ContaService.FUSO).toInstant();
        return transacao.execute(status -> contas.abrirPoupancaPara(corrente.getCliente(), corrente.getAgencia(),
                new BigDecimal(deposito), quando));
    }

    private void chave(Conta conta, TipoChavePix tipo, String valor) {
        pix.cadastrarEm(conta, tipo, valor, conta.getAbertaEm(), UsuarioLogado.SISTEMA);
    }

    /** Executa o evento no dia do mês indicado, se ele já passou; falhas de saldo são ignoradas (é só demonstração). */
    private void evento(LocalDate mes, int dia, String hora, Evento acao) {
        LocalDate data = mes.withDayOfMonth(Math.min(dia, mes.lengthOfMonth()));
        if (!data.isBefore(hoje) || data.isBefore(hoje.minusDays(190))) {
            return;
        }
        quando = data.atTime(LocalTime.parse(hora)).atZone(ContaService.FUSO).toInstant();
        try {
            acao.executar();
        } catch (RuntimeException e) {
            // saldo insuficiente num mês apertado: o evento simplesmente não acontece
        }
    }

    private Instant quando;

    @FunctionalInterface
    private interface Evento {
        void executar();
    }

    private BigDecimal valor(int minimo, int maximo) {
        return BigDecimal.valueOf(minimo + sorteio.nextInt(maximo - minimo) + sorteio.nextInt(100) / 100.0)
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private Instant em(int diasAtras, String hora) {
        return hoje.minusDays(diasAtras).atTime(LocalTime.parse(hora)).atZone(ContaService.FUSO).toInstant();
    }
}
