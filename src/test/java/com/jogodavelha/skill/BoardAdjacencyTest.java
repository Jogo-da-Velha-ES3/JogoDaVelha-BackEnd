package com.jogodavelha.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardAdjacencyTest {
    @Test
    void recognizesHorizontalVerticalAndDiagonalNeighbors() {
        assertTrue(BoardAdjacency.isAdjacent(5, 4));
        assertTrue(BoardAdjacency.isAdjacent(5, 6));
        assertTrue(BoardAdjacency.isAdjacent(5, 1));
        assertTrue(BoardAdjacency.isAdjacent(5, 9));
        assertTrue(BoardAdjacency.isAdjacent(5, 0));
        assertTrue(BoardAdjacency.isAdjacent(5, 2));
        assertTrue(BoardAdjacency.isAdjacent(5, 8));
        assertTrue(BoardAdjacency.isAdjacent(5, 10));
        assertTrue(BoardAdjacency.isAdjacent(0, 5));
        assertTrue(BoardAdjacency.isAdjacent(3, 6));
        assertTrue(BoardAdjacency.isAdjacent(12, 9));
    }

    @Test
    void rejectsNonNeighborsInvalidIndicesAndIndexWraparound() {
        assertFalse(BoardAdjacency.isAdjacent(5, 7));
        assertFalse(BoardAdjacency.isAdjacent(5, 15));
        assertFalse(BoardAdjacency.isAdjacent(3, 4));
        assertFalse(BoardAdjacency.isAdjacent(4, 3));
        assertFalse(BoardAdjacency.isAdjacent(0, 2));
        assertFalse(BoardAdjacency.isAdjacent(15, 12));
        assertFalse(BoardAdjacency.isAdjacent(5, 5));
        assertFalse(BoardAdjacency.isAdjacent(-1, 0));
        assertFalse(BoardAdjacency.isAdjacent(0, 16));
    }
}
