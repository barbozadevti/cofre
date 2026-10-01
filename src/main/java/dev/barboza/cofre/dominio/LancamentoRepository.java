package dev.barboza.cofre.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    List<Lancamento> findByContaIdAndDataHoraGreaterThanEqualAndDataHoraLessThanOrderByDataHoraAscIdAsc(
            Long contaId, Instant inicio, Instant fim);

    /** Último lançamento antes do período, para saber o saldo inicial do extrato. */
    Optional<Lancamento> findFirstByContaIdAndDataHoraLessThanOrderByDataHoraDescIdDesc(Long contaId, Instant antes);

    List<Lancamento> findTop8ByContaIdOrderByDataHoraDescIdDesc(Long contaId);

    List<Lancamento> findByIdTransacaoOrderByIdAsc(String idTransacao);

    Optional<Lancamento> findByContaIdAndChaveIdempotencia(Long contaId, String chaveIdempotencia);

    boolean existsByIdTransacao(String idTransacao);

    long countByContaIdAndTipo(Long contaId, TipoLancamento tipo);

    /** Já houve envio desta conta para a contraparte? (antifraude: destinatário novo) */
    boolean existsByContaIdAndContraparteAndTipoIn(Long contaId, String contraparte, Collection<TipoLancamento> tipos);

    @Query("""
            select l.valor from Lancamento l
            where l.conta.id = :contaId and l.tipo = :tipo and l.dataHora >= :inicio and l.dataHora < :fim""")
    List<BigDecimal> valores(@Param("contaId") Long contaId, @Param("tipo") TipoLancamento tipo,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Soma das saídas de certos tipos num intervalo (limites do Pix). */
    @Query("""
            select coalesce(sum(l.valor), 0) from Lancamento l
            where l.conta.id = :contaId and l.tipo in :tipos and l.dataHora >= :inicio and l.dataHora < :fim""")
    BigDecimal somar(@Param("contaId") Long contaId, @Param("tipos") Collection<TipoLancamento> tipos,
            @Param("inicio") Instant inicio, @Param("fim") Instant fim);

    /** Lançamentos de várias contas num intervalo (gráfico de entradas e saídas). */
    List<Lancamento> findByContaIdInAndDataHoraGreaterThanEqualOrderByDataHoraAsc(Collection<Long> contas, Instant inicio);

    @Query("""
            select count(l), coalesce(sum(l.valor), 0) from Lancamento l
            where l.tipo = :tipo and l.dataHora >= :inicio and l.dataHora < :fim""")
    List<Object[]> contarESomar(@Param("tipo") TipoLancamento tipo, @Param("inicio") Instant inicio, @Param("fim") Instant fim);
}
