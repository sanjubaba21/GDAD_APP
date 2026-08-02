# Android notification workflow

The first-release notification feed consumes the hosted contract from migration
`20260724220000_notifications_audit.sql`. Android reads `notifications` and
`notification_reads` through authenticated RLS and writes read state only through the
protected `mark_notification_read` RPC dispatched by the durable outbox.

## Read and cache boundary

- The notification query selects only identifier, shop, category, title, body, source
  reference, creation time, and expiry. Android deliberately does not request
  `safe_payload`.
- The read-receipt query explicitly filters `user_id` to the active Auth subject. This is
  required for Super Admin sessions because backend policy may expose receipts for audit
  visibility, while the device must derive only its own read state.
- Room v6 stores source shop/type/id with the existing owner user/tenant primary key.
  Owner/Salesman refresh rejects any response outside the active shop. Super Admin cache
  uses the reserved platform tenant and may contain multiple RLS-authorized shops.
- Expired rows are excluded on both replacement and observation. The UI communicates the
  rolling 90-day history; notification expiry does not remove the authoritative business
  record or immutable audit.

## Mark-read behavior

Opening an unread detail atomically marks the owner-scoped Room row read. Exactly one
`MARK_NOTIFICATION_READ` outbox item is then queued with only
`target_notification_id`. Repeated openings see the already-read row and enqueue nothing.
Remote refresh preserves this local read bit until the naturally idempotent backend RPC
has synchronized, preventing an offline-to-online refresh from visually reverting it.

The outbox contains no title, body, payload, credential, or financial value. WorkManager
dispatches when connected; the backend preserves the original `read_at` timestamp on
retries. Direct inserts into `notification_reads` remain unavailable to Android.

## UI authorization

Super Admin, Owner, and Salesman can open only their RLS-visible feed. The dashboard badge
uses the cached unread count. Category filters and detail views render safe text only.
Related-record navigation is mapped from the typed source and shown only when the active
role's `NavigationPolicy` allows that destination; otherwise no inert or unauthorized
button is rendered.
