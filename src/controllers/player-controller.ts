import { Player } from '../models/Player';

/**
 * Classe responsável por gerenciar a lógica de jogadores.
 */
export class PlayerController {
  private static players: Map<string, Player> = new Map();

  /**
   * Registra um novo jogador no sistema.
   * @param name Nome completo do jogador.
   * @param position Posição em campo (Atacante, Zagueiro, Meia).
   * @param attributes Atributos físicos, técnicos e táticos.
   * @returns O objeto Player recém-criado.
   */
  public static registerPlayer(name: string, position: string, attributes: { physical: number; technical: number; tactical: number }): Player {
    // Verifica se o nome já existe para evitar duplicidade (simplificação)
    for (const player of PlayerController.getAllPlayers()) {
      if (player.name === name) {
        throw new Error(`Jogador com o nome ${name} já está cadastrado.`);
      }
    }

    const id = Date.now().toString(); // ID simples baseado no timestamp
    const newPlayer: Player = new Player(id, name, position, attributes);
    PlayerController.players.set(id, newPlayer);
    return newPlayer;
  }

  /**
   * Retorna todos os jogadores cadastrados e ativos.
   * @returns Array de objetos Player.
   */
  public static getAllPlayers(): Player[] {
    // Filtra apenas os jogadores que não estão inativos
    return Array.from(PlayerController.players.values()).filter(p => !p.isActive);
  }

  /**
   * Desativa um jogador pelo seu ID, simulando a exclusão do time.
   * @param id O ID do jogador a ser desativado.
   * @returns Boolean indicando sucesso da operação.
   */
  public static deactivatePlayer(id: string): boolean {
    const player = PlayerController.players.get(id);
    if (player) {
      player.isActive = false;
      return true;
    }
    return false;
  }

  /**
   * Ativa um jogador pelo seu ID, simulando o retorno ao time.
   * @param id O ID do jogador a ser ativado.
   * @returns Boolean indicando sucesso da operação.
   */
  public static activatePlayer(id: string): boolean {
    const player = PlayerController.players.get(id);
    if (player) {
      player.isActive = true;
      return true;
    }
    return false;
  }

  /**
   * Busca um jogador pelo nome e retorna o objeto Player, se existir.
   * @param name Nome do jogador a ser buscado.
   * @returns O objeto Player ou null se não encontrado.
   */
  public static findPlayerByName(name: string): Player | null {
    for (const player of PlayerController.players.values()) {
      if (player.name.toLowerCase() === name.toLowerCase()) {
        return player;
      }
    }
    return null;
  }
}