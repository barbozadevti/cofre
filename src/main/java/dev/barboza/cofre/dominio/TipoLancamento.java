package dev.barboza.cofre.dominio;

public enum TipoLancamento {

    ABERTURA("Abertura de conta", true),
    DEPOSITO("Depósito em espécie", true),
    SAQUE("Saque em espécie", false),
    TRANSFERENCIA_ENVIADA("Transferência enviada", false),
    TRANSFERENCIA_RECEBIDA("Transferência recebida", true),
    PIX_ENVIADO("Pix enviado", false),
    PIX_RECEBIDO("Pix recebido", true),
    CAIXINHA_GUARDADO("Guardado na caixinha", false),
    CAIXINHA_RESGATADO("Resgate da caixinha", true),
    JUROS_CHEQUE_ESPECIAL("Juros do cheque especial", false),
    RENDIMENTO_POUPANCA("Rendimento da poupança", true),
    ESCUDO_RESGATE("Escudo de juros: resgate automático", false),
    ESCUDO_COBERTURA("Escudo de juros: saldo negativo coberto", true),
    SALARIO_PORTADO("Salário (portabilidade)", true);

    private final String descricao;
    private final boolean credito;

    TipoLancamento(String descricao, boolean credito) {
        this.descricao = descricao;
        this.credito = credito;
    }

    public String descricao() {
        return descricao;
    }

    public boolean credito() {
        return credito;
    }
}
