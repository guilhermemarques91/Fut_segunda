from sqlalchemy import Column, Integer, String, Float, Date, ForeignKey, Boolean
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class Player(Base):
    __tablename__ = "players"
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String, index=True, nullable=False)
    position = Column(String, nullable=False) # Ex: Atacante, Zagueiro
    monthly_fee = Column(Float, default=0.0) # Valor mensalidade padrão (R$)
    is_regular = Column(Boolean, default=True) # Titular regular ou avulso

class Match(Base):
    __tablename__ = "matches"
    id = Column(Integer, primary_key=True, index=True)
    date = Column(Date, nullable=False)
    opponent = Column(String, nullable=False)
    location = Column(String)

class Attendance(Base):
    __tablename__ = "attendance"
    id = Column(Integer, primary_key=True, index=True)
    player_id = Column(Integer, ForeignKey("players.id"))
    match_id = Column(Integer, ForeignKey("matches.id"))
    is_present = Column(Boolean, default=False) # True se confirmou presença
    # Adicionado para rastrear a fonte da confirmação (WhatsApp, Manual, etc.)
    source = Column(String) 

class PerformanceRating(Base):
    __tablename__ = "performance_ratings"
    id = Column(Integer, primary_key=True, index=True)
    player_id = Column(Integer, ForeignKey("players.id"))
    match_id = Column(Integer, ForeignKey("matches.id"))
    rating = Column(Float) # Nota de 1 a 10
    notes = Column(String)

class FinancialTransaction(Base):
    __tablename__ = "financial_transactions"
    id = Column(Integer, primary_key=True, index=True)
    player_id = Column(Integer, ForeignKey("players.id"))
    match_id = Column(Integer, ForeignKey("matches.id"), nullable=True) # Pode ser nulo se for mensalidade geral
    amount = Column(Float, nullable=False)
    type = Column(String, nullable=False) # Ex: Mensalidade, Jogo Avulso, Contribuição Trira Gosto
    date_paid = Column(Date, default=None)

class DinnerFund(Base):
    __tablename__ = "dinner_funds"
    id = Column(Integer, primary_key=True, index=True)
    match_id = Column(Integer, ForeignKey("matches.id"), unique=True) # Fundos são por evento/jogo
    total_cost = Column(Float, default=0.0)
    attendees = Column(String) # Lista de IDs ou nomes dos participantes confirmados

class Contribution(Base):
    __tablename__ = "contributions"
    id = Column(Integer, primary_key=True, index=True)
    fund_id = Column(Integer, ForeignKey("dinner_funds.id"))
    player_id = Column(Integer, ForeignKey("players.id"))
    amount = Column(Float, nullable=False)

# Relacionamentos (Opcional, mas útil para o ORM)
Player.attendance = relationship("Attendance", back_populates="player")
Match.attendances = relationship("Attendance", back_populates="match")