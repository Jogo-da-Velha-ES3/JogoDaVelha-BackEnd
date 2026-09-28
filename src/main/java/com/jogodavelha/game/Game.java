package com.jogodavelha.game;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.Room;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Entidade que representa uma partida de Jogo da Velha.
 * Armazena informações persistentes sobre as partidas.
 */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    public static final int SUDDEN_DEATH_TURN_SECONDS = 5;
    public static final int COINS_WIN = 50;
    public static final int COINS_DRAW = 20;
    public static final int COINS_LOSS = 10;
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

    @Column(name = "rewards_granted", nullable = false)
    private boolean rewardsGranted = false;

    public Game(Room room, User player1) {
        if (room == null || room.getId() == null) {
            throw new IllegalArgumentException("Room não pode ser nula");
        }
        if (player1 == null || player1.getId() == null) {
            throw new IllegalArgumentException("Player1 não pode ser nulo");
        }
        this.room = room;
        this.player1 = player1;
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

    /**
     * Registra o resultado de um round e aplica as regras de rounds.
     * Ao avançar de round, limpa o tabuleiro. Ao entrar no round 3, ativa suddenDeath.
     *
     * @param result O resultado do round
     * @throws IllegalStateException se a partida já estiver FINISHED
     */
    public void registerRoundResult(RoundResult result) {
        if (status == GameStatus.FINISHED) {
            throw new IllegalStateException("Não é possível registrar resultado em partida já encerrada");
        }

        switch (result) {
            case PLAYER1_WIN -> victoriesPlayer1++;
            case PLAYER2_WIN -> victoriesPlayer2++;
            case DRAW -> {
                // Empate não soma vitória para ninguém
            }
        }

        // Verifica se a partida deve terminar após o round atual
        if (currentRound == 2) {
            if (victoriesPlayer1 > victoriesPlayer2 || victoriesPlayer2 > victoriesPlayer1) {
                // Alguém tem mais vitórias, partida termina
                finish();
                return;
            }
            // Vitórias iguais (1x1 ou 0x0), vai para o round 3
        } else if (currentRound == 3) {
            // Após o round 3, sempre encerra a partida
            finish();
            return;
        }

        // Avança para o próximo round
        currentRound++;
        clearBoard();

        // Ativa sudden death no round 3
        if (currentRound == 3) {
            suddenDeath = true;
        }
    }

    /**
     * Encerra a partida, define o vencedor e concede recompensas.
     */
    public void finish() {
        if (status == GameStatus.FINISHED) {
            return;
        }

        // Valida que ambos os jogadores estão definidos
        if (player2 == null) {
            throw new IllegalStateException("Não é possível encerrar partida sem ambos os jogadores");
        }

        status = GameStatus.FINISHED;

        // Define o vencedor com base no placar
        if (victoriesPlayer1 > victoriesPlayer2) {
            winner = player1;
        } else if (victoriesPlayer2 > victoriesPlayer1) {
            winner = player2;
        }
        // Empate: winner permanece null

        grantRewards();
    }

    /**
     * Concede recompensas em moedas aos jogadores.
     * Vencedor: +50, Perdedor: +10, Empate: +20 para cada.
     * Só concede se rewardsGranted for false.
     */
    public void grantRewards() {
        if (rewardsGranted) {
            return;
        }

        if (victoriesPlayer1 == victoriesPlayer2) {
            // Empate: +20 para cada
            player1.addCoins(COINS_DRAW);
            player2.addCoins(COINS_DRAW);
        } else if (victoriesPlayer1 > victoriesPlayer2) {
            // Player1 venceu
            player1.addCoins(COINS_WIN);
            player2.addCoins(COINS_LOSS);
        } else {
            // Player2 venceu
            player2.addCoins(COINS_WIN);
            player1.addCoins(COINS_LOSS);
        }

        rewardsGranted = true;
    }

    /**
     * Limpa o tabuleiro, restaurando o estado inicial.
     */
    private void clearBoard() {
        this.board = EMPTY_BOARD;
    }
}
