/**
 * Modelo de dados para representar um jogador do time.
 */
export interface Player {
  id: string;
  name: string;
  isRegularPlayer: boolean; // Titular que paga mensalmente
  attributes: {
    physical: number; // 1-10
    technical: number; // 1-10
    tactical: number; // 1-10
  };
}

/**
 * Modelo de dados para o registro de presença em uma partida.
 */
export interface AttendanceRecord {
  matchId: string;
  date: Date;
  presentPlayerIds: string[];
  adminAddedAttendees: string[]; // IDs adicionados manualmente pelo admin
}

/**
 * Modelo de dados para um registro financeiro (pagamento).
 */
export interface FinancialRecord {
  id: string;
  playerId: string;
  date: Date;
  description: string; // Ex: Mensalidade Maio, Jantar 01/05
  amountPaid: number; // Valor pago pelo jogador
}

/**
 * Modelo de dados para o resultado de uma partida.
 */
export interface MatchResult {
  matchId: string;
  date: Date;
  homeTeamIds: string[];
  awayTeamIds: string[];
  scoreHome: number;
  scoreAway: number;
}