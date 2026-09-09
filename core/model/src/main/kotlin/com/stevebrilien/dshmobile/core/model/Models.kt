package com.stevebrilien.dshmobile.core.model

@JvmInline
value class ProjectId(val value: String)

@JvmInline
value class SessionId(val value: String)

@JvmInline
value class RuntimeId(val value: String)

enum class ProjectAccess {
    READ_ONLY,
    READ_WRITE,
}

data class Project(
    val id: ProjectId,
    val displayName: String,
    val path: String,
    val description: String? = null,
    val gitRemote: String? = null,
    val runtimeId: RuntimeId? = null,
)

data class AttachedProject(
    val projectId: ProjectId,
    val access: ProjectAccess = ProjectAccess.READ_ONLY,
)

data class SessionProjectContext(
    val sessionId: SessionId,
    val primaryProjectId: ProjectId,
    val attachedProjects: List<AttachedProject> = emptyList(),
) {
    init {
        require(attachedProjects.none { it.projectId == primaryProjectId }) {
            "Primary project must not also appear as an attached project"
        }
    }
}

enum class RuntimeKind {
    LOCAL_PHONE,
    ORANGE_PI,
    AZURE,
    OTHER_REMOTE,
}

data class RuntimeTarget(
    val id: RuntimeId,
    val displayName: String,
    val kind: RuntimeKind,
)
