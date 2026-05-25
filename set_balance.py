#!/usr/bin/env python3
"""
set_balance.py — Atualiza somente o saldo inicial no servidor sem tocar nos jogadores.

Uso:
    python set_balance.py 1804.04
"""
import sys, json, getpass

API_URL = "https://fut.barleiseca.com.br/api/api.php"
API_KEY = "fut2-minha-chave-secreta"

HEADERS = {
    'X-Api-Key':    API_KEY,
    'Content-Type': 'application/json',
    'Accept':       'application/json',
    'User-Agent':   'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36',
}

def main():
    if len(sys.argv) < 2:
        print("Uso: python set_balance.py 1804.04")
        sys.exit(1)

    try:
        novo_saldo = float(sys.argv[1].replace(',', '.'))
    except ValueError:
        print("Valor invalido. Use ponto ou virgula como decimal. Ex: 1804.04")
        sys.exit(1)

    try:
        import requests as req
    except ImportError:
        print("ERRO: pip install requests")
        sys.exit(1)

    print("  Credenciais do sistema:")
    usuario = input("  Usuario [admin]: ").strip() or 'admin'
    senha   = getpass.getpass("  Senha: ")

    resp = req.post(API_URL + "?action=login",
                    json={'username': usuario, 'password': senha},
                    headers=HEADERS)
    if not resp.ok:
        print(f"ERRO login: {resp.status_code} {resp.text[:200]}")
        sys.exit(1)
    token = resp.json().get('token')
    print("  Login OK")

    auth = {**HEADERS, 'X-Auth-Token': token}

    data = req.get(API_URL, headers=auth).json()
    if 'config' not in data:
        data['config'] = {}
    saldo_anterior = data['config'].get('initialBalance', 0)
    data['config']['initialBalance'] = novo_saldo

    r = req.post(API_URL, json=data, headers=auth)
    if r.ok:
        print(f"  Saldo inicial atualizado: R$ {saldo_anterior:.2f} -> R$ {novo_saldo:.2f}".replace('.', ','))
    else:
        print(f"  ERRO: {r.status_code} {r.text[:200]}")

if __name__ == '__main__':
    main()
