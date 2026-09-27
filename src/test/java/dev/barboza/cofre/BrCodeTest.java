package dev.barboza.cofre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import dev.barboza.cofre.cartao.Luhn;
import dev.barboza.cofre.pix.BrCode;
import dev.barboza.cofre.pix.QrCodeSvg;

class BrCodeTest {

    /** Exemplo de BR Code estático usado na documentação do Pix (chave aleatória, sem valor). */
    private static final String EXEMPLO_BCB = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000"
            + "5204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D";

    @Test
    void geraExatamenteOExemploDaDocumentacao() {
        String gerado = BrCode.gerar(new BrCode.Dados("123e4567-e12b-12d1-a456-426655440000", "Fulano de Tal",
                "BRASILIA", null, null, null));
        assertThat(gerado).isEqualTo(EXEMPLO_BCB);
    }

    @Test
    void leOExemploDaDocumentacao() {
        BrCode.Dados dados = BrCode.ler(EXEMPLO_BCB);
        assertThat(dados.chave()).isEqualTo("123e4567-e12b-12d1-a456-426655440000");
        assertThat(dados.nome()).isEqualTo("Fulano de Tal");
        assertThat(dados.cidade()).isEqualTo("BRASILIA");
        assertThat(dados.valor()).isNull();
        assertThat(dados.txid()).isNull();
    }

    @Test
    void idaEVoltaComValorDescricaoETxidSemAcentos() {
        String codigo = BrCode.gerar(new BrCode.Dados("ana@cofre.dev", "Ana Conceição Souza Albuquerque", "São Paulo",
                new BigDecimal("150.5"), "COFRE123", "Aulas de inglês"));

        BrCode.Dados lido = BrCode.ler(codigo);

        assertThat(lido.chave()).isEqualTo("ana@cofre.dev");
        assertThat(lido.nome()).isEqualTo("Ana Conceicao Souza Albuq");
        assertThat(lido.cidade()).isEqualTo("Sao Paulo");
        assertThat(lido.valor()).isEqualByComparingTo("150.50");
        assertThat(lido.txid()).isEqualTo("COFRE123");
        assertThat(lido.descricao()).isEqualTo("Aulas de ingles");
        assertThat(codigo).contains("5406150.50");
    }

    @Test
    void recusaCodigoAlterado() {
        String alterado = EXEMPLO_BCB.replace("Fulano", "Fulana");
        assertThatThrownBy(() -> BrCode.ler(alterado)).hasMessageContaining("CRC");
        assertThatThrownBy(() -> BrCode.ler(EXEMPLO_BCB.substring(0, 40))).hasMessageContaining("incompleto");
        assertThatThrownBy(() -> BrCode.ler("qualquer coisa")).hasMessageStartingWith("Código Pix inválido");
    }

    @Test
    void qrCodeEmSvg() {
        String svg = QrCodeSvg.gerar(EXEMPLO_BCB);
        assertThat(svg).startsWith("<svg").contains("viewBox=\"0 0 ").contains("<path").endsWith("</svg>");
    }

    @Test
    void luhnDosCartoes() {
        assertThat(Luhn.valido("4111111111111111")).isTrue();
        assertThat(Luhn.valido("5555555555554444")).isTrue();
        assertThat(Luhn.valido("4111111111111112")).isFalse();
        assertThat(Luhn.digito("411111111111111")).isEqualTo(1);
    }
}
