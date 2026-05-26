-- Execute este SQL no phpMyAdmin do Hostgator
-- Tabela de confirmações de presença via link WhatsApp

CREATE TABLE IF NOT EXISTS presence_confirmations (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  rodada_date  DATE NOT NULL,
  token        CHAR(12) NOT NULL,
  phone        CHAR(11) NOT NULL,
  player_id    VARCHAR(64) NOT NULL,
  player_name  VARCHAR(100) NOT NULL,
  status       ENUM('pending','football','football_dinner','dinner_only','no') NOT NULL DEFAULT 'pending',
  confirmed_at DATETIME NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_token (token),
  UNIQUE KEY uq_rodada_phone (rodada_date, phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
