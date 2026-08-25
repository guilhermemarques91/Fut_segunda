-- Execute este SQL no phpMyAdmin do Hostgator (uma vez).
-- Tabelas da leitura por IA da lista repostada no grupo do WhatsApp.
-- Alternativa: rodar api/auth_setup.php, que faz o mesmo — MAS ele tambem
-- redefine as senhas de 'gui' e 'admin' e apaga todas as sessoes.

-- Mensagens cruas recebidas pelo webhook da Evolution (rastro/depuracao).
CREATE TABLE IF NOT EXISTS wa_inbox (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  wa_msg_id   VARCHAR(96)  NOT NULL,
  chat_jid    VARCHAR(64)  NOT NULL,
  sender_name VARCHAR(100) NULL,
  body        TEXT         NOT NULL,
  status      ENUM('new','skipped','parsed','error') NOT NULL DEFAULT 'new',
  parse_error VARCHAR(255) NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  parsed_at   DATETIME NULL,
  UNIQUE KEY uq_msg (wa_msg_id),
  INDEX idx_status (status, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Propostas de mudanca que a IA montou, aguardando aprovacao do admin.
CREATE TABLE IF NOT EXISTS ai_confirm_proposals (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  inbox_id    INT  NOT NULL,
  rodada_date DATE NOT NULL,
  status      ENUM('pending','approved','rejected','superseded') NOT NULL DEFAULT 'pending',
  model       VARCHAR(64)  NULL,
  items       LONGTEXT NOT NULL,
  unmatched   LONGTEXT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at  DATETIME NULL,
  decided_by  VARCHAR(64) NULL,
  INDEX idx_status (status, rodada_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A IA precisa confirmar quem NAO tem WhatsApp cadastrado (o suplente que entra
-- no lugar de alguem). Esses nunca receberam link, entao a linha nasce sem telefone.
-- No MySQL o indice UNIQUE aceita varios NULL, entao uq_rodada_phone continua valendo.
ALTER TABLE presence_confirmations MODIFY phone CHAR(11) NULL;
