package br.com.infoativa.fiscal.domain;

public record UsuarioRegistro(
    long id,
    String usuario,
    String senha,
    String funcao,
    int nivel,
    String bloqueado,
    String hashTripa,
    String controle
) {
    public boolean isBloqueado() {
        return "S".equalsIgnoreCase(bloqueado) || "SIM".equalsIgnoreCase(bloqueado);
    }
}
