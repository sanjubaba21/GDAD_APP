begin;

create or replace function private.jsonb_metadata_is_safe(payload jsonb)
returns boolean language plpgsql stable set search_path = '' as $$
declare pair record; item jsonb;
begin
    if payload is null or pg_column_size(payload) > 8192 then return false; end if;
    if jsonb_typeof(payload) = 'object' then
        for pair in select key, value from jsonb_each(payload) loop
            if lower(pair.key) ~ '(^|_)(pin|password|passwd|secret|token|authorization|cookie|credential|verifier|hash|service_role|api_key|private_key)($|_)' then
                return false;
            end if;
            if not private.jsonb_metadata_is_safe(pair.value) then return false; end if;
        end loop;
    elsif jsonb_typeof(payload) = 'array' then
        for item in select value from jsonb_array_elements(payload) loop
            if not private.jsonb_metadata_is_safe(item) then return false; end if;
        end loop;
    elsif jsonb_typeof(payload) = 'string' and length(payload #>> '{}') > 1000 then
        return false;
    end if;
    return true;
end;
$$;

create or replace function private.notification_source_exists(
    target_type public.notification_record_type, target_shop_id uuid, target_record_id uuid
)
returns boolean language plpgsql stable security definer set search_path = '' as $$
begin
    case target_type
      when 'product' then return exists(select 1 from public.products where shop_id=target_shop_id and id=target_record_id);
      when 'sale' then return exists(select 1 from public.sales where shop_id=target_shop_id and id=target_record_id);
      when 'sale_return' then return exists(select 1 from public.sale_returns where shop_id=target_shop_id and id=target_record_id);
      when 'purchase_bill' then return exists(select 1 from public.purchase_bills where shop_id=target_shop_id and id=target_record_id);
      when 'purchase_receipt' then return exists(select 1 from public.purchase_receipts where shop_id=target_shop_id and id=target_record_id);
      when 'vendor' then return exists(select 1 from public.vendors where shop_id=target_shop_id and id=target_record_id);
      when 'vendor_return' then return exists(select 1 from public.vendor_returns where shop_id=target_shop_id and id=target_record_id);
      when 'expense' then return exists(select 1 from public.expenses where shop_id=target_shop_id and id=target_record_id);
      when 'journal_transaction' then return exists(select 1 from public.journal_transactions where shop_id=target_shop_id and id=target_record_id);
      when 'user_profile' then return exists(select 1 from public.shop_memberships where shop_id=target_shop_id and user_id=target_record_id);
      when 'system' then return target_record_id is null;
    end case;
    return false;
end;
$$;

revoke all on function private.jsonb_metadata_is_safe(jsonb),
    private.notification_source_exists(public.notification_record_type,uuid,uuid)
from public,anon,authenticated;

commit;
