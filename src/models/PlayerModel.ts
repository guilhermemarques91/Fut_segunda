/**
 * Interface que define o modelo de dados para um jogador do time.
 */
export interface Player {
  id: string; // Identificador único do jogador (ex: CPF, matrícula)
  name: string; // Nome completo do jogador
  position: 'Goleiro' | 'Zagueiro' | 'Lateral' | 'Volante' | 'Meia' | 'Atacante'; // Posição principal
  attributes: {
    defensive: number; // Atributo defensivo (0-10)
    midfield: number;  // Atributo de meio-campo (0-10)
    offensive: number; // Atributo ofensivo (0-10)
    speed: number;     // Velocidade (0-10)
    passing: number;   // Passe/Visão de jogo (0-10)
  };

  /**
   * Calcula um score geral do jogador baseado em seus atributos.
   * @returns Um número representando o nível geral do jogador.
   */
  getOverallScore(): number;
}

/**
 * Classe utilitária para criar e gerenciar instâncias de jogadores.
 */
export class PlayerModel {
  constructor(public id: string, public name: string, public position: 'Goleiro' | 'Zagueiro' | 'Lateral' | 'Volante' | 'Meia' | 'Atacante', public attributes: { defensive: number; midfield: number; offensive: number; speed: number; passing: number; }) {}

  /**
   * Calcula o score geral do jogador.
   * @returns O score calculado.
   */
  public getOverallScore(): number {
    // Exemplo de cálculo ponderado (pode ser ajustado)
    return Math.round(
      (this.attributes.defensive * 0.25 +
       this.attributes.midfield * 0.3 +
       this.attributes.offensive * 0.25 +
       this.attributes.speed * 0.1 +
       this.attributes.passing * 0.1)
    );
  }

  /**
   * Cria uma instância de Player a partir de dados brutos.
   * @param id ID do jogador.
   * @param name Nome do jogador.
   * @param position Posição.
   * @param attributes Atributos numéricos.
   * @returns Uma nova instância de PlayerModel.
   */
  public static create(id: string, name: string, position: 'Goleiro' | 'Zagueiro' | 'Lateral' | 'Volante' | 'Meia' | 'Atacante', attributes: { defensive: number; midfield: number; offensive: number; speed: number; passing: number; }): PlayerModel {
    return new PlayerModel(id, name, position, attributes);
  }
}