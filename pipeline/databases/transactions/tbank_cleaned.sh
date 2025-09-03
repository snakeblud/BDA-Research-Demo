#!/bin/bash
set -e

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="transactions"
DB_USER="auth_user"
export PGPASSWORD="eqor823y5f828y5n9*T)R(RB@&*T@BO*FN*&O@tb2f8o47"   # set this properly

ROWS=${1:-1000}

echo "Inserting $ROWS rows into tbank_cleaned..."

for i in $(seq 1 $ROWS); do
  TRANSACTIONID=$((100000 + RANDOM % 900000))
  ACCOUNTFROM=$((RANDOM % 10000))
  ACCOUNTTO=$((RANDOM % 10000))
  BANKIDFROM=$((RANDOM % 100))
  BANKIDTO=$((RANDOM % 100))
  TRANSACTIONAMOUNT=$(awk -v min=0.01 -v max=10000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')
  EXCHANGERATE="1.0"

  # macOS-compatible timestamp (random shift in seconds)
  # Random offset (up to ~30 days back)
  OFFSET=$((RANDOM % 2592000))   # 30 * 24 * 60 * 60

  # Current epoch minus offset
  TRANSACTIONDATE=$(date -u -d "@$(( $(date +%s) - OFFSET ))" +"%Y-%m-%d %H:%M:%S")

  TRANSACTIONTYPE=$((RANDOM % 200))
  INTERIMBALANCE=$(awk -v min=100 -v max=10000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')
  ACCOUNTTO_INTERIMBALANCE=$(awk -v min=100 -v max=10000 'BEGIN{srand(); printf "%.2f", min+rand()*(max-min)}')
  CURRENCY="SGD"
  QUOTECURRENCY="SGD"
  PAYMENTMODE=$([ $((RANDOM % 2)) -eq 0 ] && echo "Cash" || echo "Card")
  OVERRIDEFLAG=$([ $((RANDOM % 2)) -eq 0 ] && echo "True" || echo "False")
  NARRATIVE="auto_tx_$i"

  psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c \
    "INSERT INTO tbank_cleaned (
      TRANSACTIONID, ACCOUNTFROM, ACCOUNTTO, BANKIDFROM, BANKIDTO,
      TRANSACTIONAMOUNT, EXCHANGERATE, TRANSACTIONDATE, TRANSACTIONTYPE,
      INTERIMBALANCE, ACCOUNTTO_INTERIMBALANCE, CURRENCY, QUOTECURRENCY,
      PAYMENTMODE, OVERRIDEFLAG, NARRATIVE
    ) VALUES (
      $TRANSACTIONID, $ACCOUNTFROM, $ACCOUNTTO, $BANKIDFROM, $BANKIDTO,
      $TRANSACTIONAMOUNT, $EXCHANGERATE, '$TRANSACTIONDATE', $TRANSACTIONTYPE,
      $INTERIMBALANCE, $ACCOUNTTO_INTERIMBALANCE, '$CURRENCY', '$QUOTECURRENCY',
      '$PAYMENTMODE', $OVERRIDEFLAG, '$NARRATIVE'
    ) ON CONFLICT (TRANSACTIONID) DO NOTHING;" >/dev/null
done

echo "✅ Done. Inserted $ROWS rows into tbank_cleaned."