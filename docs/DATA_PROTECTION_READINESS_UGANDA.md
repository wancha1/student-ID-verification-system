# Uganda Data Protection Readiness — Scholink

**Status: legal and school sign-off required. This checklist is not legal advice.** Scholink processes student identity data and guardian contact information. The school and the software operator must agree who is the data controller, processor and any sub-processors before real student data is loaded.

## 1. Processing roles and registration

- [ ] Identify the school entity that determines the purposes and means of student-data processing.
- [ ] Identify Scholink's operator/developer role and any hosting or support processors.
- [ ] Confirm registration obligations for each relevant data controller, data collector and data processor with the Personal Data Protection Office (PDPO).
- [ ] Confirm the responsible Data Protection Officer (DPO) or designated privacy contact.
- [ ] Record processing purposes, data categories, recipients, retention periods and security measures in the registration and internal records.

The PDPO states that organisations collecting personal data should register and obtain a certificate. The PDPO registration guidance says controllers, collectors and processors are required to register unless an exemption is gazetted.

## 2. Cross-border hosting

The connected Supabase project is in region eu-central-1 (Frankfurt, Germany), not Uganda. This is cross-border processing/storage.

- [ ] Obtain legal advice on section 19 of the Data Protection and Privacy Act, 2019.
- [ ] Document whether the destination country has adequate protection at least equivalent to the Act, or whether the relevant data subject consent route is relied on.
- [ ] Record the transfer purpose, categories, recipient, safeguards, contract terms and retention.
- [ ] Review Supabase's data-processing terms and any relevant sub-processors.
- [ ] Confirm where database backups, logs, support access and Storage objects are processed or retained; do not assume all components share the database region.

Do not treat the EU region as automatically compliant or non-compliant. The legal basis and safeguards need to be documented.

## 3. Lawful collection and transparency

- [ ] Document the lawful basis for each category of student and guardian data.
- [ ] Have counsel determine the correct consent/authorisation process for minors and any school/legal obligation that applies.
- [ ] Provide an understandable privacy notice before collection or as soon as legally required when data comes from a third party.
- [ ] Explain data categories, purpose, responsible organisation, whether provision is mandatory, consequences of non-provision, recipients, access/correction rights and retention.
- [ ] Collect only fields necessary for the approved school purposes.
- [ ] Review student photos, guardian phone numbers, emergency contacts and free-text notes as higher-risk fields.

## 4. Security and processor controls

- [ ] Execute a written controller–processor agreement covering confidentiality, security, instructions, sub-processors, access, retention, deletion, incident support and audit cooperation.
- [ ] Enforce least privilege, MFA for privileged users, secure key management and device-loss response.
- [ ] Encrypt sensitive local data at rest and protect backups.
- [ ] Keep student photos in private storage and include Storage objects in backup/restore planning.
- [ ] Keep production student data out of development, screenshots, logs, crash reports and test fixtures.

## 5. Data-subject rights and retention

- [ ] Define how a student/guardian requests access to personal information.
- [ ] Define correction, objection/prevention-of-processing, export and deletion workflows.
- [ ] Define who verifies the requester and who approves each action.
- [ ] Establish a written retention schedule for roster records, photos, gate logs, audit events, backups and exports.
- [ ] Ensure expiry deletion prevents reconstruction in intelligible form where required.
- [ ] Keep an auditable record of requests and decisions without retaining unnecessary copies of the data.

## 6. Breach response

- [ ] Name the incident owner and backup contact.
- [ ] Prepare containment, investigation, evidence preservation and recovery steps.
- [ ] Define how to assess affected data categories and approximate numbers of students/guardians.
- [ ] Prepare the PDPO notification process. Section 23 of the Act and regulation 33 of the 2021 Regulations require notification to the Authority immediately after a qualifying breach; the Authority determines whether affected data subjects should also be notified.
- [ ] Run a tabletop incident exercise before pilot.

## Official references

- [Uganda Data Protection and Privacy Act, 2019](https://ict.go.ug/site/documents/Data-Protection-and-Privacy-Act-2019.pdf) — especially sections 6, 7, 13, 19–23, 24–29.
- [PDPO Uganda](https://pdpo.go.ug/) — registration, register search and breach reporting.
- [PDPO Registration Classification and Guidance Notes](https://pdpo.go.ug/media/2022/02/Guidance_note_on_lodging_complaints.pdf) — confirm current registration requirements and process with the Office.
- [Data Protection and Privacy Regulations, 2021](https://pdpo.go.ug/media/2022/03/Data_Protection_and_Privacy_Regulations-2021.pdf) — regulation 33 covers breach notification details and timing.

**Release gate:** no real student roster, photographs or guardian contacts should be imported until the school has completed the applicable registration, cross-border transfer, privacy notice, processor-contract, retention and incident-response decisions with qualified advice.
