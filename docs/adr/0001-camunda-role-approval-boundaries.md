# ADR 0001: Camunda 8 Boundaries for Role Approval

## Status

Accepted.

## Context

We want to add Camunda to `user-service`, but only as an orchestration tool for a human approval process. Camunda must not become the main programming model of the service and must not leak into the domain.

The first process is `role-approval`: approval of a request to assign a role to a user.

## Decision

The target platform is Camunda 8.

Camunda lives at the infrastructure boundary. The domain and application layers stay plain Java code:

- `domain` does not import Camunda, Zeebe, BPMN APIs, job clients, or process-instance APIs;
- `application` use cases remain ordinary services without Camunda annotations or `ZeebeClient`;
- Camunda workers call application services;
- workers do not update database tables directly;
- REST APIs work through application services, not directly through a Camunda client.

The rule is simple: BPMN controls the order of process steps, while business rules and request state stay inside `user-service`.

## Process Outline

`role-approval` starts after an application service creates a role request.

Process steps:

1. The application service creates a `RoleRequest` in `PENDING` status.
2. The application service starts the `role-approval` process.
3. The process waits for an admin user task.
4. The admin completes the task through API with `APPROVED` or `REJECTED`.
5. If the decision is `APPROVED`, a worker calls an application service to assign the role.
6. The application service moves the request to `APPROVED`.
7. If the decision is `REJECTED`, the application service moves the request to `REJECTED` and stores the reason.
8. If the process or worker reaches a non-recoverable error, the application service moves the request to `FAILED`.

Role assignment must be idempotent. A worker retry must not assign the same role twice and must not create duplicate side effects.

## Process Variables

The process uses these variables:

- `roleRequestId`
- `userId`
- `requestedRole`
- `requestedBy`
- `reviewerId`
- `decision`
- `decisionReason`

Process variables are used by Camunda to route and execute the process. They are not the source of truth for users, roles, or request status.

## RoleRequest

`RoleRequest` is stored in the database as a business record. It is needed for audit, recovery, and status visibility without depending on Camunda history.

`RoleRequest` should store:

- request id;
- user id;
- requested role;
- requester id;
- reviewer id, once a decision is made;
- decision and rejection reason, when present;
- status;
- created timestamp;
- updated timestamp;
- completed timestamp, when finished;
- failure reason, when status is `FAILED`.

The process instance id may be stored if it is useful for support, cancellation, or diagnostics. Domain rules must not depend on it.

Only technical process data should stay exclusively in process variables:

- task id;
- job id;
- retry metadata;
- temporary routing flags.

These values should not be stored in `RoleRequest` unless there is a clear reason to treat them as business data.

## Statuses

Role request statuses:

- `PENDING`: the request was created and is waiting for review or process execution;
- `APPROVED`: the request was approved and the role was assigned;
- `REJECTED`: the request was rejected;
- `FAILED`: the process or application step failed and needs investigation or a new request.

Status changes are owned by application services. A Camunda worker must not update status directly in the database.

## Layer Boundaries

### Domain

The domain contains entities, value objects, status transition rules, and domain errors.

Domain candidates:

- `RoleRequest`;
- `RoleRequestStatus`;
- rule that blocks duplicate active requests;
- status transition rules;
- domain exceptions for role requests.

Forbidden in the domain:

- Camunda, Zeebe, or BPMN imports;
- process variable maps;
- worker annotations;
- direct infrastructure calls.

### Application

The application layer contains ordinary use cases. It coordinates the domain, repositories, ports, and transactions.

Future use cases:

- create a role request;
- start approval after request creation;
- approve a request;
- reject a request;
- apply an approved request and assign the role;
- move a request to `FAILED`;
- get request status.

The application layer may declare workflow ports, but Camunda APIs must not appear in their signatures.

Valid port style:

```java
interface RoleApprovalWorkflowPort {
    void startRoleApproval(RoleApprovalStartCommand command);
}
```

Forbidden in application use cases:

- `ZeebeClient`;
- Camunda annotations;
- business logic coupled to BPMN element ids.

### Infrastructure

Infrastructure owns the Camunda 8 integration.

Infrastructure contains:

- Camunda client configuration;
- BPMN deployment;
- adapter for `RoleApprovalWorkflowPort`;
- workers for the `role-approval` process;
- mapping between process variables and application commands.

Workers only translate a Camunda event into an application service call.

## API

The process is started by an application service after it creates a `RoleRequest`.

The user task is completed by an admin through API. The API must not complete a Camunda task first and then try to catch up the database state. The correct order is: call an application use case, validate permissions and request state, store the decision, and then coordinate task completion through a workflow port.

Future endpoints:

- `POST /api/role-requests`
- `GET /api/role-requests/{id}`
- `GET /api/role-requests`
- `POST /api/role-requests/{id}/approve`
- `POST /api/role-requests/{id}/reject`

## Errors and Retry

Camunda retry is for technical failures, but it must be safe.

Rules:

- role assignment is idempotent by `roleRequestId`;
- repeated execution of the approval worker does not assign the role twice;
- repeated execution of the approval worker does not create duplicate audit records;
- if the user already has the role for the same approved request, the application service treats the step as already completed;
- business rejection is not a retry case;
- a non-recoverable technical error moves the request to `FAILED` through an application service.

Camunda owns retrying the process step. The application owns business idempotency.

## Future Ports, Workers, and API

Future ports:

- `RoleRequestRepository`
- `RoleApprovalWorkflowPort`
- `RoleRequestAuditPort`
- a port or an existing application service for role assignment

Future workers:

- `apply-approved-role-request`
- `mark-role-request-failed`
- optional notification worker after approval or rejection

Future APIs:

- create request;
- list requests for admins;
- get request status;
- approve;
- reject.

Future checks:

- architecture test: Camunda/Zeebe imports are forbidden in `domain`;
- architecture test: Camunda/Zeebe imports are forbidden in application use cases;
- idempotency test for role assignment retry;
- process variable mapping test;
- API tests for approve/reject.

## Consequences

We add a small amount of infrastructure code around Camunda, but keep the `user-service` core independent from the workflow engine.

The main constraint is that workers do not make quick direct database updates. This is intentional. Role assignment, status changes, and audit must remain ordinary application-layer behavior instead of being hidden in BPMN integration code.
