import { Player } from '../models/Player';

// Mock database for demonstration purposes
let players: Player[] = [];

/**
 * Adiciona um novo jogador ao sistema.
 * @param player Dados do jogador a ser adicionado.
 * @returns O objeto Player criado.
 */
export const addPlayer = (player: Omit<Player, 'id' | 'isActive'>): Player => {
  const newPlayer: Player = {
    ...player,
    id: Date.now().toString(), // Simple unique ID generation
    isActive: true,
  };
  players.push(newPlayer);
  return newPlayer;
};

/**
 * Busca um jogador pelo ID.
 * @param id O ID do jogador.
 * @returns O objeto Player ou undefined se não encontrado.
 */
export const getPlayerById = (id: string): Player | undefined => {
  return players.find(p => p.id === id);
};

/**
 * Busca todos os jogadores ativos no sistema.
 * @returns Array de objetos Player.
 */
export const getAllActivePlayers = (): Player[] => {
  return players.filter(p => p.isActive);
};

/**
 * Atualiza os dados de um jogador existente.
 * @param id O ID do jogador a ser atualizado.
 * @param updates Os novos dados (parcialmente).
 * @returns O objeto Player atualizado ou undefined se não encontrado.
 */
export const updatePlayer = (id: string, updates: Partial<Omit<Player, 'id' | 'isActive'>>): Player | undefined => {
  const playerIndex = players.findIndex(p => p.id === id);
  if (playerIndex === -1) return undefined;

  // Atualiza os atributos e outros campos sem tocar no ID ou status de atividade
  players[playerIndex] = {
    ...players[playerIndex],
    ...updates,
    id: players[playerIndex].id, // Garante que o ID não seja sobrescrito
    isActive: updates.hasOwnProperty('isActive') ? updates.isActive : players[playerIndex].isActive,
  };

  return players[playerIndex];
};

/**
 * Desativa um jogador (soft delete).
 * @param id O ID do jogador a ser desativado.
 * @returns true se o jogador foi encontrado e desativado, false caso contrário.
 */
export const deactivatePlayer = (id: string): boolean => {
  const player = getPlayerById(id);
  if (!player) return false;

  // Atualiza apenas o status de atividade
  return updatePlayer(id, { isActive: false }) !== undefined;
};