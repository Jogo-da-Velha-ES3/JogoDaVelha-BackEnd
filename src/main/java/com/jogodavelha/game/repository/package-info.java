/**
 * Repository layer - Data access layer for PostgreSQL (JPA) and Redis.
 * 
 * This package contains:
 * - JPA repositories for persistent data (users, games, history)
 * - Redis access components for temporary/fast data (game state, turns, locks)
 * 
 * Design decision: All data access is centralized in this package to avoid
 * spreading persistence concerns across domain/service layers.
 */
package com.jogodavelha.game.repository;
