# Skill: Lead Angular Software Engineer

## Context & Source Files
- **Workspace**: `/home/gmhossen/Project/pay-flow`
- **Implementation Plan**: `/home/gmhossen/Project/pay-flow/specs/FRONTEND_TASKS.md`
- **API Catalog**: `/home/gmhossen/Project/pay-flow/specs/API_CATALOG.md`
- **UI Template**: `/home/gmhossen/Project/pay-flow/PayFlow Console.html`
- **Frontend App Directory**: `/home/gmhossen/Project/pay-flow/frontend`

## Architectural Constraints (Non-Negotiable)
1. **Latest Angular Standards**:
   - Standalone components (`standalone: true`).
   - Modern control flow (`@if`, `@for (...; track ...)`), NO legacy `*ngIf`/`*ngFor`.
   - Dependency injection via `inject(Service)` instead of constructor injection.
   - All component and service state must use Angular Signals (`signal()`, `computed()`).

2. **Service Adapter Pattern**:
   - Components MUST NOT inject `HttpClient`.
   - All network calls live in `core/services/` (`AuthService`, `PaymentService`, `LedgerService`, `OperatorService`).
   - Endpoints marked **REAL** call `http://localhost:8080`.
   - Endpoints marked **STUB** return typed observables of mock data with `// TODO: Wire to backend once BE-xx is merged`.

3. **Design Tokens & Styling**:
   - Port CSS custom properties directly from `PayFlow Console.html` into `src/styles.css`.
   - Use the CSS variable names (`--bg`, `--surface`, `--ink`, `--accent`, `--status-*`, `--rail-*`).

4. **Self-Verification Before Requesting Review**:
   - Always run `npm run build` or `ng build` to verify zero TypeScript errors and zero build warnings before submitting work to the Architect.

5. **Commit & Progression Protocol**:
   - The Lead SWE owns git commits.
   - Do NOT commit code until the Architect gives `STATUS: APPROVED`.
   - When the Architect approves, stage and commit the changes immediately:
     ```bash
     git add frontend/
     git commit -m "feat(frontend): Task [X] completed and verified by Architect"
     ```
   - Then immediately proceed to the next task in `specs/FRONTEND_TASKS.md`.
