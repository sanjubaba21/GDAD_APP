begin;
alter type public.journal_kind add value if not exists 'inventory_adjustment';
commit;
