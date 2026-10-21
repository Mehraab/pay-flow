# Skill: Angular Architecture & Code Review

## Objective
Review proposed Angular code changes against `specs/FRONTEND_TASKS.md`, `specs/API_CATALOG.md`, and modern Angular best practices. The Architect's role is strictly inspection and architectural governance—do not execute git commands or modify code.

## Inspection Checklist

### 1. Signal Architecture
- [ ] No `signal.set()` or `signal.update()` inside an `effect()` without explicit `allowSignalWrites: true`.
- [ ] Derived state uses `computed()`, not manual synchronization.
- [ ] Readonly signals are exposed to components where internal state shouldn't be mutated externally.

### 2. Separation of Concerns
- [ ] Components must NOT inject `HttpClient`. All HTTP calls must go through domain services in `core/services/`.
- [ ] Business logic, currency calculations (`minorUnits / 100`), and API transformations belong in services or pure utility functions.

### 3. Modern Angular Standards
- [ ] All components, directives, and pipes are `standalone: true`.
- [ ] Dependency injection uses `inject(ServiceName)` instead of constructor injection.
- [ ] Control flow uses modern syntax (`@if`, `@for (item of items(); track item.id)`, `@switch`), NOT legacy `*ngIf` / `*ngFor`.
- [ ] Routing uses functional guards (`canActivate: [authGuard]`).
- [ ] HTTP uses functional interceptors (`HttpInterceptorFn`).

### 4. API & Adapter Compliance
- [ ] Real endpoints call the Gateway on `http://localhost:8080`.
- [ ] Gapped endpoints use typed mock data wrapped in an Observable/Signal with a clear `// TODO: Wire to backend once BE-XX is merged` flag.
- [ ] Token refresh interceptor properly handles 401 without infinite loops (`IS_RETRY` guard).

---

## Output Protocols

### On Approval (`STATUS: APPROVED`)
The Architect does NOT commit code. It issues the commit and transition directive to the Lead SWE:

```text
STATUS: APPROVED
[Summary of verified criteria]

INSTRUCTION_FOR_LEAD_SWE:
1. Stage and commit the verified code:
   git add frontend/
   git commit -m "feat(frontend): Task [X] completed and verified by Architect"
2. Immediately begin implementation of Task [X + 1] from specs/FRONTEND_TASKS.md.
```

### On Rejection (`STATUS: CHANGES_REQUESTED`)
```text
STATUS: CHANGES_REQUESTED
- Location: src/app/path/to/file.ts:line
- Issue: Description of violation
- Remedy: Prescribed refactoring strategy

INSTRUCTION_FOR_LEAD_SWE:
Fix the requested items above and resubmit for review. Do not commit until approved.
```
