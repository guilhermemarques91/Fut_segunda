/**
 * Serviço responsável por gerenciar despesas compartilhadas, como o jantar de confraternização.
 */
export class TriraGostoService {
  private totalCost: number = 0;
  private attendees: Set<string> = new Set(); // Armazena IDs ou nomes únicos dos participantes

  /**
   * Define o custo total da despesa (ex: conta do restaurante).
   * @param cost O valor total a ser dividido.
   */
  public setTotalCost(cost: number): void {
    if (cost < 0) {
      throw new Error("O custo não pode ser negativo.");
    }
    this.totalCost = cost;
  }

  /**
   * Adiciona um participante à lista de quem deve pagar.
   * @param identifier Um identificador único do participante (ex: nome ou ID).
   */
  public addAttendee(identifier: string): void {
    if (!identifier) {
      throw new Error("O identificador do participante não pode ser vazio.");
    }
    this.attendees.add(identifier);
  }

  /**
   * Limpa o estado do serviço para um novo cálculo de despesa.
   */
  public reset(): void {
    this.totalCost = 0;
    this.attendees.clear();
  }

  /**
   * Calcula a divisão da conta entre os participantes registrados.
   * @returns O valor que cada participante deve pagar, ou null se não houver custos ou participantes.
   */
  public calculateShare(): number | null {
    const count = this.attendees.size;
    if (count === 0) {
      return null; // Ninguém para dividir a conta
    }

    if (this.totalCost <= 0) {
      return 0; // Sem custo, ninguém deve pagar
    }

    // Calcula o valor exato que cada um deve pagar
    const share = this.totalCost / count;
    return parseFloat(share.toFixed(2)); // Retorna com duas casas decimais para moeda
  }

  /**
   * Obtém a lista atual de participantes.
   * @returns Um array de strings contendo os identificadores dos participantes.
   */
  public getAttendeesList(): string[] {
    return Array.from(this.attendees);
  }
}