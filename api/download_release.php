<?php
// Download público do .apk publicado (sem X-Api-Key/X-Auth-Token de propósito —
// precisa funcionar num tap direto do navegador do celular/relógio, que não
// manda headers customizados). Não é dado sensível, é só o instalador do app.
header('Access-Control-Allow-Origin: *');

$platform = $_GET['platform'] ?? '';
if (!in_array($platform, ['android', 'wear'], true)) {
    http_response_code(400);
    echo 'Parâmetro platform inválido (use android ou wear).';
    exit;
}

$dir      = __DIR__ . '/releases';
$metaFile = $dir . '/meta.json';
$meta     = is_file($metaFile) ? (json_decode(file_get_contents($metaFile), true) ?: []) : [];
$info     = $meta[$platform] ?? null;

if (!$info || empty($info['fileName']) || !is_file($dir . '/' . $info['fileName'])) {
    http_response_code(404);
    echo 'Nenhuma build publicada ainda para "' . htmlspecialchars($platform) . '".';
    exit;
}

$path = $dir . '/' . $info['fileName'];
$versionSlug  = preg_replace('/[^a-zA-Z0-9._-]/', '', $info['versionName'] ?? 'build');
$downloadName = 'fut-segunda-' . $platform . '-v' . $versionSlug . '.apk';

header('Content-Type: application/vnd.android.package-archive');
header('Content-Disposition: attachment; filename="' . $downloadName . '"');
header('Content-Length: ' . filesize($path));
header('Cache-Control: no-cache, no-store, must-revalidate');
header('Pragma: no-cache');
readfile($path);
