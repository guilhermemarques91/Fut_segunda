/**
 * Serviço responsável por gerenciar todas as transações financeiras do time.
 */
export class FinanceService {
  // Estrutura para armazenar pagamentos mensais (titulares)
  private monthlyPayments: Map<string, number> = new Map(); // Key: playerId, Value: valor pago

  // Estrutura para armazenar pagamentos por jogo (avulsos/participantes)
  private gamePayments: Map<string, { totalPaid: number; count: number }> = new Map(); // Key: playerId, Value: {totalPaid, count}

  /**
   * Registra o pagamento mensal de um jogador titular.
   * @param playerId ID do jogador.
   * @param value Valor pago neste mês.
   */
  public recordMonthlyPayment(playerId: string, value: number): void {
    if (value <= 0) {
      throw new Error("O valor deve ser positivo.");
    }
    this.monthlyPayments.set(playerId, this.monthlyPayments.get(playerId) || 0 + value);
  }

  /**
   * Registra um pagamento por jogo para um jogador (avulso ou titular).
   * @param playerId ID do jogador.
   * @param value Valor pago pelo jogo.
   */
  public recordGamePayment(playerId: string, value: number): void {
    if (value <= 0) {
      throw new Error("O valor deve ser positivo.");
    }

    const current = this.gamePayments.get(playerId) || { totalPaid: 0, count: 0 };
    this.gamePayments.set(playerId, {
      totalPaid: current.totalPaid + value,
      count: current.count + 1
    });
  }

  /**
   * Calcula o saldo devedor mensal para um jogador (simplificado).
   * @param playerId ID do jogador.
   * @returns O valor total pago até agora ou 0 se não houver registro.
   */
  public getMonthlyBalance(playerId: string): number {
    return this.monthlyPayments.get(playerId) || 0;
  }

  /**
   * Obtém o histórico de pagamentos por jogo para um jogador.
   * @param playerId ID do jogador.
   * @returns Objeto com total pago e número de jogos registrados.
   */
  public getGamePaymentHistory(playerId: string): { totalPaid: number; count: number } | null {
    return this.gamePayments.get(playerId) || null;
  }

  /**
   * Limpa todos os registros financeiros (útil para início de um novo ciclo).
   */
  public resetRecords(): void {
    this.monthlyPayments.clear();
    this.gamePayments.clear();
  }
}