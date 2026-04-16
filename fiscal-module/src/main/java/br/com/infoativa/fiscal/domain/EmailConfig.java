package br.com.infoativa.fiscal.domain;

public record EmailConfig(String host, int port, String usuario, String senha, boolean ssl) {}
