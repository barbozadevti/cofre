package dev.barboza.cofre.salario;

import java.util.Arrays;

import dev.barboza.cofre.dominio.OperacaoInvalidaException;

/** Bancos de onde o salário pode vir (código de compensação e nome). A portabilidade é simulada. */
public enum BancoDeOrigem {

    B001("001", "Banco do Brasil"),
    B033("033", "Santander"),
    B077("077", "Inter"),
    B104("104", "Caixa Econômica Federal"),
    B237("237", "Bradesco"),
    B260("260", "Nubank"),
    B336("336", "C6 Bank"),
    B341("341", "Itaú Unibanco");

    private final String codigo;
    private final String nome;

    BancoDeOrigem(String codigo, String nome) {
        this.codigo = codigo;
        this.nome = nome;
    }

    public String codigo() {
        return codigo;
    }

    public String nome() {
        return nome;
    }

    public static BancoDeOrigem porCodigo(String codigo) {
        return Arrays.stream(values()).filter(b -> b.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new OperacaoInvalidaException("Escolha o banco onde o salário cai hoje."));
    }
}
