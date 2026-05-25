import { PlayerModel } from '../models/PlayerModel';
import { TeamFormationService } from './TeamFormationService';
import { FinanceService } from './FinanceService';
import { TriraGostoService } from './TriraGostoService';

/**
 * Serviço principal que orquestra todas as funcionalidades do aplicativo de gestão de futebol.
 */
export class GameManagerService {
  private players: PlayerModel[] = [];
  private teamFormationService: TeamFormationService;
  private financeService: FinanceService;
  private triraGostoService: TriraGostoService;

  constructor() {
    this.teamFormationService = new TeamFormationService();
    this.financeService = new FinanceService();
    this.triraGostoService = new TriraGostoService();
  }

  /**
   * Inicializa o sistema com uma lista de jogadores.
   * @param players Lista de objetos PlayerModel.
   */
  public initializePlayers(players: PlayerModel[]): void {
    this.players = players;
  }

  // --- Funcionalidade 1: Formação de Time ---

  /**
   * Realiza a formação automática do time baseada nos atributos dos jogadores disponíveis.
   * @param numPlayers O número de jogadores que devem compor o time (ex: 11).
   * @returns Um array de PlayerModel representando o time formado.
   */
  public formTeam(numPlayers: number): PlayerModel[] {
    if (this.players.length < numPlayers) {
      throw new Error(`Não há jogadores suficientes para formar um time de ${numPlayers}.`);
    }
    return this.teamFormationService.formTeam(this.players, numPlayers);
  }

  // --- Funcionalidade 2: Controle Financeiro ---

  /**
   * Registra a presença e o status financeiro dos jogadores para um jogo específico.
   * @param presenceList Lista de IDs/Nomes presentes no jogo.
   * @param isTitularMap Mapa de IDs/Nomes que são titulares mensais (true) ou não (false).
   */
  public recordGameAttendance(presenceList: string[], isTitularMap: Map<string, boolean>): void {
    this.financeService.recordAttendance(presenceList);
    // Lógica para atualizar o status de titularidade mensal pode ser adicionada aqui se necessário
  }

  /**
   * Registra um pagamento financeiro (ex: taxa do jogo ou valor avulso).
   * @param playerId ID do jogador pagante.
   * @param amount Valor pago.
   * @param type Tipo de cobrança ('Mensal', 'JogoAvulso').
   */
  public recordPayment(playerId: string, amount: number, type: 'Mensal' | 'JogoAvulso'): void {
    this.financeService.recordPayment(playerId, amount, type);
  }

  // --- Funcionalidade 3: Trira Gosto (Divisão de Despesas) ---

  /**
   * Inicia o cálculo da divisão de despesas para um evento específico (ex: jantar).
   * @param totalCost O custo total do evento.
   */
  public startTriraGosto(totalCost: number): void {
    this.triraGostoService.reset();
    this.triraGostoService.setTotalCost(totalCost);
  }

  /**
   * Adiciona um participante à lista de quem deve dividir a despesa.
   * @param identifier ID ou nome do participante.
   */
  public addTriraGostoAttendee(identifier: string): void {
    this.triraGostoService.addAttendee(identifier);
  }

  /**
   * Calcula e retorna o valor que cada participante deve pagar na divisão de despesas.
   * @returns O valor da cota ou null se não houver cálculo possível.
   */
  public calculateTriraGostoShare(): number | null {
    return this.triraGostoService.calculateShare();
  }

  // --- Funcionalidade 4: Relatórios e Status ---

  /**
   * Gera um relatório consolidado do status financeiro de todos os jogadores.
   */
  public generateFinancialReport(): void {
    console.log("--- RELATÓRIO FINANCEIRO GERAL ---");
    this.financeService.displayPlayerBalances(this.players);
  }

  /**
   * Gera um relatório consolidado do time e dos atributos.
   */
  public displayTeamStatus(): void {
    console.log("\n--- STATUS DO TIME E ATRIBUTOS ---");
    this.players.forEach(player => {
      console.log(`[${player.name}] Score Geral: ${player.getOverallScore()} | Posição: ${player.position}`);
    });
  }
}