#!/usr/bin/env bash
# Send orders to orders-api: normal background traffic, with the noise a real
# service has (the odd out-of-stock, the odd fraud decline).
#
#   load.sh [-n COUNT] [-r PER_SECOND] [--country CC] [--name NAME]
#
#   -n COUNT       orders to send; 0 = until Ctrl-C (default 20)
#   -r PER_SECOND  rate (default 2)
#   --country CC   force the customer's country (otherwise PT/ES/FR/DE/IT/NL)
#   --name NAME    force the customer's name
#
# Prints one line per order: the transaction id, the HTTP status, the order id.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

count=20
rate=2
force_country=""
force_name=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -n) count="$2"; shift 2 ;;
        -r) rate="$2"; shift 2 ;;
        --country) force_country="$2"; shift 2 ;;
        --name) force_name="$2"; shift 2 ;;
        -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
        *) die "unknown argument: $1" ;;
    esac
done

first=(Ana Bruno Carla Diogo Elena Filipe Greta Hugo Ines Jorge Klara Luis Marta Nuno Olga Pedro)
last=(Silva Costa Moreau Becker Rossi Jansen Garcia Ferreira Dubois Weber Bianchi Visser)
countries=(PT ES FR DE IT NL)
skus=(SKU-1001 SKU-1002 SKU-2001 SKU-3005 SKU-4410)
pause=$(awk "BEGIN { print 1 / $rate }")

pick() {
    local arr=("$@")
    echo "${arr[RANDOM % ${#arr[@]}]}"
}

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

send() {
    local fn ln name email country phone sku qty price tx
    fn=$(pick "${first[@]}")
    ln=$(pick "${last[@]}")
    name="${force_name:-$fn $ln}"
    email="$(echo "$fn.$ln" | tr '[:upper:]' '[:lower:]')@example.com"
    country="${force_country:-$(pick "${countries[@]}")}"
    phone="+351 9$((RANDOM % 10))$((RANDOM % 10)) $((100 + RANDOM % 900)) $((100 + RANDOM % 900))"
    qty=$((1 + RANDOM % 3))
    price="$((5 + RANDOM % 295)).$((10 + RANDOM % 90))"
    sku=$(pick "${skus[@]}")
    case $((RANDOM % 15)) in
        0) sku="SKU-LIMITED" ;;              # runs out now and then: 409
        1) price="1500.00"; qty=2 ;;         # high amount: fraud decline, 422
    esac
    tx="tx-$(uuidgen | tr '[:upper:]' '[:lower:]' | cut -c1-13)"

    local body
    body=$(printf '{"customer":{"name":"%s","email":"%s","phone":"%s","country":"%s"},"items":[{"sku":"%s","quantity":%d,"unitPrice":%s}],"cardLast4":"%04d"}' \
        "$(json_escape "$name")" "$email" "$phone" "$country" "$sku" "$qty" "$price" $((RANDOM % 10000)))

    local out status order
    out=$(curl -s -m 15 -o /dev/stdout -w '\n%{http_code}' -X POST "$ORDERS_URL/orders" \
        -H 'Content-Type: application/json' -H "X-Transaction-Id: $tx" -d "$body" || true)
    status=$(tail -n1 <<<"$out")
    order=$(head -n1 <<<"$out" | sed -n 's/.*"id":"\(ord-[^"]*\)".*/\1/p')
    printf '%s  %s  %s %s\n' "$tx" "${status:-000}" "${order:--}" "$country"
}

i=0
while [[ "$count" -eq 0 || "$i" -lt "$count" ]]; do
    send
    i=$((i + 1))
    sleep "$pause"
done
