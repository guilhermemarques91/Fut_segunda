/**
 * Representa a avaliação e nota de um jogador em uma partida específica.
 */
export interface PlayerRating {
  playerId: string;
  matchId: string;
  rating: number; // Nota geral (ex: 1 a 10)
  // Atributos específicos avaliados no jogo
  physicalPerformance: number; // Ex: Energia, resistência demonstrada
  technicalPerformance: number; // Ex: Qualidade de passe/drible em campo
  tacticalContribution: number; // Ex: Posicionamento tático e visão de jogo
}

/**
 * Serviço responsável por calcular e gerenciar as avaliações dos jogadores.
 */
export class RatingService {
  /**
   * Calcula a nota geral do jogador com base nos atributos observados em um jogo.
   * @param playerId ID do jogador.
   * @param matchId ID da partida.
   * @param physicalPerformance Nota de performance física (1-10).
   * @param technicalPerformance Nota de performance técnica (1-10).
   * @param tacticalContribution Nota de contribuição tática (1-10).
   * @returns Um objeto PlayerRating completo.
   */
  public calculatePlayerRating(playerId: string, matchId: string, physicalPerformance: number, technicalPerformance: number, tacticalContribution: number): PlayerRating {
    // Lógica de cálculo da nota geral (ex: média ponderada)
    const rating = Math.round((physicalPerformance + technicalPerformance + tacticalContribution) / 3);

    return {
      playerId: playerId,
      matchId: matchId,
      rating: rating,
      physicalPerformance: physicalPerformance,
      technicalPerformance: technicalPerformance,
      tacticalContribution: tacticalContribution,
    };
  }
}
