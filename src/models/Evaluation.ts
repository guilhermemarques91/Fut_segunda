export interface PlayerScore {
  gameId: string; // ID do jogo ou evento
  playerId: string;
  score: number; // Nota de 1 a 10
  comment: string | null;
}

/**
 * Classe para gerenciar as avaliações e notas dos jogadores.
 */
export class EvaluationController {
  private static scores: Map<string, PlayerScore[]> = new Map();

  /**
   * Registra a nota de um jogador após um jogo.
   * @param gameId ID único do evento/jogo.
   * @param playerId ID do jogador avaliado.
   * @param score Nota dada (1-10).
   * @param comment Comentário adicional da avaliação.
   */
  public static recordScore(gameId: string, playerId: string, score: number, comment: string | null): PlayerScore {
    if (score < 1 || score > 10) {
      throw new Error("A nota deve estar entre 1 e 10.");
    }

    const newScore: PlayerScore = { gameId, playerId, score, comment };
    let scores = EvaluationController.scores.get(gameId) || [];
    scores.push(newScore);
    EvaluationController.scores.set(gameId, scores);
    return newScore;
  }

  /**
   * Obtém todas as notas de um jogador específico.
   */
  public static getPlayerScores(playerId: string): PlayerScore[] {
    let allScores: PlayerScore[] = [];
    for (const [gameId, scores] of EvaluationController.scores.entries()) {
      scores.filter(s => s.playerId === playerId).forEach(score => allScores.push(score));
    }
    return allScores;
  }
}