<?php
// Leitura por IA da lista repostada no grupo do WhatsApp.
//
// Divisão de responsabilidades, de propósito:
//   • a IA (Ollama local) SÓ segmenta o texto e diz quais emojis viu ao lado de cada nome;
//   • o casamento nome→player_id e a escolha do status são determinísticos, aqui em PHP.
// Modelo pequeno erra justamente em lookup de ID e em inventar status — então essas duas
// coisas ficam fora do alcance dele.

require_once __DIR__ . '/whatsapp.php';        // wa_cfg(), wa_load_appdata()
require_once __DIR__ . '/attendance_sync.php'; // syncAttendanceFromConfirmations()

// Lê constante de config sem quebrar quando ela não existe (config.php antigo).
function ai_cfg($name, $default = '') {
    return defined($name) ? constant($name) : $default;
}

function ai_configured() {
    return ai_cfg('AI_CONFIRM_ENABLED', true) && ai_cfg('OLLAMA_URL') && ai_cfg('OLLAMA_MODEL');
}

// ── FILTRO BARATO (sem IA) ───────────────────────────────────────────────
// Só vale a pena acordar o modelo se a mensagem tem cara de lista da pelada:
// várias linhas numeradas E ao menos um dos emojis de marcação.
function ai_looks_like_roster($text) {
    $text = (string)$text;
    if (strlen($text) < 40) return false;
    $marks = ['✅', '❌', '🥩', '✔️', '✔', '☑️', '☑', '❎', '✖️'];
    $hasMark = false;
    foreach ($marks as $m) { if (strpos($text, $m) !== false) { $hasMark = true; break; } }
    if (!$hasMark) return false;
    $numbered = 0;
    // Split byte-safe: \R casaria com 0x85, que é o 3º byte de "✅" (E2 9C 85),
    // e quebraria a linha no meio do emoji. O padrão sem /u é só ASCII de propósito —
    // texto malformado do WhatsApp não pode fazer o preg_match falhar calado.
    foreach (preg_split('/\r\n|\r|\n/', $text) as $line) {
        if (preg_match('/^\s*\d{1,2}\s*[\.\)\-]/', $line)) $numbered++;
    }
    return $numbered >= 5;
}

// ── NORMALIZAÇÃO DE NOME ─────────────────────────────────────────────────
// Sem mbstring (o projeto evita — ver comentário em wa_data_extenso).
function ai_fold_accents($s) {
    static $map = [
        'á'=>'a','à'=>'a','â'=>'a','ã'=>'a','ä'=>'a','å'=>'a',
        'é'=>'e','è'=>'e','ê'=>'e','ë'=>'e',
        'í'=>'i','ì'=>'i','î'=>'i','ï'=>'i',
        'ó'=>'o','ò'=>'o','ô'=>'o','õ'=>'o','ö'=>'o',
        'ú'=>'u','ù'=>'u','û'=>'u','ü'=>'u',
        'ç'=>'c','ñ'=>'n','ý'=>'y',
        'Á'=>'a','À'=>'a','Â'=>'a','Ã'=>'a','Ä'=>'a','Å'=>'a',
        'É'=>'e','È'=>'e','Ê'=>'e','Ë'=>'e',
        'Í'=>'i','Ì'=>'i','Î'=>'i','Ï'=>'i',
        'Ó'=>'o','Ò'=>'o','Ô'=>'o','Õ'=>'o','Ö'=>'o',
        'Ú'=>'u','Ù'=>'u','Û'=>'u','Ü'=>'u',
        'Ç'=>'c','Ñ'=>'n','Ý'=>'y',
    ];
    return strtr((string)$s, $map);
}

function ai_norm($s) {
    $s = strtolower(ai_fold_accents($s));
    $s = preg_replace('/[^a-z0-9 ]+/', ' ', $s);
    $s = preg_replace('/\s+/', ' ', $s);
    return trim($s);
}

// ── CASAMENTO NOME → JOGADOR ─────────────────────────────────────────────
// Índice de busca a partir de data.players (name + apelido + primeiro nome).
function ai_build_index($players) {
    $full = [];   // norm(name|apelido) -> [ids]
    $first = [];  // primeiro nome       -> [ids]
    foreach ($players as $p) {
        $id = (int)($p['id'] ?? 0);
        if (!$id) continue;
        foreach ([$p['name'] ?? '', $p['apelido'] ?? ''] as $cand) {
            $n = ai_norm($cand);
            if ($n === '') continue;
            $full[$n][] = $id;
            $tok = strtok($n, ' ');
            if ($tok !== false && strlen($tok) >= 3) $first[$tok][] = $id;
        }
    }
    // dedup
    foreach ($full  as $k => $v) $full[$k]  = array_values(array_unique($v));
    foreach ($first as $k => $v) $first[$k] = array_values(array_unique($v));
    return ['full' => $full, 'first' => $first];
}

// Devolve o player_id ou null (não achou / ambíguo).
function ai_match_player($raw, $index) {
    $n = ai_norm($raw);
    if ($n === '' || strlen($n) < 2) return null;

    // 1) nome ou apelido exato
    if (isset($index['full'][$n]) && count($index['full'][$n]) === 1) return $index['full'][$n][0];

    // 2) primeiro nome ("Gui" -> "Gui Marques"), só se não ambíguo
    if (isset($index['first'][$n]) && count($index['first'][$n]) === 1) return $index['first'][$n][0];

    // 3) o texto do grupo pode trazer sobrenome a mais/a menos ("Léo balada" vs "Leo Balada"):
    //    tenta pelo primeiro token do que foi escrito
    $tok = strtok($n, ' ');
    if ($tok !== false && strlen($tok) >= 3
        && isset($index['first'][$tok]) && count($index['first'][$tok]) === 1) {
        return $index['first'][$tok][0];
    }

    // 4) erro de digitação: levenshtein <= 2 contra os nomes completos, sem empate
    if (strlen($n) >= 4) {
        $best = null; $bestDist = 3; $ties = 0;
        foreach ($index['full'] as $key => $ids) {
            if (count($ids) !== 1) continue;
            if (abs(strlen($key) - strlen($n)) > 2) continue;
            $d = levenshtein($n, $key);
            if ($d < $bestDist) { $bestDist = $d; $best = $ids[0]; $ties = 1; }
            elseif ($d === $bestDist && $ids[0] !== $best) { $ties++; }
        }
        if ($best !== null && $ties === 1) return $best;
    }

    return null;
}

// ── MARCAS → STATUS ──────────────────────────────────────────────────────
// ✅ = joga · 🥩 = tira gosto · ❌ = não vai. Mapeamento fixo, sem IA no meio.
function ai_status_from_marks($check, $meat, $cross) {
    if ($cross && !$check) return 'no';
    if ($check && $meat)   return 'football_dinner';
    if ($check)            return 'football';
    if ($meat)             return 'dinner_only';
    return null; // nada marcado: mantém como está
}

// ── OLLAMA ───────────────────────────────────────────────────────────────
function ai_ollama_schema() {
    return [
        'type' => 'object',
        'required' => ['entries'],
        'properties' => [
            'entries' => [
                'type' => 'array',
                'items' => [
                    'type' => 'object',
                    'required' => ['name', 'check', 'meat', 'cross'],
                    'properties' => [
                        'name'  => ['type' => 'string'],
                        'check' => ['type' => 'boolean'],
                        'meat'  => ['type' => 'boolean'],
                        'cross' => ['type' => 'boolean'],
                    ],
                ],
            ],
        ],
    ];
}

function ai_ollama_system_prompt() {
    return <<<'TXT'
Você extrai a lista de presença de uma pelada de futebol a partir de uma mensagem de WhatsApp.

Para CADA pessoa citada na mensagem, devolva um objeto com:
  name  = o nome da pessoa exatamente como aparece escrito (sem número, sem emoji)
  check = true se houver ✅ (ou ✔ / ☑ / "vou") ao lado do nome
  meat  = true se houver 🥩 ao lado do nome
  cross = true se houver ❌ (ou ✖ / ❎) ao lado do nome

REGRAS IMPORTANTES:
1. Uma linha pode conter DUAS pessoas — quando alguém sai e outro entra no lugar.
   Nesse caso devolva DOIS objetos, cada um com os seus próprios emojis.
2. Inclua também os nomes das seções de suplentes/reservas, que não são numeradas.
3. Não invente nomes que não estão na mensagem e não corrija a grafia.
4. Ignore títulos, datas, separadores, legendas e o link de confirmação.
5. Cabeçalho de seção NÃO é pessoa: "MENSALISTAS", "Suplentes", "Reservas",
   "AVULSOS" e parecidos nunca viram objeto.

EXEMPLOS
Entrada: "1. Felipe Campovilla✅🥩"
Saída:   [{"name":"Felipe Campovilla","check":true,"meat":true,"cross":false}]

Entrada: "2. Tiburcio ❌"
Saída:   [{"name":"Tiburcio","check":false,"meat":false,"cross":true}]

Entrada: "3. Caê Sâmia ❌ Samuel ✅🥩"
Saída:   [{"name":"Caê Sâmia","check":false,"meat":false,"cross":true},
          {"name":"Samuel","check":true,"meat":true,"cross":false}]

Entrada: "14. Neck ❌Léo balada ✅"
Saída:   [{"name":"Neck","check":false,"meat":false,"cross":true},
          {"name":"Léo balada","check":true,"meat":false,"cross":false}]

Entrada: "Raphael 🥩"
Saída:   [{"name":"Raphael","check":false,"meat":true,"cross":false}]
TXT;
}

// Chama o Ollama e devolve ['ok'=>bool, 'entries'=>[...], 'err'=>string].
function ai_ollama_extract($text) {
    if (!ai_configured()) return ['ok' => false, 'err' => 'not_configured'];

    $url = rtrim(ai_cfg('OLLAMA_URL'), '/') . '/api/chat';
    $body = [
        'model'    => ai_cfg('OLLAMA_MODEL'),
        'stream'   => false,
        'format'   => ai_ollama_schema(),
        'options'  => ['temperature' => 0, 'num_ctx' => 8192],
        'messages' => [
            ['role' => 'system', 'content' => ai_ollama_system_prompt()],
            ['role' => 'user',   'content' => $text],
        ],
    ];
    // Medido no gemma4:12b: 204s com thinking, 44s sem, com resultado idêntico.
    // Segmentar linha e ver emoji não precisa de raciocínio — só custa tempo de cron.
    if (!ai_cfg('AI_THINK', false)) $body['think'] = false;
    $payload = json_encode($body, JSON_UNESCAPED_UNICODE);

    // O Ollama fica atrás do Cloudflare Access — autenticação é por service token.
    $headers = ['Content-Type: application/json'];
    if (ai_cfg('CF_ACCESS_CLIENT_ID') && ai_cfg('CF_ACCESS_CLIENT_SECRET')) {
        $headers[] = 'CF-Access-Client-Id: '     . ai_cfg('CF_ACCESS_CLIENT_ID');
        $headers[] = 'CF-Access-Client-Secret: ' . ai_cfg('CF_ACCESS_CLIENT_SECRET');
    }

    $post = function ($payload) use ($url, $headers) {
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_POST           => true,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => (int) ai_cfg('OLLAMA_TIMEOUT', 600),
            CURLOPT_HTTPHEADER     => $headers,
            CURLOPT_POSTFIELDS     => $payload,
        ]);
        $r = ['res' => curl_exec($ch), 'code' => curl_getinfo($ch, CURLINFO_HTTP_CODE), 'err' => curl_error($ch)];
        curl_close($ch);
        return $r;
    };

    $out  = $post($payload);
    $res  = $out['res'];
    $code = $out['code'];
    $err  = $out['err'];

    // Modelo sem suporte a thinking recusa o parâmetro — tenta de novo sem ele.
    if ($code >= 400 && stripos((string)$res, 'think') !== false && isset($body['think'])) {
        unset($body['think']);
        $out  = $post(json_encode($body, JSON_UNESCAPED_UNICODE));
        $res  = $out['res'];
        $code = $out['code'];
        $err  = $out['err'];
    }

    if ($code < 200 || $code >= 300) {
        return ['ok' => false, 'err' => 'ollama http ' . $code . ($err ? " ($err)" : '')];
    }
    // O Access devolve a página de login com HTTP 200 — sem esta checagem o erro
    // apareceria como "JSON inválido do modelo", que manda debugar no lugar errado.
    if (stripos($res, 'Cloudflare Access') !== false || stripos($res, '<!doctype html') !== false) {
        return ['ok' => false, 'err' => 'bloqueado pelo Cloudflare Access — confira CF_ACCESS_CLIENT_ID/SECRET'];
    }
    $body = json_decode($res, true);
    $content = $body['message']['content'] ?? '';
    if ($content === '') return ['ok' => false, 'err' => 'resposta vazia do modelo'];

    $parsed = json_decode($content, true);
    if (!is_array($parsed)) {
        // modelo embrulhou o JSON em prosa — tenta pegar o primeiro objeto
        if (preg_match('/\{.*\}/s', $content, $m)) $parsed = json_decode($m[0], true);
    }
    if (!is_array($parsed) || !isset($parsed['entries']) || !is_array($parsed['entries'])) {
        return ['ok' => false, 'err' => 'JSON inválido do modelo'];
    }
    return ['ok' => true, 'entries' => $parsed['entries']];
}

// ── RODADA ALVO ──────────────────────────────────────────────────────────
// A rodada aberta mais próxima; sem nenhuma no banco, a próxima segunda-feira.
function ai_target_rodada($pdo) {
    $d = $pdo->query('SELECT MIN(rodada_date) FROM presence_confirmations WHERE rodada_date >= CURDATE()')
             ->fetchColumn();
    if ($d) return $d;
    $next = new DateTime('today');
    $w = (int)$next->format('w');            // 0=domingo, 1=segunda
    if ($w !== 1) $next->modify('next monday');
    return $next->format('Y-m-d');
}

// Token da rodada (para inserir linha de quem não recebeu link). Gera um se não houver.
function ai_rodada_token($pdo, $date) {
    $stmt = $pdo->prepare('SELECT token FROM presence_confirmations WHERE rodada_date = ? LIMIT 1');
    $stmt->execute([$date]);
    $tk = $stmt->fetchColumn();
    if ($tk) return $tk;
    // mesmo alfabeto de generate_tokens (api.php) — sem caracteres ambíguos
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789';
    $token = '';
    $bytes = random_bytes(12);
    for ($i = 0; $i < 12; $i++) $token .= $alphabet[ord($bytes[$i]) % strlen($alphabet)];
    return $token;
}

// ── MONTAGEM DA PROPOSTA ─────────────────────────────────────────────────
// entries (da IA) + estado atual do banco  ->  ['items' => [...], 'unmatched' => [...]]
// items só contém MUDANÇA de verdade: a lista tem 16 nomes, mas em geral 1-2 mudaram.
function ai_build_proposal($pdo, $entries, $date) {
    $app     = wa_load_appdata($pdo);
    $players = $app['players'];
    $index   = ai_build_index($players);
    $nameById = [];
    foreach ($players as $p) { $nameById[(int)$p['id']] = $p['name'] ?? ''; }

    $stmt = $pdo->prepare('SELECT player_id, status FROM presence_confirmations WHERE rodada_date = ?');
    $stmt->execute([$date]);
    $current = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $r) { $current[(int)$r['player_id']] = $r['status']; }

    $items = [];
    $unmatched = [];
    $seen = [];

    foreach ($entries as $e) {
        $raw   = trim((string)($e['name'] ?? ''));
        $check = !empty($e['check']);
        $meat  = !empty($e['meat']);
        $cross = !empty($e['cross']);
        if ($raw === '') continue;

        $to = ai_status_from_marks($check, $meat, $cross);
        if ($to === null) continue; // ninguém marcou nada: deixa pendente

        $id = ai_match_player($raw, $index);
        if (!$id) {
            $unmatched[] = ['raw' => $raw, 'status' => $to, 'reason' => 'nome não reconhecido'];
            continue;
        }
        if (isset($seen[$id])) {
            // mesmo jogador citado duas vezes na mensagem com resultados diferentes
            if ($seen[$id] !== $to) {
                $unmatched[] = ['raw' => $raw, 'status' => $to,
                                'reason' => 'citado duas vezes (mantive "' . $seen[$id] . '")'];
            }
            continue;
        }
        $seen[$id] = $to;

        $from = $current[$id] ?? 'pending';
        if ($from === $to) continue; // no-op

        $items[] = [
            'player_id'   => $id,
            'player_name' => $nameById[$id] ?? $raw,
            'raw'         => $raw,
            'from_status' => $from,
            'to_status'   => $to,
        ];
    }

    return ['items' => $items, 'unmatched' => $unmatched];
}

// ── APLICAÇÃO (o único caminho de escrita) ───────────────────────────────
// $onlyIds = subconjunto de player_id que o admin deixou marcado (null = todos).
function ai_apply_proposal($pdo, $proposalId, $decidedBy, $onlyIds = null) {
    $stmt = $pdo->prepare('SELECT * FROM ai_confirm_proposals WHERE id = ?');
    $stmt->execute([$proposalId]);
    $prop = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$prop) return ['ok' => false, 'err' => 'proposta não encontrada'];
    if ($prop['status'] !== 'pending') return ['ok' => false, 'err' => 'proposta já foi ' . $prop['status']];

    $date  = $prop['rodada_date'];
    $items = json_decode($prop['items'], true) ?: [];
    if ($onlyIds !== null) {
        $keep  = array_flip(array_map('intval', $onlyIds));
        $items = array_values(array_filter($items, fn($it) => isset($keep[(int)$it['player_id']])));
    }

    $token = ai_rodada_token($pdo, $date);
    $sel = $pdo->prepare('SELECT id FROM presence_confirmations WHERE rodada_date = ? AND player_id = ? LIMIT 1');
    $upd = $pdo->prepare('UPDATE presence_confirmations SET status = ?, confirmed_at = NOW() WHERE id = ?');
    $ins = $pdo->prepare('INSERT INTO presence_confirmations
                          (rodada_date, token, phone, player_id, player_name, status, confirmed_at)
                          VALUES (?, ?, NULL, ?, ?, ?, NOW())');

    $applied = 0;
    foreach ($items as $it) {
        $pid = (string)(int)$it['player_id'];
        $to  = $it['to_status'];
        if (!in_array($to, ['football', 'football_dinner', 'dinner_only', 'no'], true)) continue;
        // SELECT antes do UPDATE: rowCount() do MySQL é 0 quando o valor não muda,
        // o que faria a gente inserir uma linha duplicada.
        $sel->execute([$date, $pid]);
        $rowId = $sel->fetchColumn();
        if ($rowId) $upd->execute([$to, $rowId]);
        else        $ins->execute([$date, $token, $pid, (string)($it['player_name'] ?? ''), $to]);
        $applied++;
    }

    // mesma importação do link público: presence_confirmations -> app_data
    syncAttendanceFromConfirmations($pdo, $date);

    $pdo->prepare("UPDATE ai_confirm_proposals SET status = 'approved', decided_at = NOW(), decided_by = ? WHERE id = ?")
        ->execute([$decidedBy, $proposalId]);

    return ['ok' => true, 'applied' => $applied, 'date' => $date];
}
