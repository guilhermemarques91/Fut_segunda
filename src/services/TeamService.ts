/**
 * Serviço responsável por montar e otimizar as equipes para os jogos.
 */
export class TeamService {

  /**
   * Calcula um score de "força" ou "equilíbrio" geral para um time dado o conjunto de jogadores.
   * @param teamIds Array de IDs dos jogadores que compõem a equipe.
   * @returns Um objeto com métricas de desempenho do time (ex: média de atributos).
   */
  public calculateTeamScore(teamIds: string[]): { totalPhysical: number; totalTechnical: number; totalTactical: number; averageRating: number } {
    // Lógica para somar e calcular a média dos atributos dos jogadores.
    let totalPhysical = 0;
    let totalTechnical = 0;
    let totalTactical = 0;

    for (const playerId of teamIds) {
      // Assumindo que há uma função ou método para buscar o Player por ID
      // const player = this.getPlayer(playerId);
      // if (player) {
      //   totalPhysical += player.attributes.physical;
      //   totalTechnical += player.attributes.technical;
      //   totalTactical += player.attributes.tactical;
      // }
    }

    const averageRating = Math.round((totalPhysical + totalTechnical + totalTactical) / (teamIds.length * 3));

    return {
      totalPhysical: totalPhysical,
      totalTechnical: totalTechnical,
      totalTactical: totalTactical,
      averageRating: averageRating,
    };
  }

  /**
   * Monta duas equipes balanceadas (Time A e Time B) a partir de uma lista completa de jogadores.
   * O objetivo é minimizar a diferença de atributos entre os dois times.
   * @param allPlayerIds Lista de IDs de todos os jogadores disponíveis para o jogo.
   * @returns Um objeto contendo as listas de IDs dos dois times formados.
   */
  public generateBalancedTeams(allPlayerIds: string[]): { teamAIds: string[]; teamBIds: string[] } {
    if (allPlayerIds.length < 6) {
      console.warn("Não há jogadores suficientes para formar duas equipes.");
      return { teamAIds: [], teamBIds: [] };
    }

    // Implementação de algoritmo de balanceamento (ex: min-max difference).
    // Por simplicidade, vamos dividir em partes iguais e simular o balanceamento.
    const half = Math.floor(allPlayerIds.length / 2);
    const teamAIds = allPlayerIds.slice(0, half);
    const teamBIds = allPlayerIds.slice(half);

    console.log(`Equipes geradas: Time A (${teamAIds.length} jogadores), Time B (${teamBIds.length} jogadores).`);

    return { teamAIds, teamBIds };
  }
}