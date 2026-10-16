package com.jogodavelha.room;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.UUID;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "Criação e entradas em salas de jogo")
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "Criar sala", description = "Cria sala para o jogador informado")
    @ApiResponse(responseCode = "201", description = "Sala criada com sucesso")
    @ApiResponse(responseCode = "400", description = "Jogador inválido ou não encontrado", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Jogador já está em sala/partida ativa", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @PostMapping
    public ResponseEntity<RoomDTO> createRoom(
            Authentication authentication
    ) {
        UUID playerId = (UUID) authentication.getPrincipal();

        /*
        NOTA: a conversão Room → RoomDTO é feita aqui no Controller (e não no
         RoomService) para não alterar o contrato público do Service, que já
         está coberto por testes unitários na tarefa BE-008. Isso funciona porque
         o Spring mantém a sessão do Hibernate aberta durante toda a requisição
         (spring.jpa.open-in-view, habilitado por padrão), permitindo o acesso
         aos campos lazy (player1, player2) neste ponto. Caso o time decida
         desabilitar open-in-view no futuro, essa conversão deve ser movida para
         dentro do RoomService (ajustando também o RoomServiceTest).
         */

        RoomDTO roomDTO = RoomDTO.from(
                roomService.createRoom(playerId)
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(roomDTO);
    }

    @Operation(summary = "Entrar em sala", description = "Jogador entra em uma sala existente pelo código")
    @ApiResponse(responseCode = "200", description = "Entrou na sala com sucesso")
    @ApiResponse(responseCode = "400", description = "Código inválido ou jogador não encontrado", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Sala não encontrada ou encerrada", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Sala cheia, partida já iniciada ou jogador indisponível", content = @Content(schema = @Schema(implementation = com.jogodavelha.error.ApiError.class)))
    @PostMapping("/{code}/join")
    public ResponseEntity<RoomDTO> joinRoom(
            @PathVariable String code,
            Authentication authentication
    ) {

        UUID playerId = (UUID) authentication.getPrincipal();

        RoomDTO roomDTO =
                RoomDTO.from(
                        roomService.joinRoom(code, playerId)
                );

        return ResponseEntity.ok(roomDTO);
    }
}
