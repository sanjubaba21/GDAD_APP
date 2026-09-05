begin;

do $migration$
declare
  function_definition text;
  restricted_rule text := $rule$if actor_role='salesman' and (effective_price<>configured_price or line_discount<>0 or p_sale_discount_paisa<>0) then$rule$;
  flexible_rule text := $rule$if actor_role='salesman' and (line_discount<>0 or p_sale_discount_paisa<>0) then$rule$;
begin
  select pg_get_functiondef(
    'public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)'::regprocedure
  ) into function_definition;

  if strpos(function_definition, restricted_rule) = 0 then
    raise exception 'expected sale price authorization rule was not found';
  end if;

  execute replace(function_definition, restricted_rule, flexible_rule);
end;
$migration$;

comment on function public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)
is 'Posts one negotiated-price, no-negative-stock FIFO sale with settlement, ledger, notification, and audit effects. Both shop roles may set effective unit price; discounts and credit remain Owner-only.';

commit;
