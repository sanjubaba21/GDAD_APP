#!/usr/bin/env bash
set -uo pipefail

database_url="${DATABASE_URL:-postgresql://postgres:postgres@127.0.0.1:54322/postgres}"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

psql "$database_url" -v ON_ERROR_STOP=1 -q -f "$root_dir/supabase/integration-tests/backend_concurrency_setup.sql"

shop_a='a6900000-0000-4000-8000-000000000001'
owner_a='10690000-0000-4000-8000-000000000001'
disabled_a='20690000-0000-4000-8000-000000000002'
owner_b='30690000-0000-4000-8000-000000000003'
product_race='a6b00000-0000-4000-8000-000000000001'
product_retry='a6b00000-0000-4000-8000-000000000002'

run_sql() {
  local sql="$1" output="$2"
  psql "$database_url" -v ON_ERROR_STOP=1 -Atq -c "$sql" >"$output" 2>&1
}

wait_pair_exactly_one() {
  local first_pid="$1" second_pid="$2" label="$3" first_status second_status successes
  wait "$first_pid"; first_status=$?
  wait "$second_pid"; second_status=$?
  successes=0
  [[ "$first_status" -eq 0 ]] && successes=$((successes+1))
  [[ "$second_status" -eq 0 ]] && successes=$((successes+1))
  if [[ "$successes" -ne 1 ]]; then
    echo "$label expected exactly one success; statuses=$first_status,$second_status" >&2
    return 1
  fi
}

sale_a="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_a',true); select pg_sleep(0.2); select public.post_fifo_sale('race-a','$shop_a',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','$product_race','quantity',1)),0,false,null,null,null,'[{\"method\":\"cash\",\"amount_paisa\":1000}]'::jsonb); commit;"
sale_b="${sale_a/race-a/race-b}"
run_sql "$sale_a" "$tmp_dir/sale-a.log" & sale_a_pid=$!
run_sql "$sale_b" "$tmp_dir/sale-b.log" & sale_b_pid=$!
wait_pair_exactly_one "$sale_a_pid" "$sale_b_pid" 'competing FIFO sale' || exit 1

retry_sql="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_a',true); select pg_sleep(0.2); select public.post_fifo_sale('retry-same','$shop_a',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','$product_retry','quantity',2)),0,false,null,null,null,'[{\"method\":\"cash\",\"amount_paisa\":2000}]'::jsonb); commit;"
run_sql "$retry_sql" "$tmp_dir/retry-a.log" & retry_a_pid=$!
run_sql "$retry_sql" "$tmp_dir/retry-b.log" & retry_b_pid=$!
wait "$retry_a_pid"; retry_a_status=$?
wait "$retry_b_pid"; retry_b_status=$?
if [[ "$retry_a_status" -ne 0 || "$retry_b_status" -ne 0 ]] || ! cmp -s "$tmp_dir/retry-a.log" "$tmp_dir/retry-b.log"; then
  echo 'exact concurrent retry did not replay one identical result' >&2
  exit 1
fi

bill_id="$(psql "$database_url" -Atq -c "select id from public.purchase_bills where idempotency_key='purchase:integration-purchase:bill'")"
vendor_a="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_a',true); select pg_sleep(0.2); select public.post_vendor_payment('vendor-race-a','$shop_a','a6d00000-0000-4000-8000-000000000001','cash',jsonb_build_array(jsonb_build_object('purchase_bill_id','$bill_id','amount_paisa',700)),(timezone('Asia/Kathmandu',now()))::date); commit;"
vendor_b="${vendor_a/vendor-race-a/vendor-race-b}"
run_sql "$vendor_a" "$tmp_dir/vendor-a.log" & vendor_a_pid=$!
run_sql "$vendor_b" "$tmp_dir/vendor-b.log" & vendor_b_pid=$!
wait_pair_exactly_one "$vendor_a_pid" "$vendor_b_pid" 'competing vendor payment' || exit 1

sale_id="$(psql "$database_url" -Atq -c "select id from public.sales where idempotency_key='sale:retry-same:header'")"
return_sql="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_a',true); select public.post_sale_return('partial-one','$shop_a','$sale_id',(timezone('Asia/Kathmandu',now()))::date,'Integration partial return',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id='$sale_id'),'quantity',1,'disposition','sellable')),'cash'); commit;"
run_sql "$return_sql" "$tmp_dir/return.log" || { cat "$tmp_dir/return.log" >&2; exit 1; }

cash_id="$(psql "$database_url" -Atq -c "select id from public.financial_accounts where shop_id='$shop_a' and purpose_code='cash_main'")"
expense_a="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_a',true); select pg_sleep(0.2); select public.post_expense('expense-race-a','$shop_a','$cash_id',900,(timezone('Asia/Kathmandu',now()))::date,'Concurrent debit',null,null); commit;"
expense_b="${expense_a/expense-race-a/expense-race-b}"
run_sql "$expense_a" "$tmp_dir/expense-a.log" & expense_a_pid=$!
run_sql "$expense_b" "$tmp_dir/expense-b.log" & expense_b_pid=$!
wait_pair_exactly_one "$expense_a_pid" "$expense_b_pid" 'competing expense debit' || exit 1

disabled_sql="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$disabled_a',true); select public.post_fifo_sale('disabled-forged','$shop_a',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','$product_retry','quantity',1)),0,false,null,null,null,'[{\"method\":\"cash\",\"amount_paisa\":1000}]'::jsonb); commit;"
cross_sql="begin; set local role authenticated; select set_config('request.jwt.claim.sub','$owner_b',true); select public.post_fifo_sale('cross-forged','$shop_a',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','$product_retry','quantity',1)),0,false,null,null,null,'[{\"method\":\"cash\",\"amount_paisa\":1000}]'::jsonb); commit;"
if run_sql "$disabled_sql" "$tmp_dir/disabled.log" || run_sql "$cross_sql" "$tmp_dir/cross.log"; then
  echo 'disabled or cross-shop forged operation unexpectedly succeeded' >&2
  exit 1
fi

psql "$database_url" -v ON_ERROR_STOP=1 -q -f "$root_dir/supabase/integration-tests/backend_concurrency_verify.sql"
