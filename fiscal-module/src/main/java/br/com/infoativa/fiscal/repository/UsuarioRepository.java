package br.com.infoativa.fiscal.repository;

import br.com.infoativa.fiscal.domain.UsuarioRegistro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsuarioRepository {

    private static final Logger log = LoggerFactory.getLogger(UsuarioRepository.class);
    private final Connection conn;

    public UsuarioRepository(Connection conn) {
        this.conn = conn;
    }

    public UsuarioRegistro authenticate(String usuario, String senha) {
        String sql = "SELECT ID, USUARIO, SENHA, FUNCAO, NIVEL, BLOQUEADO, HASH_TRIPA, CONTROLE FROM USUARIO WHERE UPPER(USUARIO) = UPPER(?) AND SENHA = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, senha);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UsuarioRegistro u = mapRow(rs);
                    if (u.isBloqueado()) {
                        log.warn("Usuario bloqueado: {}", usuario);
                        return null;
                    }
                    log.info("Login bem-sucedido: {} (Nivel: {})", usuario, u.nivel());
                    return u;
                }
            }
        } catch (SQLException e) {
            log.error("Erro ao autenticar: {}", e.getMessage());
        }
        log.warn("Falha no login: {}", usuario);
        return null;
    }

    public List<UsuarioRegistro> findAll() {
        List<UsuarioRegistro> usuarios = new ArrayList<>();
        String sql = "SELECT ID, USUARIO, SENHA, FUNCAO, NIVEL, BLOQUEADO, HASH_TRIPA, CONTROLE FROM USUARIO ORDER BY USUARIO";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usuarios.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Erro ao listar usuarios: {}", e.getMessage());
        }
        return usuarios;
    }

    private UsuarioRegistro mapRow(ResultSet rs) throws SQLException {
        return new UsuarioRegistro(
            rs.getLong("ID"),
            rs.getString("USUARIO") != null ? rs.getString("USUARIO").trim() : "",
            rs.getString("SENHA") != null ? rs.getString("SENHA").trim() : "",
            rs.getString("FUNCAO") != null ? rs.getString("FUNCAO").trim() : "",
            rs.getInt("NIVEL"),
            rs.getString("BLOQUEADO") != null ? rs.getString("BLOQUEADO").trim() : "N",
            rs.getString("HASH_TRIPA") != null ? rs.getString("HASH_TRIPA").trim() : "",
            rs.getString("CONTROLE") != null ? rs.getString("CONTROLE").trim() : ""
        );
    }
}
