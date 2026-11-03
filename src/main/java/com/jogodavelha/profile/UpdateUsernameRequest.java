package com.jogodavelha.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UpdateUsernameRequest(

        @NotBlank(message = "Username é obrigatório")
        @Size(
                min = 3,
                max = 15,
                message = "Username deve ter entre 3 e 15 caracteres"
        )
        String username

) {
}
