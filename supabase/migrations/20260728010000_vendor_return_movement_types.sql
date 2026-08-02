begin;
alter type public.inventory_movement_type add value if not exists 'vendor_return';
alter type public.inventory_movement_type add value if not exists 'vendor_return_reversal';
commit;
