
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
 * Representa o registro financeiro de um pagamento.
 */
export interface FinancialRecord {
  matchId?: string; // Opcional, pode ser nulo se for pagamento mensal/geral
  date: Date;
  description: string; // Ex: "Mensalidade de Maio", "Jantar do dia X"
  amountPaid: number;
  paidByPlayerIds: string[]; // IDs dos pagadores
}
