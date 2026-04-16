package br.com.infoativa.fiscal.config;

import br.com.infoativa.fiscal.domain.EmailConfig;
import java.util.List;

public record AppConfig(
    String ipServidor,
    int porta,
    String basePath,
    String caminhoXmlNfe,
    String caminhoXmlNfce,
    String caminhoXmlCompras,
    boolean utilizaSenha,
    String senhaAcesso,
    boolean utilizaSat,
    EmailConfig emailConfig,
    List<String> emailDestinatarios
) {}
