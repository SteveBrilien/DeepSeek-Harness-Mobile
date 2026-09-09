package com.stevebrilien.dshmobile.core.dshapi

import com.stevebrilien.dshmobile.core.model.ProjectAccess
import com.stevebrilien.dshmobile.core.model.ProjectId
import com.stevebrilien.dshmobile.core.model.SessionId

data class DshEndpoint(
    val httpUrl: String,
    val websocketUrl: String? = null,
)

data class DshHealth(
    val live: Boolean,
    val ready: Boolean,
    val version: String? = null,
    val detail: String? = null,
)

data class ProjectContextEntry(
    val projectId: ProjectId,
    val displayName: String,
    val resolvedPath: String,
    val access: ProjectAccess,
    val primary: Boolean,
)

data class ProjectContextSnapshot(
    val sessionId: SessionId,
    val projects: List<ProjectContextEntry>,
)

sealed interface MentionReference {
    data class Project(val projectId: ProjectId) : MentionReference
    data class File(val stableUri: String) : MentionReference
    data class Session(val sessionId: SessionId) : MentionReference
}

interface DshBridge {
    suspend fun health(): DshHealth
    suspend fun endpoint(): DshEndpoint?
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun projectContext(sessionId: SessionId): ProjectContextSnapshot?
}
