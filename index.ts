import { GameManagerService } from './services/GameManagerService';
import { PlayerModel } from './models/PlayerModel';

/**
 * Função principal de demonstração do fluxo de uso do sistema de gestão.
 */
async function main() {
  console.log("=============================================");
  console.log("🚀 INICIANDO SIMULAÇÃO DO SISTEMA DE FUTEBOL ⚽");
  console.log("=============================================");

  // 1. Cadastro e Inicialização dos Jogadores (Simulação de Dados)
  const playersData = [
    { id: 'p001', name: 'João Silva', position: 'Atacante', attributes: { defensive: 3, midfield: 7, offensive: 9, speed: 8, passing: 6 } },
    { id: 'p002', name: 'Pedro Costa', position: 'Meia', attributes: { defensive: 6, midfield: 8, offensive: 7, speed: 7, passing: 9 } },
    { id: 'p003', name: 'Lucas Souza', position: 'Zagueiro', attributes: { defensive: 9, midfield: 5, offensive: 4, speed: 6, passing: 5 } },
    { id: 'p004', name: 'Rafael Lima', position: 'Lateral', attributes: { defensive: 7, midfield: 6, offensive: 5, speed: 8, passing: 6 } },
    { id: 'p005', name: 'Bruno Melo', position: 'Goleiro', attributes: { defensive: 9, midfield: 3, offensive: 2, speed: 4, passing: 3 } },
    // Adicionando mais jogadores para testes de formação e finanças
    { id: 'p006', name: 'Carlos Dias', position: 'Atacante', attributes: { defensive: 2, midfield: 5, offensive: 8, speed: 7, passing: 5 } },
    { id: 'p007', name: 'Thiago Reis', position: 'Volante', attributes: { defensive: 8, midfield: 9, offensive: 4, speed: 6, passing: 7 } },
  ];

  const players = playersData.map(data => PlayerModel.create(
    data.id, data.name, data.position, data.attributes
  ));

  // Inicializa o serviço principal
  const gameManager = new GameManagerService();
  gameManager.initializePlayers(players);

  console.log("\n✅ Jogadores cadastrados com sucesso.");

  // 2. Formação de Time (Exemplo: time de 11 jogadores - simulando que temos mais)
  try {
    const team = gameManager.formTeam(5); // Tentando formar um time de 5 para o exemplo
    console.log("\n✅ Time formado com sucesso!");
    team.forEach(player => console.log(`   - ${player.name} (${player.position})`));
  } catch (error) {
    console.error(`\n❌ Erro ao formar time: ${(error as Error).message}`);
  }

  // 3. Simulação de Jogo e Avaliação
  const presentPlayers = ['p001', 'p002', 'p003', 'p004', 'p005']; // Quem compareceu
  const isTitularMap = new Map([
    ['p001', true], // João é titular mensal
    ['p002', false],
    ['p003', true], // Lucas é titular mensal
    ['p004', true], // Rafael é titular mensal
    ['p005', true]  // Bruno é titular mensal
  ]);

  gameManager.recordGameAttendance(presentPlayers, isTitularMap);
  console.log("\n✅ Presença e status de titularidade registrados.");


  // 4. Controle Financeiro (Pagamentos)
  // Simula o pagamento da taxa do jogo por quem compareceu
  const gameFee = 50; // Valor fixo da taxa do jogo
  presentPlayers.forEach(id => {
    gameManager.recordPayment(id, gameFee, 'JogoAvulso');
  });

  // Simula o pagamento mensal de um jogador avulso que foi titular no mês passado (ex: p006)
  const monthlyFee = 150;
  gameManager.recordPayment('p006', monthlyFee, 'Mensal');
  console.log("\n✅ Pagamentos registrados (Taxa do jogo e mensalidades).");

  // 5. Trira Gosto (Divisão de Despesas)
  const totalDinnerCost = 300; // Custo total da janta
  gameManager.startTriraGosto(totalDinnerCost);

  // Adiciona quem confirmou presença e o administrador manualmente
  gameManager.addTriraGostoAttendee('p001'); // João (Presente)
  gameManager.addTriraGostoAttendee('p002'); // Pedro (Presente)
  gameManager.addTriraGostoAttendee('admin_user'); // Administrador manual

  const share = gameManager.calculateTriraGostoShare();
  console.log(`\n✅ Divisão de despesas (Jantar): Custo total R$${totalDinnerCost.toFixed(2)}. Cada pessoa deve pagar: R$${share ? share.toFixed(2) : 'N/A'}.`);

  // 6. Relatórios Finais
  gameManager.generateFinancialReport();
}

main().catch(err => {
  console.error("\nERRO FATAL NA EXECUÇÃO:", err);
});