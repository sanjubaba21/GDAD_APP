begin;
alter type public.journal_kind add value if not exists 'deposit';
alter type public.journal_kind add value if not exists 'withdrawal';
commit;
