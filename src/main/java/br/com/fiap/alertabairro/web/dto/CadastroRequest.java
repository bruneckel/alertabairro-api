package br.com.fiap.alertabairro.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /auth/cadastro}.
 *
 * <p>A validacao e declarativa, junto do campo: substitui as checagens manuais que o
 * back-end anterior espalhava pelo handler da rota.
 */
public record CadastroRequest(
    @NotBlank(message = "nome e obrigatorio")
    @Size(max = 100, message = "nome deve ter no maximo 100 caracteres")
    String nome,

    @NotBlank(message = "e-mail e obrigatorio")
    @Email(message = "e-mail invalido")
    @Size(max = 150, message = "e-mail deve ter no maximo 150 caracteres")
    String email,

    @NotBlank(message = "bairro e obrigatorio")
    @Size(max = 100, message = "bairro deve ter no maximo 100 caracteres")
    String bairro,

    // bcrypt so considera os primeiros 72 bytes da senha.
    @NotBlank(message = "senha e obrigatoria")
    @Size(min = 6, max = 72, message = "senha deve ter de 6 a 72 caracteres")
    String senha,

    @Pattern(regexp = "morador|idoso|voluntario",
             message = "perfil deve ser: morador, idoso, voluntario")
    String perfil) {

  /**
   * Normaliza na CONSTRUCAO, nao em um metodo auxiliar.
   *
   * <p>A Bean Validation roda depois que o Jackson monta o record, entao normalizar aqui
   * e o que faz {@code @Email} enxergar o valor limpo. Com a normalizacao num metodo
   * separado, um e-mail valido cercado de espacos era recusado com 400 antes de alguem
   * ter a chance de apara-lo -- e espaco sobrando e exatamente o que o teclado do celular
   * acrescenta sozinho.
   *
   * <p>Aparar nome e bairro aqui tambem faz {@code @NotBlank} recusar " ".
   */
  public CadastroRequest {
    nome = nome == null ? null : nome.trim();
    email = email == null ? null : email.trim().toLowerCase();
    bairro = bairro == null ? null : bairro.trim();
  }
}
