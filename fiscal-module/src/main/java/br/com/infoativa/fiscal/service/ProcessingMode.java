package br.com.infoativa.fiscal.service;

public enum ProcessingMode {
    AUTO, ANUAL, MANUAL;

    public static ProcessingMode fromString(String mode) {
        if (mode == null) return AUTO;
        return switch (mode.toUpperCase()) {
            case "ANUAL" -> ANUAL;
            case "MANUAL" -> MANUAL;
            default -> AUTO;
        };
    }
}
