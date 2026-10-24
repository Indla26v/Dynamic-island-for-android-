# Data safety form (Play Console)

**Does your app collect or share any of the required user data types?** No.

Island processes notification content, media metadata and the on-screen window list **on the device only**, to draw the island. It does not send any of this off the device. It has no `INTERNET` permission (the manifest removes it explicitly, along with `ACCESS_NETWORK_STATE`). Under Play's definitions, processing that never leaves the device is not "collection".

| Question | Answer |
| --- | --- |
| Data collected | None |
| Data shared | None |
| Is data encrypted in transit? | Not applicable (no network access) |
| Can users request deletion? | Uninstalling removes everything; settings and per-app rules live in app storage |
| Committed to the Families policy? | No (not designed for children) |

## Permissions to explain in the listing

| Permission | Why |
| --- | --- |
| Notification access (NotificationListenerService) | Read the notifications and media sessions to show on the island |
| Accessibility service | Draw over the status bar at the cutout, and list windows to hide in full screen or when the shade is open |
| POST_NOTIFICATIONS | Only for the "Try it" demo notifications |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Keep the listener bound on aggressive OEM skins (no foreground service, no wakelocks) |

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted on Play. Island's case is that a notification listener has to stay bound, which is the "acceptable use" for apps whose core function would break. If review pushes back, remove the direct request and keep only the link to the system battery settings list.
