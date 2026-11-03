package com.jogodavelha.game;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.Room;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade que representa uma partida de Jogo da Velha.
 * Armazena informações persistentes sobre as partidas.
 */
@Entity
@Table(name = "games")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    public static final int SUDDEN_DEATH_TURN_SECONDS = 5;
    public static final int MAX_ROUNDS = 3;
    public static final String EMPTY_BOARD = "----------------";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    private User player2;

    @Column(nullable = false, length = 16)
    private String board = EMPTY_BOARD;

    @Column(name = "current_round", nullable = false)
    private int currentRound = 1;

    @Column(name = "victories_player1", nullable = false)
    private int victoriesPlayer1 = 0;

    @Column(name = "victories_player2", nullable = false)
    private int victoriesPlayer2 = 0;

    @Column(name = "sudden_death", nullable = false)
    private boolean suddenDeath = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameStatus status = GameStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_character_id")
    private com.jogodavelha.character.Character player1Character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_character_id")
    private com.jogodavelha.character.Character player2Character;

    @Column(name = "rewards_granted", nullable = false)
    private boolean rewardsGranted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", nullable = false, length = 16)
    private GameEndReason endReason = GameEndReason.NORMAL;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /**
     * Resultado das recompensas concedidas na finalização da partida.
     * Utilizado pela publicação do evento MATCH_END.
     */
    @Transient
    private RewardResult rewardResult;

    public Game(Room room, User player1) {
        if (room == null || room.getId() == null) {
            throw new IllegalArgumentException("Room não pode ser nula");
        }
        if (player1 == null || player1.getId() == null) {
            throw new IllegalArgumentException("Player1 não pode ser nulo");
        }
        this.room = room;
        this.player1 = player1;
        this.startedAt = Instant.now();
    }

    public void setPlayer2(User player2) {
        if (player2 == null || player2.getId() == null) {
            throw new IllegalArgumentException("Player2 não pode ser nulo");
        }
        if (player1.getId().equals(player2.getId())) {
            throw new IllegalArgumentException("Player2 deve ser diferente de Player1");
        }
        this.player2 = player2;
    }

    public void setPlayer1Character(com.jogodavelha.character.Character character) {
        this.player1Character = character;
    }

    public void setPlayer2Character(com.jogodavelha.character.Character character) {
        this.player2Character = character;
    }

    /**
     * Retorna o round atual da partida.
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * Retorna o placar atual da partida.
     */
    public Score getScore() {
        return new Score(victoriesPlayer1, victoriesPlayer2);
    }

    public void setRewardResult(RewardResult rewardResult) {
        this.rewardResult = rewardResult;
    }

    /**
     * Retorna o resultado final da partida.
     *
     * @return GameOutcome indicando quem venceu ou se houve empate
     * @throws IllegalStateException se a partida ainda não estiver FINISHED
     */
    public GameOutcome getOutcome() {
        if (status != GameStatus.FINISHED) {
            throw new IllegalStateException("Não é possível obter resultado de partida em andamento");
        }

        if (winner == null) {
            return GameOutcome.DRAW;
        }
        if (player1.getId().equals(winner.getId())) {
            return GameOutcome.PLAYER1_WIN;
        }
        return GameOutcome.PLAYER2_WIN;
    }

    /** Encerra a partida por timeout, sem alterar o placar dos rounds já jogados. */
    public void forfeit(User loser) {
        if (status == GameStatus.FINISHED) {
            throw new IllegalStateException("A partida já foi encerrada.");
        }
        if (player2 == null) {
            throw new IllegalStateException("W.O. exige os dois jogadores.");
        }
        if (loser == null || loser.getId() == null) {
            throw new IllegalArgumentException("O perdedor deve ser um jogador da partida.");
        }
        User forfeitWinner;
        if (player1.getId().equals(loser.getId())) {
            forfeitWinner = player2;
        } else if (player2.getId().equals(loser.getId())) {
            forfeitWinner = player1;
        } else {
            throw new IllegalArgumentException("O perdedor não pertence à partida.");
        }
        winner = forfeitWinner;
        endReason = GameEndReason.FORFEIT_TIMEOUT;
        finishedAt = Instant.now();
        status = GameStatus.FINISHED;
    }

    /**
     * Marca as recompensas como concedidas.
     *
     * @throws IllegalStateException se a partida não estiver FINISHED
     */
    public void markRewardsGranted() {
        if (status != GameStatus.FINISHED) {
            throw new IllegalStateException("Não é possível marcar recompensas em partida em andamento");
        }
        this.rewardsGranted = true;
    }

    /**
     * Registra o resultado de um round e aplica as regras de rounds.
     * Ao avançar de round, limpa o tabuleiro. Ao entrar no round 3, ativa suddenDeath.
     *
     * @param result O resultado do round
     * @throws IllegalArgumentException se result for nulo
     * @throws IllegalStateException se a partida já estiver FINISHED ou player2 não estiver definido
     */
    public void registerRoundResult(RoundResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Resultado do round não pode ser nulo");
        }

        if (status == GameStatus.FINISHED) {
            throw new IllegalStateException("Não é possível registrar resultado em partida já encerrada");
        }

        if (player2 == null) {
            throw new IllegalStateException("Não é possível registrar round sem ambos os jogadores");
        }

        switch (result) {
            case PLAYER1_WIN -> victoriesPlayer1++;
            case PLAYER2_WIN -> victoriesPlayer2++;
            case DRAW -> {
                // Empate não soma vitória para ninguém
            }
        }

        // Verifica se a partida deve terminar após o round atual
        if (currentRound == MAX_ROUNDS - 1) {
            if (victoriesPlayer1 > victoriesPlayer2 || victoriesPlayer2 > victoriesPlayer1) {
                // Alguém tem mais vitórias, partida termina
                finish();
                return;
            }
            // Vitórias iguais (1x1 ou 0x0), vai para o round 3
        } else if (currentRound == MAX_ROUNDS) {
            // Após o round 3, sempre encerra a partida
            finish();
            return;
        }

        // Avança para o próximo round
        currentRound++;
        clearBoard();

        // Ativa sudden death no round 3
        if (currentRound == MAX_ROUNDS) {
            suddenDeath = true;
        }
    }

    /**
     * Encerra a partida e define o vencedor.
     * Método privado - só chamado por registerRoundResult.
     */
    private void finish() {
        if (status == GameStatus.FINISHED) {
            return;
        }

        status = GameStatus.FINISHED;
        endReason = GameEndReason.NORMAL;
        finishedAt = Instant.now();

        // Define o vencedor com base no placar
        if (victoriesPlayer1 > victoriesPlayer2) {
            winner = player1;
        } else if (victoriesPlayer2 > victoriesPlayer1) {
            winner = player2;
        }
        // Empate: winner permanece null
    }

    /**
     * Limpa o tabuleiro, restaurando o estado inicial.
     */
    private void clearBoard() {
        this.board = EMPTY_BOARD;
    }

    /**
     * Define o tabuleiro com um estado específico.
     * Método protegido para uso em testes.
     */
    protected void setBoard(String board) {
        this.board = board;
    }
}
