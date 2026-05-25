# ⚽ Futebol de Segunda - Gerenciamento App

## 🚀 Visão Geral do Projeto
Este aplicativo foi desenvolvido para gerenciar todas as operações e aspectos sociais, financeiros e esportivos da nossa equipe de futebol de segunda-feira. O objetivo é centralizar a comunicação, o controle financeiro e a organização dos jogos, garantindo que todos os membros estejam na mesma página.

## ✨ Funcionalidades Principais

### 📅 Controle de Presença
*   **Confirmação:** Sistema para confirmar presença dos jogadores.
*   **Integração WhatsApp:** Recebimento automático ou integração com confirmações via WhatsApp.

### ⭐ Avaliação e Desempenho do Jogador
*   **Notas e Avaliações:** Permite que os administradores (ou usuários) avaliem o desempenho de cada jogador após o jogo, atribuindo notas e comentários construtivos.
*   **Histórico:** Manutenção de um histórico detalhado do desempenho individual.

### 🤖 Motor de Equipe Automática
*   **Seleção Inteligente:** Um algoritmo que monta automaticamente as equipes para os jogos, baseando-se nos atributos (posição preferencial, nível de jogo, etc.) e no desempenho recente dos jogadores.
*   **Equilíbrio:** Busca manter o equilíbrio entre as equipes em cada rodada.

### 📊 Talea de Resultados Semanais
*   **Registro de Jogos:** Cadastro completo dos resultados de cada partida.
*   **Estatísticas:** Exibição de estatísticas semanais e acumuladas (gols marcados, gols sofridos, etc.).

### 💰 Controle Financeiro
Este módulo gerencia todas as transações financeiras da equipe:
1.  **Titulares Mensais:** Cadastro e cobrança de valores mensais fixos para jogadores titulares.
2.  **Jogadores Avulsos:** Registro de pagamentos por jogo para jogadores que participam esporadicamente.

### 🍻 Controle de Trípago (Jantar)
*   **Divisão Automática:** Calcula automaticamente o valor a ser pago por cada participante na noite do jogo, dividindo os custos da janta entre todos os membros que confirmaram presença.
*   **Administração Manual:** Permite ao administrador adicionar manualmente jogadores ou ajustar valores quando necessário.

## 🛠️ Como Configurar e Usar (Guia do Administrador)

### Pré-requisitos
Certifique-se de ter o ambiente de desenvolvimento configurado com as dependências listadas no `package.json` (ou equivalente).

### 1. Cadastro Inicial
*   **Jogadores:** Adicione todos os jogadores, preenchendo atributos essenciais (Nome, Posição Principal, Nível de Jogo, etc.).
*   **Financeiro:** Defina a lista de titulares e seus valores mensais.

### 2. Fluxo Semanal do Jogo
1.  **Presença:** Use o módulo de presença para registrar quem irá jogar (idealmente integrado com WhatsApp).
2.  **Motor de Equipe:** Execute o motor automático para gerar as equipes do jogo da semana.
3.  **Jogo e Avaliação:** Após o jogo, preencha a tabela de resultados e avalie cada jogador individualmente.
4.  **Financeiro/Trípago:** Registre os pagamentos (mensais ou por jogo) e calcule o valor do trípago com base na lista de presença confirmada.

## 🚀 Próximos Passos
*   [ ] Implementar a integração completa com WhatsApp para confirmação de presença.
*   [ ] Testar e refinar os algoritmos de balanceamento das equipes.
*   [ ] Adicionar funcionalidades extras (Ex: Galeria de fotos, calendário de jogos).

---
*Desenvolvido por [Seu Nome/Equipe]*