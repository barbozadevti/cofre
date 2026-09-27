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

    @Query("select coalesce(sum(c.limite), 0) from Conta c where c.situacao <> dev.barboza.cofre.dominio.SituacaoConta.ENCERRADA")
    BigDecimal totalDeLimiteConcedido();

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
