begin;

alter table private.vendor_operation_requests drop constraint vendor_operation_allowed;
alter table private.vendor_operation_requests add constraint vendor_operation_allowed
check(operation in ('payment','return','reverse_payment','reverse_return',
  'manage_create','manage_update','manage_archive'));

create or replace function public.manage_vendor(
  p_idempotency_key text,
  p_action text,
  p_shop_id uuid,
  p_vendor_id uuid default null,
  p_display_name text default null,
  p_phone text default null,
  p_tax_reference text default null,
  p_notes text default null
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid());
  normalized_action text := lower(trim(p_action));
  target_id uuid;
  fingerprint text;
  request private.vendor_operation_requests%rowtype;
  vendor_row public.vendors%rowtype;
  before_state jsonb := '{}'::jsonb;
  after_state jsonb;
begin
  if actor is null then raise exception using errcode='42501',message='not authorized'; end if;
  if normalized_action not in ('create','update','archive') or p_idempotency_key is null
     or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid vendor operation';
  end if;
  if not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active
  ) then raise exception using errcode='42501',message='not authorized'; end if;

  if normalized_action in ('create','update') and (
    p_display_name is null or length(trim(p_display_name)) not between 1 and 160
    or p_display_name ~ '[[:cntrl:]]'
    or (p_phone is not null and (length(trim(p_phone)) not between 1 and 40 or p_phone ~ '[[:cntrl:]]'))
    or (p_tax_reference is not null and (length(trim(p_tax_reference)) not between 1 and 80 or p_tax_reference ~ '[[:cntrl:]]'))
    or (p_notes is not null and (length(trim(p_notes)) not between 1 and 1000 or p_notes ~ '[[:cntrl:]]'))
  ) then raise exception using errcode='22023',message='invalid vendor fields'; end if;
  if normalized_action='create' and p_vendor_id is not null then
    raise exception using errcode='22023',message='create vendor id must be server generated';
  elsif normalized_action in ('update','archive') and p_vendor_id is null then
    raise exception using errcode='22023',message='vendor id is required';
  end if;

  if normalized_action='create' then target_id:=extensions.gen_random_uuid();
  else
    target_id:=p_vendor_id;
    select * into vendor_row from public.vendors where id=target_id and shop_id=p_shop_id for update;
    if not found then raise exception using errcode='42501',message='not authorized'; end if;
    before_state:=jsonb_build_object('display_name',vendor_row.display_name,'phone',vendor_row.phone,
      'tax_reference',vendor_row.tax_reference,'notes',vendor_row.notes,'active',vendor_row.active);
  end if;

  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',normalized_action,p_shop_id::text,
    coalesce(p_vendor_id::text,''),coalesce(trim(p_display_name),''),coalesce(trim(p_phone),''),
    coalesce(trim(p_tax_reference),''),coalesce(trim(p_notes),'')),'sha256'),'hex');
  insert into private.vendor_operation_requests(shop_id,idempotency_key,operation,actor_user_id,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),'manage_'||normalized_action,actor,fingerprint,target_id) on conflict do nothing;
  select * into request from private.vendor_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'manage_'||normalized_action or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  target_id:=request.record_id;

  if normalized_action='create' then
    insert into public.vendors(id,shop_id,display_name,phone,tax_reference,notes)
    values(target_id,p_shop_id,trim(p_display_name),nullif(trim(p_phone),''),nullif(trim(p_tax_reference),''),nullif(trim(p_notes),''))
    returning * into vendor_row;
  elsif normalized_action='update' then
    if not vendor_row.active then raise exception using errcode='55000',message='archived vendor cannot be updated'; end if;
    update public.vendors set display_name=trim(p_display_name),phone=nullif(trim(p_phone),''),
      tax_reference=nullif(trim(p_tax_reference),''),notes=nullif(trim(p_notes),'')
    where id=target_id returning * into vendor_row;
  else
    if not vendor_row.active then raise exception using errcode='55000',message='vendor is already archived'; end if;
    if exists(select 1 from public.purchase_bills where vendor_id=target_id and status='draft') then
      raise exception using errcode='55000',message='vendor is required by an in-progress purchase';
    end if;
    update public.vendors set active=false where id=target_id returning * into vendor_row;
  end if;

  after_state:=jsonb_build_object('id',vendor_row.id,'shop_id',vendor_row.shop_id,
    'display_name',vendor_row.display_name,'phone',vendor_row.phone,'tax_reference',vendor_row.tax_reference,
    'notes',vendor_row.notes,'active',vendor_row.active,'updated_at',vendor_row.updated_at);
  insert into private.business_audit_events(
    shop_id,actor_user_id,operation,record_type,record_id,before_metadata,after_metadata,idempotency_key
  ) values(p_shop_id,actor,normalized_action,'vendor',target_id,before_state,after_state,
    'vendor:'||trim(p_idempotency_key));
  update private.vendor_operation_requests set result=after_state,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return after_state;
end;
$$;

revoke all on table private.vendor_operation_requests from public,anon,authenticated;
revoke all on function public.manage_vendor(text,text,uuid,uuid,text,text,text,text) from public,anon,authenticated;
grant execute on function public.manage_vendor(text,text,uuid,uuid,text,text,text,text) to authenticated;
comment on function public.manage_vendor(text,text,uuid,uuid,text,text,text,text)
is 'Owner-only idempotent vendor create/update/archive with immutable audit evidence.';

commit;
