<?php
// Helpers de envio para o WhatsApp via Evolution API + montagem da mensagem de
// confirmados (porta fiel de buildConfirmadosMsg() do frontend/index.html).
// IMPORTANTE: se o formato/emoji mudar no app, espelhar aqui (e vice-versa).

// Lê uma constante de config sem quebrar quando ela não existe (config.php antigo).
function wa_cfg($name, $default = '') {
    return defined($name) ? constant($name) : $default;
}

function wa_configured() {
    return wa_cfg('EVOLUTION_URL') && wa_cfg('EVOLUTION_INSTANCE')
        && wa_cfg('EVOLUTION_APIKEY') && wa_cfg('EVOLUTION_GROUP_JID');
}

// Emojis por status — manter igual ao EMOJI de buildConfirmadosMsg (index.html)
function wa_emoji($st) {
    $m = [
        'football'        => '✅',
        'football_dinner' => '✅🥩',
        'dinner_only'     => '🥩',
        'no'              => '❌',
        'pending'         => '⏳',
    ];
    return $m[$st] ?? '⏳';
}

// Data por extenso em pt-BR: "Segunda-feira, 09 de junho"
function wa_data_extenso($ymd) {
    $dias  = ['domingo','segunda-feira','terça-feira','quarta-feira','quinta-feira','sexta-feira','sábado'];
    $meses = ['','janeiro','fevereiro','março','abril','maio','junho','julho','agosto','setembro','outubro','novembro','dezembro'];
    try {
        $d = new DateTime($ymd . 'T12:00:00');
    } catch (Exception $e) {
        return $ymd;
    }
    $w   = (int)$d->format('w');           // 0=domingo
    $dia = $d->format('d');                 // 2 dígitos
    $mes = (int)$d->format('n');            // 1..12
    $txt = $dias[$w] . ', ' . $dia . ' de ' . $meses[$mes];
    return ucfirst($txt); // 1ª letra do dia é sempre ASCII (s/t/q/d) — sem depender de mbstring
}

// Carrega players + avulsoOrder + attendances do app_data (id=1)
function wa_load_appdata($pdo) {
    $row = $pdo->query('SELECT data FROM app_data WHERE id = 1')->fetch(PDO::FETCH_ASSOC);
    $d = $row ? (json_decode($row['data'], true) ?? []) : [];
    return [
        'players'     => $d['players']     ?? [],
        'avulsoOrder' => $d['avulsoOrder'] ?? [],
        'attendances' => $d['attendances'] ?? [],
    ];
}

// Ordem dos avulsos: ordem configurada primeiro; resto por nº de presenças desc.
function wa_ordered_avulsos($players, $avulsoOrder, $attCount) {
    $avulsosAll = array_values(array_filter($players, fn($p) => empty($p['isRegular'])));
    $byId = [];
    foreach ($avulsosAll as $p) { $byId[$p['id']] = $p; }
    $inOrder = [];
    $usedIds = [];
    foreach (($avulsoOrder ?: []) as $id) {
        if (isset($byId[$id])) { $inOrder[] = $byId[$id]; $usedIds[$id] = true; }
    }
    $rest = array_values(array_filter($avulsosAll, fn($p) => empty($usedIds[$p['id']])));
    usort($rest, fn($a, $b) => ($attCount[$b['id']] ?? 0) <=> ($attCount[$a['id']] ?? 0));
    return array_merge($inOrder, $rest);
}

// Mensagem de confirmados (espelha buildConfirmadosMsg do app).
function wa_build_confirmados_msg($pdo, $rodadaDate) {
    $app = wa_load_appdata($pdo);
    $players = $app['players'];

    // contagem de presenças por jogador (para ordenar avulsos sem ordem fixa)
    $attCount = [];
    foreach ($app['attendances'] as $a) {
        foreach (($a['players'] ?? []) as $pid) { $attCount[$pid] = ($attCount[$pid] ?? 0) + 1; }
    }

    // status de confirmação por player_id
    $stmt = $pdo->prepare('SELECT player_id, status FROM presence_confirmations WHERE rodada_date = ?');
    $stmt->execute([$rodadaDate]);
    $statusBy = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $r) {
        $statusBy[(int)$r['player_id']] = $r['status'];
    }
    $statusFor = fn($id) => $statusBy[$id] ?? 'pending';

    // mensalistas: goleiros (A→Z) depois linha (A→Z)
    $mensAll = array_values(array_filter($players, fn($p) => !empty($p['isRegular'])));
    $gks   = array_values(array_filter($mensAll, fn($p) => ($p['position'] ?? '') === 'Goleiro'));
    $linha = array_values(array_filter($mensAll, fn($p) => ($p['position'] ?? '') !== 'Goleiro'));
    usort($gks,   fn($a, $b) => strcmp($a['name'] ?? '', $b['name'] ?? ''));
    usort($linha, fn($a, $b) => strcmp($a['name'] ?? '', $b['name'] ?? ''));
    $mensalistas = array_merge($gks, $linha);

    $avulsos = wa_ordered_avulsos($players, $app['avulsoOrder'], $attCount);

    $isPlaying   = fn($st) => $st === 'football' || $st === 'football_dinner';
    $isConfirmed = fn($st) => $st === 'football' || $st === 'football_dinner' || $st === 'dinner_only';

    // avulsos confirmados, na ordem; fila de substitutos = os que jogam
    $confirmedAvulsos = [];
    foreach ($avulsos as $p) {
        $st = $statusFor($p['id']);
        if ($isConfirmed($st)) $confirmedAvulsos[] = ['p' => $p, 'st' => $st];
    }
    $subPool = array_values(array_filter($confirmedAvulsos, fn($x) => $isPlaying($x['st'])));
    $usedSubs = [];

    $hr = '━━━━━━━━━━━━━━━━━━━━';
    $lines = ['⚽ *PELADA DE SEGUNDA*', '📅 ' . wa_data_extenso($rodadaDate), '', $hr, '📋 *MENSALISTAS*', ''];

    foreach ($mensalistas as $i => $p) {
        $gk = (($p['position'] ?? '') === 'Goleiro') ? ' 🧤' : '';
        $st = $statusFor($p['id']);
        $n  = $i + 1;
        if ($st === 'no') {
            $sub = null;
            foreach ($subPool as $cand) {
                if (empty($usedSubs[$cand['p']['id']])) { $sub = $cand['p']; break; }
            }
            if ($sub) {
                $usedSubs[$sub['id']] = true;
                $lines[] = "{$n}. {$p['name']}{$gk} ❌ → {$sub['name']} ✅";
            } else {
                $lines[] = "{$n}. {$p['name']}{$gk} ❌";
            }
        } else {
            $lines[] = "{$n}. {$p['name']}{$gk} " . wa_emoji($st);
        }
    }

    $extra = array_values(array_filter($confirmedAvulsos, fn($x) => empty($usedSubs[$x['p']['id']])));
    if (count($extra)) {
        $lines[] = '';
        $lines[] = '💵 *AVULSOS*';
        $lines[] = '';
        foreach ($extra as $i => $x) {
            $lines[] = ($i + 1) . ". {$x['p']['name']} " . wa_emoji($x['st']);
        }
    }

    $lines[] = '';
    $lines[] = '_✅ joga · 🥩 janta · ❌ não vai · ⏳ aguardando_';
    $lines[] = $hr;
    foreach (wa_confirm_link_lines($pdo, $rodadaDate) as $l) $lines[] = $l;
    return implode("\n", $lines);
}

// Bloco do link de confirmação (rodapé) — token da rodada + SITE_URL.
// Retorna [] se não houver token ou SITE_URL não estiver configurado.
function wa_confirm_link_lines($pdo, $rodadaDate) {
    $tkStmt = $pdo->prepare('SELECT token FROM presence_confirmations WHERE rodada_date = ? LIMIT 1');
    $tkStmt->execute([$rodadaDate]);
    $tk = $tkStmt->fetchColumn();
    if (!$tk || !wa_cfg('SITE_URL')) return [];
    return [
        '',
        '🔗 *Confirme sua presença pelo link:*',
        rtrim(wa_cfg('SITE_URL'), '/') . '/confirmar.php?t=' . $tk,
    ];
}

// Envia um texto para o grupo configurado via Evolution API (sendText v2).
function wa_send_group_text($text) {
    if (!wa_configured()) return ['ok' => false, 'err' => 'not_configured'];
    $url = rtrim(wa_cfg('EVOLUTION_URL'), '/') . '/message/sendText/' . rawurlencode(wa_cfg('EVOLUTION_INSTANCE'));
    $payload = json_encode(['number' => wa_cfg('EVOLUTION_GROUP_JID'), 'text' => $text], JSON_UNESCAPED_UNICODE);
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 8,
        CURLOPT_HTTPHEADER     => ['Content-Type: application/json', 'apikey: ' . wa_cfg('EVOLUTION_APIKEY')],
        CURLOPT_POSTFIELDS     => $payload,
    ]);
    $res  = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    return ['ok' => ($code >= 200 && $code < 300), 'code' => $code, 'err' => $err, 'res' => $res];
}

// Registra o webhook de mensagens recebidas na instância (Evolution v2).
// Só MESSAGES_UPSERT: é o único evento que interessa p/ ler a lista do grupo.
function wa_set_webhook($hookUrl) {
    if (!wa_cfg('EVOLUTION_URL') || !wa_cfg('EVOLUTION_INSTANCE') || !wa_cfg('EVOLUTION_APIKEY')) {
        return ['ok' => false, 'err' => 'not_configured'];
    }
    $url = rtrim(wa_cfg('EVOLUTION_URL'), '/') . '/webhook/set/' . rawurlencode(wa_cfg('EVOLUTION_INSTANCE'));
    $payload = json_encode(['webhook' => [
        'enabled'        => true,
        'url'            => $hookUrl,
        'events'         => ['MESSAGES_UPSERT'],
    ]], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST           => true,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
        CURLOPT_HTTPHEADER     => ['Content-Type: application/json', 'apikey: ' . wa_cfg('EVOLUTION_APIKEY')],
        CURLOPT_POSTFIELDS     => $payload,
    ]);
    $res  = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    return ['ok' => ($code >= 200 && $code < 300), 'code' => $code, 'err' => $err, 'url' => $hookUrl, 'res' => $res];
}

// Lista os grupos da instância (para descobrir o JID na configuração).
function wa_list_groups() {
    if (!wa_cfg('EVOLUTION_URL') || !wa_cfg('EVOLUTION_INSTANCE') || !wa_cfg('EVOLUTION_APIKEY')) {
        return ['ok' => false, 'err' => 'not_configured'];
    }
    $url = rtrim(wa_cfg('EVOLUTION_URL'), '/') . '/group/fetchAllGroups/' . rawurlencode(wa_cfg('EVOLUTION_INSTANCE')) . '?getParticipants=false';
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT        => 10,
        CURLOPT_HTTPHEADER     => ['apikey: ' . wa_cfg('EVOLUTION_APIKEY')],
    ]);
    $res  = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    $data = json_decode($res, true);
    $groups = [];
    if (is_array($data)) {
        foreach ($data as $g) {
            if (isset($g['id'])) $groups[] = ['id' => $g['id'], 'subject' => $g['subject'] ?? ''];
        }
    }
    return ['ok' => ($code >= 200 && $code < 300), 'code' => $code, 'groups' => $groups];
}
