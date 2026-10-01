package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    Optional<Conta> findByNumero(String numero);

    List<Conta> findAllByOrderByNumeroBaseAsc();

    List<Conta> findByClienteIdOrderByNumeroBaseAsc(Long clienteId);

    @Query("select coalesce(max(c.numeroBase), 0) from Conta c")
    int maiorNumeroBase();

    /** Contas com saldo negativo: base da cobrança diária de juros do cheque especial. */
    @Query("select c from Conta c where c.saldo < 0 and c.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    List<Conta> usandoChequeEspecial();

    @Query("select coalesce(sum(c.saldo), 0) from Conta c where c.saldo > 0")
    BigDecimal totalEmCustodia();

    @Query("select coalesce(sum(c.limite), 0) from ContaCorrente c where c.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    BigDecimal totalDeLimiteConcedido();

    /** Poupanças que podem render (base do crédito diário de rendimentos). */
    @Query("select p from ContaPoupanca p where p.saldo > 0 and p.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    List<ContaPoupanca> poupancasComSaldo();

    @Query("select c from ContaCorrente c where c.escudoAtivo = true and c.saldo < 0 and c.situacao = dev.barboza.cofre.dominio.SituacaoConta.ATIVA")
    List<ContaCorrente> comEscudoNoNegativo();

    @Query("select p from ContaPoupanca p where p.cliente.id = :clienteId and p.situacao = dev.barboza.cofre.dominio.SituacaoConta.ATIVA order by p.numeroBase")
    List<ContaPoupanca> poupancasAtivasDoCliente(@Param("clienteId") Long clienteId);

    @Query("select count(c) from ContaCorrente c where c.escudoAtivo = true")
    long contasComEscudo();

    @Query("select coalesce(sum(c.saldo), 0) from ContaCorrente c where c.saldo > 0")
    BigDecimal custodiaEmContaCorrente();

    @Query("select coalesce(sum(p.saldo), 0) from ContaPoupanca p")
    BigDecimal custodiaEmPoupanca();

    @Query("select count(p) from ContaPoupanca p where p.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    long poupancasAtivas();

    @Query("select count(c) from ContaCorrente c where c.saldo < 0")
    long correntesNoNegativo();

    /** Saldo positivo de cada cliente, do maior para o menor (concentração da carteira). */
    @Query("select c.cliente.nome, sum(c.saldo) from Conta c where c.saldo > 0 group by c.cliente.id, c.cliente.nome order by sum(c.saldo) desc")
    List<Object[]> saldoPorCliente();

    @Query("select count(p) from ContaPoupanca p where p.cliente.id = :clienteId and p.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    long poupancasAbertas(@Param("clienteId") Long clienteId);

    @Query("select coalesce(sum(-c.saldo), 0) from Conta c where c.saldo < 0")
    BigDecimal totalDeLimiteEmUso();

    long countBySituacao(SituacaoConta situacao);

    @Query("""
            select c from Conta c
            where lower(c.cliente.nome) like lower(concat('%', :termo, '%'))
               or c.cliente.cpf like concat('%', :digitos, '%')
               or c.numero like concat('%', :termo, '%')
            order by c.numeroBase""")
    List<Conta> buscar(@Param("termo") String termo, @Param("digitos") String digitos);
}
