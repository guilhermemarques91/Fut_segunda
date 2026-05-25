
/**
 * Define o status do jogo.
 */
export enum MatchStatus {
  Scheduled = 'AGENDADO',
  Completed = 'COMPLETADO',
}

/**
 * Representa um jogo ou partida da equipe.
 */
export interface TeamMatch {
  id: string;
  date: Date;
  opponentName: string;
  status: MatchStatus;
  // Resultados do jogo (ex: 3x1)
  score: { home: number; away: number };
  // Jogadores que participaram desta partida
  participants: string[]; // Array de IDs de jogadores
}

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
 * Representa a lista de presença para um evento específico.
 */
export interface AttendanceRecord {
  matchId: string;
  date: Date;
  // Lista de IDs dos jogadores que confirmaram presença
  presentPlayerIds: string[]; 
  // Administradores adicionados manualmente (para custos como jantar)
  adminAddedAttendees: string[]; 
}

/**
 * Estrutura para o controle financeiro geral.
 */
export interface FinancialRecord {
  matchId: string; // Pode ser nulo se for pagamento mensal/geral
  date: Date;
  description: string; // Ex: "Mensalidade de Maio", "Jantar do dia X"
  amountPaid: number;
  paidByPlayerIds: string[]; // IDs dos pagadores
}
