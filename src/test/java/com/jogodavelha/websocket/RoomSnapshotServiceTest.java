package com.jogodavelha.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.User;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftEligibilityProvider;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.draw.RoundDrawState;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomSnapshotServiceTest {
    private static final List<RoomStatus> ACTIVE = List.of(RoomStatus.WAITING, RoomStatus.IN_GAME);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final DraftStateManager drafts = mock(DraftStateManager.class);
    private final GameStateManager gameStates = mock(GameStateManager.class);
    private final RoomPlayerService roomPlayers = spy(new RoomPlayerService(
            mock(com.jogodavelha.avatar.AvatarService.class)));
    private final PlayerService playerService = mock(PlayerService.class);
    private final CharacterRepository characters = mock(CharacterRepository.class);
    private final WebSocketService websocket = mock(WebSocketService.class);
    private final RoomSnapshotService snapshots = new RoomSnapshotService(
            rooms, games, drafts, gameStates, roomPlayers, playerService, characters, websocket);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void snapshotMatchesFrontendShapeAndUsesSharedPlayerData() {
        RoomFixture fixture = activeRoom(true);

        var snapshot = snapshots.compose("0042", fixture.host().getId());

        assertEquals("0042", snapshot.code());
        assertEquals(RoomSnapshotService.RoomPhase.WAITING, snapshot.phase());
        assertEquals(RoomSnapshotService.Slot.P1, snapshot.mySlot());
        assertEquals(1, snapshot.players().size());
        assertEquals(fixture.host().getId().toString(), snapshot.players().getFirst().userId());
        assertEquals("host", snapshot.players().getFirst().nickname());
        assertEquals(16, snapshot.board().size());
        assertEquals(0, snapshot.board().getFirst().index());
        assertNull(snapshot.turn());
        assertTrue(snapshot.myDeck().isEmpty());
        assertNull(snapshot.outcome());
        assertTrue(snapshot.serverTime() > 0);
        verify(roomPlayers).host(fixture.room());
    }

    @Test
    void publicSnapshotContainsNoPrivatePlayerValuesAndPrivateQueueGetsPerPlayerSnapshot() throws Exception {
        RoomFixture fixture = activeRoom(false);
        UUID gameId = UUID.randomUUID();
        Game game = new Game(fixture.room(), fixture.host());
        game.setPlayer2(fixture.guest());
        setId(game, gameId);
        when(games.findByRoomIdAndStatus(fixture.room().getId(), GameStatus.IN_PROGRESS))
                .thenReturn(Optional.of(game));
        GameState state = GameState.builder().gameId(gameId).board(GameState.EMPTY_BOARD)
                .player1Id(fixture.host().getId()).player2Id(fixture.guest().getId())
                .roundNumber(1).build();
        state.setInitialSkills(fixture.host().getId(), List.of("BOMBA", "ESCUDO"));
        when(gameStates.find(gameId)).thenReturn(Optional.of(state));

        snapshots.publish("0042");

        var topicCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), topicCaptor.capture());
        JsonNode topicJson = mapper.valueToTree(topicCaptor.getValue());
        assertEquals("ROOM_SNAPSHOT", topicJson.path("type").asText());
        assertEquals(topicJson.path("serverTime").asLong(), topicJson.path("payload").path("serverTime").asLong());
        assertTrue(topicJson.path("serverTime").isNumber());
        assertTrue(topicJson.path("payload").path("mySlot").isNull());
        assertTrue(topicJson.path("payload").path("myDeck").isEmpty());
        assertTrue(topicJson.path("payload").path("drawOptions").isNull());
        assertFalse(topicJson.toString().contains("BOMBA"));

        var privateCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(fixture.host().getId().toString()), eq("/queue/room"), privateCaptor.capture());
        JsonNode privateJson = mapper.valueToTree(privateCaptor.getValue());
        assertEquals("ROOM_SNAPSHOT", privateJson.path("type").asText());
        assertEquals("P1", privateJson.path("payload").path("mySlot").asText());
        assertEquals("BOMBA", privateJson.path("payload").path("myDeck").get(0).path("ability").asText());
        verify(websocket).sendToUser(eq(fixture.guest().getId().toString()), eq("/queue/room"), any());
    }

    @Test
    void publishedSnapshotProjectsPersistedDraftSelectionAndReadiness() {
        RoomFixture fixture = activeRoom(false);
        UUID characterId = UUID.randomUUID();
        var eligible = new DraftEligibilityProvider.EligibleItems(
                List.of(characterId, UUID.randomUUID()), List.of("BOMBA", "ESCUDO"));
        var draft = new DraftState(fixture.room().getId(), "0042", 21_000L, 2L,
                DraftState.Player.initial(fixture.host().getId(), eligible)
                        .withCharacter(characterId).withSkills(List.of("BOMBA", "ESCUDO")),
                DraftState.Player.initial(fixture.guest().getId(), eligible));
        when(drafts.find(fixture.room().getId())).thenReturn(Optional.of(draft));
        var character = new com.jogodavelha.character.Character("X",
                com.jogodavelha.character.CharacterSymbol.X, 0);
        character.setId(characterId);
        when(characters.findAllById(List.of(characterId))).thenReturn(List.of(character));

        snapshots.publish("0042");

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(eq("/topic/rooms/0042"), captor.capture());
        JsonNode snapshot = mapper.valueToTree(captor.getValue());
        assertEquals("ROOM_SNAPSHOT", snapshot.path("type").asText());
        assertEquals(21_000L, snapshot.path("payload").path("draft").path("deadline").asLong());
        assertTrue(snapshot.path("payload").path("draft").path("ready").path("P1").asBoolean());
        assertFalse(snapshot.path("payload").path("draft").path("ready").path("P2").asBoolean());
        assertEquals("X", snapshot.path("payload").path("players").get(0).path("character").asText());
    }

    @Test
    void mapsLiveGameStateToFrontendBoardTurnPlayersAndPrivateDeck() throws Exception {
        RoomFixture fixture = activeRoom(false);
        UUID gameId = UUID.randomUUID();
        Game game = new Game(fixture.room(), fixture.host());
        game.setPlayer2(fixture.guest());
        setId(game, gameId);
        when(games.findByRoomIdAndStatus(fixture.room().getId(), GameStatus.IN_PROGRESS))
                .thenReturn(Optional.of(game));
        GameState state = GameState.builder()
                .gameId(gameId)
                .board("X---------------")
                .player1Id(fixture.host().getId())
                .player2Id(fixture.guest().getId())
                .firstPlayerId(fixture.host().getId())
                .currentTurnPlayerId(fixture.guest().getId())
                .turnDurationMillis(10_000)
                .turnDeadlineEpochMillis(50_000)
                .roundNumber(2)
                .phase(GamePhase.PLAYING)
                .shieldedPositions(Map.of(1, new GameState.ShieldEffect(fixture.host().getId(), 2)))
                .build();
        state.setPlayer1ConsecutiveTimeouts(1);
        state.setInitialSkills(fixture.host().getId(), List.of("BOMBA", "ESCUDO"));
        state.setRoundSkillDeck(fixture.host().getId(), List.of("BOMBA", "ESCUDO", "TORNADO"));
        state.recordSkillUse(fixture.host().getId(), "BOMBA");
        when(gameStates.find(gameId)).thenReturn(Optional.of(state));

        var snapshot = snapshots.compose("0042", fixture.host().getId());

        assertEquals(RoomSnapshotService.RoomPhase.PLAYING, snapshot.phase());
        assertEquals(gameId, snapshot.gameId());
        assertEquals(RoomSnapshotService.Slot.P1, snapshot.players().get(0).slot());
        assertEquals(1, snapshot.players().get(0).consecutiveTimeouts());
        assertEquals(RoomSnapshotService.Slot.P1, snapshot.board().get(0).owner());
        assertEquals("ESCUDO", snapshot.board().get(1).effects().getFirst().kind());
        assertEquals(RoomSnapshotService.Slot.P2, snapshot.turn().player());
        assertEquals(40_000, snapshot.turn().startedAt());
        assertEquals(50_000, snapshot.turn().deadline());
        assertEquals(10, snapshot.turn().durationSeconds());
        assertEquals(3, snapshot.myDeck().size());
        assertTrue(snapshot.myDeck().stream().filter(deckSlot -> deckSlot.ability().equals("BOMBA"))
                .findFirst().orElseThrow().usedThisRound());
        assertFalse(snapshot.myDeck().stream().anyMatch(deckSlot -> deckSlot.source().equals("DRAWN")
                && deckSlot.ability().equals("BOMBA")));
    }

    @Test
    void syncPublishesOnlyAfterAuthenticatedParticipantIsValidated() {
        RoomFixture fixture = activeRoom(true);

        snapshots.sync("0042", fixture.host().getId());

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket, never()).sendToTopic(anyString(), any());
        verify(websocket).sendToUser(eq(fixture.host().getId().toString()), eq("/queue/room"), captor.capture());
        verify(websocket, never()).sendToUser(eq(fixture.guest().getId().toString()), anyString(), any());
        var envelope = (RoomSnapshotService.SnapshotEnvelope) captor.getValue();
        assertEquals("ROOM_SNAPSHOT", envelope.type());
        assertEquals(RoomSnapshotService.Slot.P1, envelope.payload().mySlot());
    }

    @Test
    void syncRejectsNonParticipantWithoutPublishing() {
        RoomFixture fixture = activeRoom(true);

        assertThrows(AccessDeniedException.class, () -> snapshots.sync("0042", UUID.randomUUID()));

        verifyNoInteractions(websocket, games, drafts, gameStates, playerService);
    }

    @Test
    void invalidOrMissingRoomIsRejectedWithoutPublishing() {
        assertThrows(IllegalArgumentException.class, () -> snapshots.sync("bad", UUID.randomUUID()));
        when(rooms.findByCodeAndStatusIn("0042", ACTIVE)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> snapshots.sync("0042", UUID.randomUUID()));
        verifyNoInteractions(websocket, games, drafts, gameStates, playerService);
    }

    private RoomFixture activeRoom(boolean waiting) {
        UUID roomId = UUID.randomUUID();
        User host = user("host");
        User guest = user("guest");
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getCode()).thenReturn("0042");
        when(room.getStatus()).thenReturn(waiting ? RoomStatus.WAITING : RoomStatus.IN_GAME);
        when(room.getPlayer1()).thenReturn(host);
        when(room.getPlayer2()).thenReturn(waiting ? null : guest);
        when(rooms.findByCodeAndStatusIn("0042", ACTIVE)).thenReturn(Optional.of(room));
        when(drafts.find(roomId)).thenReturn(Optional.empty());
        when(playerService.isConnected("0042", host.getId())).thenReturn(true);
        when(playerService.isConnected("0042", guest.getId())).thenReturn(false);
        return new RoomFixture(room, host, guest);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void persistedForfeitRestoresOutcomeAndThirtyZeroCoinsWithoutTheTransientRewardResult(boolean hostLoses) throws Exception {
        var fixture = activeRoom(false);
        UUID gameId = UUID.randomUUID();
        var game = new Game(fixture.room(), fixture.host());
        game.setPlayer2(fixture.guest());
        setId(game, gameId);
        game.forfeit(hostLoses ? fixture.host() : fixture.guest());
        when(games.findByRoomIdAndStatus(fixture.room().getId(), GameStatus.FINISHED)).thenReturn(Optional.of(game));

        assertNull(snapshots.compose("0042", fixture.host().getId()).coins());
        game.markRewardsGranted();
        var result = snapshots.compose("0042", fixture.host().getId());

        assertNull(game.getRewardResult());
        assertEquals(RoomSnapshotService.RoomPhase.RESULT, result.phase());
        assertEquals("WO", result.outcome().kind());
        assertEquals(hostLoses ? RoomSnapshotService.Slot.P2 : RoomSnapshotService.Slot.P1, result.outcome().winner());
        assertEquals(Map.of(RoomSnapshotService.Slot.P1, hostLoses ? 0 : 30,
                RoomSnapshotService.Slot.P2, hostLoses ? 30 : 0), result.coins());
        assertNull(result.turn());
    }

    @Test
    void reconnectRestoresOnlyTheViewersPendingOptionsAndPublicSnapshotHidesBothDraws() throws Exception {
        RoomFixture fixture = activeRoom(false);
        UUID p1 = fixture.host().getId(), p2 = fixture.guest().getId(), gameId = UUID.randomUUID();
        Game game = new Game(fixture.room(), fixture.host());
        game.setPlayer2(fixture.guest());
        setId(game, gameId);
        when(games.findByRoomIdAndStatus(fixture.room().getId(), GameStatus.IN_PROGRESS)).thenReturn(Optional.of(game));
        GameState state = GameState.builder().gameId(gameId).board(GameState.EMPTY_BOARD)
                .player1Id(p1).player2Id(p2).roundNumber(1).phase(GamePhase.DRAFT).build();
        state.setInitialSkills(p1, List.of("BOMBA", "ESCUDO"));
        state.setInitialSkills(p2, List.of("CONGELAR", "SILENCIO"));
        state.setRoundDraw(new RoundDrawState("0042", 1, 9_000, Map.of(
                p1, new RoundDrawState.Player(List.of("TORNADO", "SILENCIO", "CONGELAR"), null),
                p2, new RoundDrawState.Player(List.of("BOMBA", "ESCUDO", "AMPULHETA"), null))));
        when(gameStates.find(gameId)).thenReturn(Optional.of(state));

        var own = snapshots.compose("0042", p1);
        assertEquals(RoomSnapshotService.RoomPhase.ROUND_DRAW, own.phase());
        assertNull(own.turn());
        assertEquals(9_000, own.drawOptions().deadline());
        assertEquals(state.getRoundDraw().players().get(p1).options(), own.drawOptions().options());
        snapshots.publish("0042");
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToTopic(anyString(), captor.capture());
        var publicSnapshot = (RoomSnapshotService.SnapshotEnvelope) captor.getValue();
        assertNull(publicSnapshot.payload().drawOptions());
        assertTrue(publicSnapshot.payload().myDeck().isEmpty());
        state.setRoundDraw(state.getRoundDraw().select(p1, "TORNADO"));
        state.setRoundSkillDeck(p1, List.of("BOMBA", "ESCUDO", "TORNADO"));
        assertNull(snapshots.compose("0042", p1).drawOptions());
        assertNotNull(snapshots.compose("0042", p2).drawOptions());
        clearInvocations(websocket);

        snapshots.publish("0042");

        var privateCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(websocket).sendToUser(eq(p1.toString()), eq("/queue/room"), privateCaptor.capture());
        JsonNode ownJson = mapper.valueToTree(privateCaptor.getValue());
        assertEquals("ROUND_DRAW", ownJson.path("payload").path("phase").asText());
        assertTrue(ownJson.path("payload").has("drawOptions"));
        assertTrue(ownJson.path("payload").path("drawOptions").isNull());
        assertEquals(3, ownJson.path("payload").path("myDeck").size());
        assertEquals("P1", ownJson.path("payload").path("mySlot").asText());
        assertTrue(ownJson.path("payload").path("turn").isNull());
        assertEquals(ownJson.path("serverTime"), ownJson.path("payload").path("serverTime"));
        verify(websocket).sendToUser(eq(p2.toString()), eq("/queue/room"), privateCaptor.capture());
        JsonNode opponentJson = mapper.valueToTree(privateCaptor.getValue());
        assertEquals(9_000, opponentJson.path("payload").path("drawOptions").path("deadline").asLong());
        assertEquals(3, opponentJson.path("payload").path("drawOptions").path("options").size());
        verify(websocket).sendToTopic(anyString(), captor.capture());
        JsonNode publicJson = mapper.valueToTree(captor.getValue());
        assertTrue(publicJson.path("payload").path("drawOptions").isNull());
        assertTrue(publicJson.path("payload").path("mySlot").isNull());
        assertTrue(publicJson.path("payload").path("myDeck").isEmpty());
    }

    private User user(String username) {
        User user = new User(username, "private-password", username + "@example.com");
        user.setId(UUID.randomUUID());
        return user;
    }

    private void setId(Game game, UUID id) throws Exception {
        var field = Game.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(game, id);
    }

    private record RoomFixture(Room room, User host, User guest) { }
}
