from fastapi import APIRouter

router = APIRouter(prefix="/v1")

# Rotas de Jogador
@router.get("/players/")
def get_all_players():
    """Retorna a lista completa de jogadores."""
    pass

@router.post("/players/add")
def add_player(name: str, position: str, skill_level: float):
    """Adiciona um novo jogador ao sistema."""
    pass

# Rotas de Partida e Presença
@router.get("/matches/")
def get_all_matches():
    """Retorna a lista de partidas registradas."""
    pass

@router.post("/matches/attendance")
def record_attendance(match_id: int, player_ids: list[int], sources: dict):
    """Registra a presença dos jogadores para uma partida específica."""
    # 'sources' deve ser um dicionário {player_id: source}
    pass

# Rotas de Avaliação e Formação de Equipe
@router.post("/matches/{match_id}/rating")
def submit_rating(match_id: int, player_id: int, rating: float, notes: str):
    """Submete a avaliação de um jogador após uma partida."""
    pass

# Rotas Financeiras e Trira Gosto
@router.post("/finance/transaction")
def record_financial_transaction(player_id: int, amount: float, type: str, match_id: int = None):
    """Registra qualquer transação financeira (mensalidade, jogo avulso)."""
    pass

@router.post("/dinner-fund/contribute")
def contribute_to_dinner_fund(match_id: int, player_id: int, amount: float):
    """Registra a contribuição para o fundo de jantar."""
    pass