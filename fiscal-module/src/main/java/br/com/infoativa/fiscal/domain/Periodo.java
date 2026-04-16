package br.com.infoativa.fiscal.domain;

import java.time.LocalDate;

public record Periodo(LocalDate inicio, LocalDate fim) {
    public String descricao() {
        return String.format("%02d/%d a %02d/%d",
            inicio.getMonthValue(), inicio.getYear(),
            fim.getMonthValue(), fim.getYear());
    }

    public String mesAnoRef() {
        return String.format("%02d_%d", inicio.getMonthValue(), inicio.getYear());
    }
}
