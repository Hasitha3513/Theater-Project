"""Inert declaration validation only. No CLI, I/O, execution or ownership attestation.

A model-valid result is never permission to perform an operation. Evidence values
are supplied declarations; this module cannot establish their truth.
"""
from dataclasses import dataclass, replace
from enum import Enum
import re


class Evidence(Enum):
    MISSING = 'MISSING'
    DECLARED = 'DECLARED'
    VERIFIED = 'VERIFIED'


class Approval(Enum):
    PENDING = 'PENDING'
    APPROVED = 'APPROVED'


class Category(Enum):
    WORKLOAD = 'WORKLOAD'
    HELPER = 'HOST_HELPER'
    SHARED = 'SHARED_INFRASTRUCTURE'
    THREAD = 'THREAD'
    TASK = 'KERNEL_TASK'
    FILE = 'TEMP_FILE'
    DIRECTORY = 'TEMP_DIRECTORY'
    SOCKET = 'UNIX_SOCKET'


class Lifecycle(Enum):
    DECLARED = 'DECLARED'
    ACTIVE = 'ACTIVE'
    STOPPED = 'STOPPED'
    DISPOSED = 'DISPOSED'


class Eligibility(Enum):
    UNKNOWN = 'UNKNOWN'
    ELIGIBLE = 'ELIGIBLE'
    PROHIBITED = 'PROHIBITED'


class Reason(Enum):
    INPUT = 'INVALID_INPUT'
    AUTHORIZATION = 'AUTHORIZATION_MISSING'
    RUN = 'RUN_ID_MISSING_OR_INVALID'
    RUN_REUSED = 'RUN_ID_REUSED'
    SYNTHETIC = 'SYNTHETIC_ONLY_REQUIRED'
    RUNTIME = 'RUNTIME_NOT_APPROVED'
    RUNTIME_QUALIFICATION = 'RUNTIME_UNQUALIFIED'
    IMAGE = 'IMAGE_NOT_APPROVED'
    DIGEST = 'IMMUTABLE_IMAGE_DIGEST_REQUIRED'
    HELPER_BUDGET = 'HELPER_BUDGET_NOT_APPROVED'
    THREAD_TASK = 'THREAD_TASK_BUDGET_NOT_APPROVED'
    WORKLOAD = 'WORKLOAD_LIMIT_OR_ROLE_VIOLATION'
    UNEXPECTED = 'UNCLASSIFIED_HELPER_OR_DESCENDANT'
    OWNERSHIP = 'OWNERSHIP_MISSING_OR_CONFLICTING'
    CONFLICT = 'RESOURCE_ID_CONFLICT'
    CROSS_RUN = 'CROSS_RUN_ADOPTION'
    PROVENANCE = 'CREATION_PROVENANCE_MISSING'
    SOCKET = 'SOCKET_OWNERSHIP_MISSING'
    TRANSITION = 'INVALID_LIFECYCLE_TRANSITION'
    CLEANUP = 'CLEANUP_ELIGIBILITY_AMBIGUOUS'
    CLEANUP_VERIFICATION = 'CLEANUP_VERIFICATION_MISSING'
    AGENT_TIME = 'AGENT_LIFETIME_EXCEEDED'
    PROBE_TIME = 'PROBE_DEADLINE_EXCEEDED'
    CUMULATIVE = 'CUMULATIVE_DEADLINE_EXCEEDED'
    DATA = 'SYNTHETIC_DATA_LIMIT_EXCEEDED'
    STORAGE = 'TEMPORARY_STORAGE_LIMIT_EXCEEDED'
    NETWORK = 'NETWORK_ISOLATION_UNVERIFIED'
    FILESYSTEM = 'FILESYSTEM_ISOLATION_UNVERIFIED'
    SECURITY = 'SECURITY_QUALIFICATION_MISSING'
    INDEPENDENT = 'INDEPENDENT_EVIDENCE_MISSING'


@dataclass(frozen=True)
class Result:
    reasons: tuple[Reason, ...]

    def __post_init__(self):
        if type(self.reasons) is not tuple or any(type(x) is not Reason for x in self.reasons):
            raise ValueError("INVALID_RESULT")

    @property
    def model_valid(self):
        return not self.reasons

    @property
    def operationally_authorized(self):
        return False


def result(*reasons):
    return Result(tuple(dict.fromkeys(reasons)))


def identifier(value):
    return type(value) is str and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}', value) is not None


def integer(value, minimum, maximum):
    return type(value) is int and minimum <= value <= maximum


@dataclass(frozen=True)
class Resource:
    run_id: str
    category: Category
    identity: str
    owner: str
    provenance: Evidence
    lifecycle: Lifecycle
    cleanup: Eligibility
    cleanup_verification: Evidence


@dataclass(frozen=True)
class Ledger:
    run_id: str
    owner: str
    entries: tuple[Resource, ...] = ()

    def __post_init__(self):
        if not identifier(self.run_id) or not identifier(self.owner) or type(self.entries) is not tuple:
            raise ValueError(Reason.INPUT.value)
        seen = set()
        for entry in self.entries:
            check = self._declaration(entry)
            if not check.model_valid:
                raise ValueError(check.reasons[0].value)
            if entry.identity in seen:
                raise ValueError(Reason.CONFLICT.value)
            seen.add(entry.identity)

    def _declaration(self, entry):
        if type(entry) is not Resource:
            return result(Reason.INPUT)
        if entry.run_id != self.run_id:
            return result(Reason.CROSS_RUN)
        if not identifier(entry.identity) or not identifier(entry.owner):
            return result(Reason.OWNERSHIP)
        if any(type(value) is not kind for value, kind in (
            (entry.category, Category), (entry.provenance, Evidence), (entry.lifecycle, Lifecycle),
            (entry.cleanup, Eligibility), (entry.cleanup_verification, Evidence))):
            return result(Reason.INPUT)
        if entry.category is not Category.SHARED and entry.owner != self.owner:
            return result(Reason.OWNERSHIP)
        if entry.category is Category.SHARED and entry.cleanup is not Eligibility.PROHIBITED:
            return result(Reason.CLEANUP)
        if entry.lifecycle is Lifecycle.DISPOSED and not (
            entry.provenance is Evidence.VERIFIED and entry.cleanup is Eligibility.ELIGIBLE
            and entry.cleanup_verification is Evidence.VERIFIED):
            return result(Reason.CLEANUP_VERIFICATION)
        return result()

    def declare(self, entry):
        check = self._declaration(entry)
        if not check.model_valid:
            return self, check
        if any(x.identity == entry.identity for x in self.entries):
            return self, result(Reason.CONFLICT)
        if entry.lifecycle is not Lifecycle.DECLARED:
            return self, result(Reason.TRANSITION)
        return replace(self, entries=self.entries + (entry,)), result()

    def cleanup_check(self, identity):
        entry = next((x for x in self.entries if x.identity == identity), None)
        if entry is None:
            return result(Reason.OWNERSHIP)
        reasons = []
        if entry.category is Category.SHARED or entry.cleanup is not Eligibility.ELIGIBLE:
            reasons.append(Reason.CLEANUP)
        if entry.provenance is not Evidence.VERIFIED:
            reasons.append(Reason.PROVENANCE)
        if entry.lifecycle is not Lifecycle.STOPPED:
            reasons.append(Reason.TRANSITION)
        if entry.cleanup_verification is not Evidence.VERIFIED:
            reasons.append(Reason.CLEANUP_VERIFICATION)
        if entry.category in (Category.FILE, Category.DIRECTORY, Category.SOCKET):
            processes = (Category.WORKLOAD, Category.HELPER, Category.THREAD, Category.TASK)
            if any(x.category in processes and x.lifecycle not in (Lifecycle.STOPPED, Lifecycle.DISPOSED)
                   for x in self.entries):
                reasons.append(Reason.TRANSITION)
        if entry.category in (Category.FILE, Category.DIRECTORY):
            if any(x.category is Category.SOCKET and (x.lifecycle not in (Lifecycle.STOPPED, Lifecycle.DISPOSED)
                   or x.cleanup_verification is not Evidence.VERIFIED) for x in self.entries):
                reasons.append(Reason.CLEANUP_VERIFICATION)
        return result(*reasons)

    def transition(self, identity, state, cleanup_verification):
        entry = next((x for x in self.entries if x.identity == identity), None)
        if entry is None:
            return self, result(Reason.OWNERSHIP)
        if type(state) is not Lifecycle or type(cleanup_verification) is not Evidence:
            return self, result(Reason.INPUT)
        next_state = {Lifecycle.DECLARED: Lifecycle.ACTIVE, Lifecycle.ACTIVE: Lifecycle.STOPPED,
                      Lifecycle.STOPPED: Lifecycle.DISPOSED}
        if next_state.get(entry.lifecycle) is not state:
            return self, result(Reason.TRANSITION)
        candidate = replace(entry, lifecycle=state, cleanup_verification=cleanup_verification)
        if state is Lifecycle.DISPOSED:
            check = self.cleanup_check(identity)
            if not check.model_valid:
                return self, check
        return replace(self, entries=tuple(candidate if x.identity == identity else x for x in self.entries)), result()


@dataclass(frozen=True)
class Accounting:
    # Pairs bind process identities to explicit conceptual roles.
    workloads: tuple[tuple[str, str], ...]
    helpers: tuple[str, ...]
    shared: tuple[str, ...]
    threads: int | None
    tasks: int | None
    unexpected: tuple[str, ...]


@dataclass(frozen=True)
class Limits:
    workload: int
    helper_approval: Approval
    helper_ceiling: int | None
    task_approval: Approval
    thread_ceiling: int | None
    task_ceiling: int | None
    agent_seconds: int
    shutdown_reserve: int
    probe_seconds: int
    cumulative_seconds: int
    synthetic_bytes: int
    temporary_bytes: int


@dataclass(frozen=True)
class Preflight:
    authorization: Approval
    run_id: str
    used_run_ids: frozenset[str]
    synthetic_only: bool
    runtime: str | None
    runtime_approval: Approval
    runtime_qualification: Evidence
    image_approval: Approval
    image_qualification: Evidence
    image_digest: str | None
    limits: Limits
    network_isolation: Evidence
    filesystem_isolation: Evidence
    socket_ownership: Evidence
    cleanup_requirements: Evidence
    security_qualification: Evidence
    independent_evidence: Evidence


def evaluate(config, accounting, ledger):
    """Validate declarations without observing resources or granting permission."""
    if type(config) is not Preflight or type(accounting) is not Accounting or type(ledger) is not Ledger:
        return result(Reason.INPUT)
    reasons = []
    def require(condition, reason):
        if not condition:
            reasons.append(reason)
    require(config.authorization is Approval.APPROVED, Reason.AUTHORIZATION)
    require(identifier(config.run_id), Reason.RUN)
    if type(config.used_run_ids) is not frozenset or any(not identifier(x) for x in config.used_run_ids):
        reasons.append(Reason.INPUT)
    else:
        require(config.run_id not in config.used_run_ids, Reason.RUN_REUSED)
    require(config.run_id == ledger.run_id, Reason.CROSS_RUN)
    require(config.synthetic_only is True, Reason.SYNTHETIC)
    require(identifier(config.runtime) and config.runtime_approval is Approval.APPROVED, Reason.RUNTIME)
    require(config.runtime_qualification is Evidence.VERIFIED, Reason.RUNTIME_QUALIFICATION)
    require(config.image_approval is Approval.APPROVED and config.image_qualification is Evidence.VERIFIED, Reason.IMAGE)
    require(type(config.image_digest) is str and re.fullmatch(r'sha256:[0-9a-f]{64}', config.image_digest) is not None, Reason.DIGEST)
    for field, reason in [('network_isolation', Reason.NETWORK), ('filesystem_isolation', Reason.FILESYSTEM),
                          ('socket_ownership', Reason.SOCKET), ('cleanup_requirements', Reason.CLEANUP_VERIFICATION),
                          ('security_qualification', Reason.SECURITY), ('independent_evidence', Reason.INDEPENDENT)]:
        require(getattr(config, field) is Evidence.VERIFIED, reason)
    limits = config.limits
    if type(limits) is not Limits:
        return result(*reasons, Reason.INPUT)
    require(type(limits.workload) is int and limits.workload == 3, Reason.WORKLOAD)
    require(limits.helper_approval is Approval.APPROVED and integer(limits.helper_ceiling, 0, 2**31-1), Reason.HELPER_BUDGET)
    require(limits.task_approval is Approval.APPROVED and integer(limits.thread_ceiling, 1, 2**31-1)
            and integer(limits.task_ceiling, 1, 2**31-1), Reason.THREAD_TASK)
    require(integer(limits.agent_seconds, 1, 60) and integer(limits.shutdown_reserve, 1, 15)
            and limits.shutdown_reserve < limits.agent_seconds, Reason.AGENT_TIME)
    require(integer(limits.probe_seconds, 1, 60) and type(limits.agent_seconds) is int
            and type(limits.shutdown_reserve) is int
            and limits.probe_seconds <= limits.agent_seconds - limits.shutdown_reserve, Reason.PROBE_TIME)
    require(integer(limits.cumulative_seconds, 1, 600), Reason.CUMULATIVE)
    require(integer(limits.synthetic_bytes, 1, 10*1024*1024), Reason.DATA)
    require(integer(limits.temporary_bytes, 1, 50*1024*1024), Reason.STORAGE)
    require(type(limits.synthetic_bytes) is int and type(limits.temporary_bytes) is int
            and limits.synthetic_bytes <= limits.temporary_bytes, Reason.STORAGE)
    if any(type(x) is not tuple for x in (accounting.workloads, accounting.helpers, accounting.shared, accounting.unexpected)):
        return result(*reasons, Reason.INPUT)
    require(not accounting.unexpected, Reason.UNEXPECTED)
    if any(type(x) is not tuple or len(x) != 2 or not all(identifier(v) for v in x) for x in accounting.workloads):
        return result(*reasons, Reason.INPUT)
    if any(not identifier(x) for x in accounting.helpers + accounting.shared):
        return result(*reasons, Reason.INPUT)
    roles = tuple(x[1] for x in accounting.workloads)
    require(len(roles) <= 3 and len(set(roles)) == len(roles)
            and {'controller', 'agent'} <= set(roles) <= {'controller', 'agent', 'probe'}, Reason.WORKLOAD)
    identities = tuple(x[0] for x in accounting.workloads) + accounting.helpers + accounting.shared
    require(len(identities) == len(set(identities)), Reason.CONFLICT)
    if integer(limits.helper_ceiling, 0, 2**31-1):
        require(len(accounting.helpers) <= limits.helper_ceiling, Reason.HELPER_BUDGET)
    require(integer(accounting.threads, 1, 2**31-1) and integer(accounting.tasks, 1, 2**31-1), Reason.THREAD_TASK)
    if all(type(x) is int for x in (accounting.threads, accounting.tasks, limits.thread_ceiling, limits.task_ceiling)):
        require(len(identities) <= accounting.threads <= accounting.tasks
                and accounting.threads <= limits.thread_ceiling and accounting.tasks <= limits.task_ceiling, Reason.THREAD_TASK)
    by_id = {x.identity: x for x in ledger.entries}
    for entries, category in ((tuple(x[0] for x in accounting.workloads), Category.WORKLOAD),
                              (accounting.helpers, Category.HELPER), (accounting.shared, Category.SHARED)):
        for identity in entries:
            entry = by_id.get(identity)
            require(entry is not None and entry.category is category, Reason.OWNERSHIP)
    declared = {x.identity for x in ledger.entries if x.category in (Category.WORKLOAD, Category.HELPER, Category.SHARED)}
    require(declared == set(identities), Reason.UNEXPECTED)
    require(any(x.category is Category.SOCKET for x in ledger.entries), Reason.SOCKET)
    for entry in ledger.entries:
        require(entry.provenance is Evidence.VERIFIED, Reason.PROVENANCE)
        require(entry.lifecycle is not Lifecycle.DISPOSED, Reason.TRANSITION)
        require(entry.cleanup is (Eligibility.PROHIBITED if entry.category is Category.SHARED else Eligibility.ELIGIBLE), Reason.CLEANUP)
    return result(*reasons)
