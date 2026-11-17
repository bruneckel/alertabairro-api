package br.com.fiap.alertabairro.web.dto;

/**
 * Usuario como o app o conhece.
 *
 * <p>Nao existe campo de senha neste record: o hash nao tem como sair da API por descuido,
 * porque nao ha onde ele caiba.
 */
public record UsuarioResponse(
    long id, String nome, String email, String bairro, String perfil) {}
