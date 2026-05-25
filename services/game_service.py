from app.models.database import Player, Match, Attendance, PerformanceRating, FinancialTransaction, DinnerFund, Contribution
from sqlalchemy.orm import sessionmaker
from datetime import date
from typing import List, Dict

# Inicializa a sessão de banco de dados (assumindo que o motor principal fará isso)
SessionLocal = sessionmaker()

class GameService:
    """
    Serviço central para gerenciar a lógica de jogo, finanças e resultados.
    """

    @staticmethod
    def generate_team(available_players: List[Player], team_size: int = 11) -> Dict[str, List[Player]]:
        """
        Gera duas equipes balanceadas (Casa e Fora) baseando-se nos atributos dos jogadores.
        A lógica deve tentar equilibrar o nível de habilidade geral entre os times.
        """
        if len(available_players) < team_size * 2:
            return {"Home": [], "Away": []}

        # 1. Ordena os jogadores por um atributo principal (ex: skill_level) para facilitar a distribuição
        sorted_players = sorted(available_players, key=lambda p: p.skill_level, reverse=True)
        
        home_team = []
        away_team = []

        # 2. Distribuição alternada para balanceamento inicial (mais forte vai para o time que precisa de reforço)
        for i in range(min(len(sorted_players), team_size * 2)):
            player = sorted_players[i]
            if i % 2 == 0:
                home_team.append(player) # Jogador mais forte vai para o time A (Home)
            else:
                away_team.append(player) # Jogador seguinte vai para o time B (Away)

        # Ajuste fino para garantir que ambos os times tenham tamanho igual e usem todos os jogadores disponíveis até o limite
        home_team = home_team[:team_size]
        away_team = away_team[:team_size]
        
        return {"Home": home_team, "Away": away_team}

    @staticmethod
    def record_match_result(match: Match, winner_id: int, loser_id: int):
        """
        Registra o resultado do jogo e atualiza a tabela de resultados semanais.
        Esta função deve ser chamada após um jogo para registrar quem venceu/perdeu.
        """
        # Lógica para atualizar a Tabela de Resultados Semanais (pode ser uma nova tabela ou apenas em logs)
        print(f"Resultado registrado: {winner_id} venceu contra {loser_id} no dia {match.date}.")
        # Aqui seria adicionada lógica para persistir o resultado na DB

    @staticmethod
    def calculate_financials(session, player_ids: List[int], match_id: int = None):
        """
        Calcula e registra todas as transações financeiras necessárias (mensalidades e jogos avulsos).
        """
        # 1. Mensalidade dos Titulares Regulares
        regular_players = session.query(Player).filter(Player.is_regular == True).all()
        for player in regular_players:
            if match_id is None: # Assumindo que mensalidades são registradas fora de um jogo específico
                # Lógica para registrar a cobrança da mensalidade (se não foi paga)
                pass 

        # 2. Pagamento por Jogo Avulso (para jogadores não regulares ou convidados)
        # Esta lógica precisaria saber se o jogador participou do jogo e se há uma taxa de participação.
        pass


    @staticmethod
    def manage_dinner_fund(session, match_id: int, confirmed_player_ids: List[int], total_cost: float):
        """
        Gerencia o fundo 'Trira Gosto'. Cria ou atualiza o registro do fundo e calcula a contribuição.
        """
        # 1. Verifica se já existe um fundo para este match_id
        fund = session.query(DinnerFund).filter_by(match_id=match_id).first()

        if not fund:
            fund = DinnerFund(match_id=match_id, total_cost=total_cost)
            session.add(fund)
            session.flush() # Garante que o ID do fundo seja gerado

        # 2. Atualiza a lista de participantes (se necessário)
        # fund.attendees = ",".join(map(str, confirmed_player_ids)) # Exemplo de atualização

        # 3. Calcula e registra as contribuições para cada participante confirmado
        contribution_amount = round(total_cost / len(confirmed_player_ids), 2) if confirmed_player_ids else 0.0

        for player_id in confirmed_player_ids:
            # Verifica se o jogador já contribuiu neste fundo (para evitar duplicidade)
            contribution = session.query(Contribution).filter_by(fund_id=fund.id, player_id=player_id).first()
            if not contribution:
                new_contribution = Contribution(fund_id=fund.id, player_id=player_id, amount=contribution_amount)
                session.add(new_contribution)

        session.commit()
        print("Fundo 'Trira Gosto' gerenciado com sucesso.")