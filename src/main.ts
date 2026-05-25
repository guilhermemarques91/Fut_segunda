import { PlayerController } from './controllers/player-controller';

/**
 * Simula o fluxo principal do aplicativo de gerenciamento de futebol.
 */
const runAppInterface = async () => {
  console.log("=============================================");
  console.log("⚽️ Gerenciador de Futebol - Início da Sessão ⚽️");
  console.log("=============================================");

  // --- 1. Cadastro Inicial de Jogadores (Simulação) ---
  console.log("\n[PASSO 1] Cadastrando jogadores iniciais...");

  try {
    const player1 = PlayerController.registerPlayer(
      "João Silva", "Atacante", { physical: 8, technical: 9, tactical: 7 }
    );
    console.log(`✅ Cadastro realizado: ${player1.name} (ID: ${player1.id})`);

    const player2 = PlayerController.registerPlayer(
      "Pedro Costa", "Zagueiro", { physical: 8, technical: 7, tactical: 9 }
    );
    console.log(`✅ Cadastro realizado: ${player2.name} (ID: ${player2.id})`);

    const player3 = PlayerController.registerPlayer(
      "Lucas Mendes", "Meia", { physical: 6, technical: 8, tactical: 9 }
    );
    console.log(`✅ Cadastro realizado: ${player3.name} (ID: ${player3.id})`);

  } catch (error) {
    console.error("Erro ao cadastrar jogadores:", error);
  }


  // --- 2. Visualização da Lista de Jogadores Ativos ---
  console.log("\n[PASSO 2] Listando todos os jogadores ativos:");
  const players = PlayerController.getAllPlayers();

  if (players.length === 0) {
    console.log("Nenhum jogador ativo encontrado.");
  } else {
    players.forEach(p => {
      console.log(`- ${p.name} | Posição: ${p.position}`);
      console.log(`  Atributos: Físico=${p.attributes.physical}, Técnico=${p.attributes.technical}, Tático=${p.attributes.tactical}`);
    });
  }

  // --- 3. Simulação de Funcionalidades Futuras (Placeholder) ---
  console.log("\n[PASSO 3] Próximos Módulos:");
  console.log("---------------------------------------------");
  console.log("✅ Avaliação e Notas: Implementar lógica para registrar notas após jogos.");
  console.log("✅ Montador de Equipe Automática: Usará os atributos dos jogadores para sugerir times.");
  console.log("✅ Controle Financeiro: Módulo separado para gerenciar pagamentos (mensal/por jogo).");
  console.log("✅ Controle de Jantar: Lógica para dividir custos com base na presença confirmada.");

  // Exemplo de desativação (simulação)
  if (players.length > 0) {
    const idToDeactivate = players[0].id; // Desativando o primeiro jogador cadastrado
    console.log(`\n[SIMULAÇÃO] Desativando ${players[0].name} (ID: ${idToDeactivate})...`);
    const success = PlayerController.deactivatePlayer(idToDeactivate);
    if (success) {
      console.log("✅ Jogador desativado com sucesso.");
    } else {
      console.log("❌ Falha ao desativar o jogador.");
    }
  }

  console.log("\n=============================================");
  console.log("✨ Interface de Gerenciamento carregada com sucesso! ✨");
};

// Executa a simulação da interface
runAppInterface();