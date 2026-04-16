package br.com.infoativa.fiscal.service;

import br.com.infoativa.fiscal.domain.Periodo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class PeriodService {

    public static Periodo resolveAuto() {
        LocalDate today = LocalDate.now();
        YearMonth prev = YearMonth.from(today).minusMonths(1);
        return new Periodo(prev.atDay(1), prev.atEndOfMonth());
    }

    public static Periodo resolveManual(LocalDate inicio, LocalDate fim) {
        return new Periodo(inicio, fim);
    }

    public static List<Periodo> resolveAnual(int ano) {
        List<Periodo> periodos = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(ano, m);
            if (ym.isAfter(YearMonth.now())) break;
            periodos.add(new Periodo(ym.atDay(1), ym.atEndOfMonth()));
        }
        return periodos;
    }

    public static String nomeMes(int mes) {
        return switch (mes) {
            case 1 -> "Janeiro"; case 2 -> "Fevereiro"; case 3 -> "Marco";
            case 4 -> "Abril"; case 5 -> "Maio"; case 6 -> "Junho";
            case 7 -> "Julho"; case 8 -> "Agosto"; case 9 -> "Setembro";
            case 10 -> "Outubro"; case 11 -> "Novembro"; case 12 -> "Dezembro";
            default -> "Mes " + mes;
        };
    }
}
