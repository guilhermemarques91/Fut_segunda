import { PlayerService } from '../services/PlayerService';
import { Player } from '../models/Player';

/**
 * Controller responsável por orquestrar as operações de gerenciamento de jogadores.
 */
export class PlayerController {

  /**
   * Registra um novo jogador no sistema.
   * @param name Nome completo do jogador.
   * @param position Posição em campo.
   * @param attributes Atributos físicos, técnicos e táticos (1-10).
   * @returns O objeto Player criado.
   */
  public static registerPlayer(name: string, position: 'Goleiro' | 'Zagueiro' | 'Lateral' | 'Volante' | 'Meia' | 'Atacante', attributes: { physical: number; technical: number; tactical: number }): Player => {
    // Validação básica de atributos (poderia ser mais robusta)
    if (attributes.physical < 1 || attributes.technical < 1 || attributes.tactical < 1) {
      throw new Error("Atributos devem ter um valor mínimo de 1.");
    }
    return PlayerService.addPlayer({ name, position, attributes });
  };

  /**
   * Busca todos os jogadores ativos no sistema.
   * @returns Array de objetos Player.
   */
  public static getAllPlayers(): Player[] {
    return PlayerService.getAllActivePlayers();
  }

  /**
   * Atualiza os atributos ou status de um jogador existente.
   * @param id O ID do jogador a ser atualizado.
   * @param updates Objeto contendo os campos a serem atualizados (ex: { attributes: {...}, isActive: boolean }).
   * @returns O objeto Player atualizado, ou null se o ID não for encontrado.
   */
  public static updatePlayerDetails(id: string, updates: Partial<Omit<Player, 'id' | 'isActive'>>): Player | null {
    // Nota: A lógica de atualização deve ser mais granular para permitir atualizar apenas atributos sem passar todo o objeto.
    // Por simplicidade, vamos assumir que os updates contêm todos os dados necessários ou um atributo específico.
    return PlayerService.updatePlayer(id, updates);
  }

  /**
   * Desativa um jogador do sistema (soft delete).
   * @param id O ID do jogador a ser desativado.
   * @returns true se o jogador foi encontrado e desativado, false caso contrário.
   */
  public static deactivatePlayer(id: string): boolean {
    return PlayerService.deactivatePlayer(id);
  }
}