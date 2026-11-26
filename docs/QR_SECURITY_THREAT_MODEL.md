# Scholink V2 QR — Threat Model

## Security properties that must not be conflated

1. **Cryptographic authenticity:** a valid ECDSA signature establishes that a trusted issuer signed the exact canonical payload.
2. **Credential integrity:** strict parsing and signature verification detect payload alteration.
3. **Student identity:** the QR payload identifies a card record. It does not prove the presenter is the named student.
4. **Physical possession:** scanning a QR proves only that someone can display its image. It does not prove possession of the original physical card.
5. **Authorization:** entry, exit, meals and examinations require separate school policy decisions. A valid signature does not authorize any of them.

## Threats

| Threat | What the current static signed QR does | Residual risk |
|---|---|---|
| Screenshot/photo copying | Signature remains valid in the copied image. | A copied credential can be presented by another person. |
| QR sharing | No cryptographic prevention. | A student can share the image remotely. |
| Replay | Repeated presentation contains the same valid signed token. | A second verifier or disconnected device may accept the same card again. |
| Forged payload | ECDSA verification rejects payloads without a trusted issuer signature. | Issuer private-key compromise defeats this control. |
| Unknown/revoked issuer | Local trust registry can reject unknown/revoked key IDs. | Offline devices may not receive revocation promptly. |
| Lost/stolen card | Server-side card status can reject a suspended card when current data is available. | Offline stale roster may continue to accept it. |
| Clock manipulation | Signed validity dates are checked against the verifier clock. | A modified device clock can affect date-window checks unless trusted time is available. |
| Issuer key replacement | New database immutability migration prevents changing public-key material under an existing key ID. | The key-enrollment authority itself still needs strong account protection and governance. |
| Multiple offline devices | Local debounce can suppress rapid repeats on one device. | It cannot provide global anti-passback across disconnected devices. |

## Proportionate controls

### Immediate controls
- Keep the QR payload minimal and signed.
- Continue strict versioned parsing and reject unknown versions, malformed encodings, unknown issuer IDs and revoked issuer IDs.
- Keep issuer private keys on a dedicated issuing device in Android Keystore; verifier devices must hold public keys only.
- Make issuer public keys immutable and revocation one-way at the database boundary.
- Require staff to compare the displayed student profile/photo with the person presenting the card where school policy and privacy rules permit.
- Use card suspension/replacement procedures for reported lost or stolen cards.
- Make stale/offline verification visibly distinct from a current server-backed decision.
- Record scan events with unique event IDs and deduplicate them when devices reconnect.
- Do not describe a local scan throttle as anti-replay protection.

### Offline limitation
A static QR cannot provide reliable cross-device anti-replay while all verifiers are disconnected. Device-local duplicate suppression is useful for accidental double scans, not for preventing a student from presenting the same screenshot at another gate.

A global anti-passback guarantee requires shared state or a protocol that makes each presentation fresh. When the network is unavailable, the school must either accept this limitation under a formally approved offline policy or fall back to manual identity checks.

### Future options

**Option A — Static signed QR plus photo/manual identity comparison**
- Lowest cost and simplest offline operation.
- Does not prevent credential copying.
- Suitable only if the school accepts the residual risk and trains staff to compare the presenter with the profile.

**Option B — Short-lived dynamic QR**
- Issuer/app creates a short-lived signed token containing a nonce and expiry.
- Verifiers check freshness and server-side replay state.
- Stronger replay resistance when online.
- Offline devices still cannot coordinate used nonces; short expiry depends on trustworthy time and key distribution.

**Option C — Challenge-response credential**
- Verifier sends a fresh challenge to a student-held device or NFC-capable credential.
- The credential signs the challenge with a protected private key.
- Stronger proof of possession than a static image.
- Requires student devices or suitable smart cards, enrollment, support and a more complex operational model. It is not a low-cost assumption for the initial LTC pilot.

## Recommendation for Scholink V1
Do not redesign the current QR format yet. Keep it as a signed card identifier, explicitly document that it is copyable, and combine it with a staff-facing student photo/identity check and current card-status verification when online. Make offline decisions visibly stale and subject to an approved manual procedure.

Do not claim anti-passback or proof of student identity from the QR signature alone.

## Required acceptance tests
- Bit-flipped payload and altered card ID are rejected.
- Unknown issuer key is rejected.
- Revoked issuer key is rejected.
- Revoked key cannot be reactivated.
- Existing key material cannot be replaced under its key ID.
- Same QR scanned twice on one device is handled predictably.
- Same QR scanned on two online devices produces deduplicated/consistent server events.
- Same QR scanned on two offline devices is documented as a residual risk, not a passed anti-replay test.
- Offline stale roster and revoked-card scenarios are visibly identified.
- Clock rollback and forward-jump behavior is tested on supported devices.
- Staff can distinguish identity verification from authorization outcomes.
