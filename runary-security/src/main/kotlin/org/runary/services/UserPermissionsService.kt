package org.runary.services

import com.github.benmanes.caffeine.cache.Caffeine
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.RowView
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class UserPermissions(
    val userId: String,
    val canManageLibraries: Boolean = true,
    val canUploadBooks: Boolean = true,
    val canDownloadBooks: Boolean = true,
    val canDeleteBooks: Boolean = false,
    val canEditMetadata: Boolean = true,
    val canManageBookmarks: Boolean = true,
    val canManageAnnotations: Boolean = true,
    val canManageReadingProgress: Boolean = true,
    val canManageShelves: Boolean = true,
    val canExportBooks: Boolean = true,
    val canSendToDevice: Boolean = true,
    val canUseKoboSync: Boolean = true,
    val canUseKoreaderSync: Boolean = true,
    val canUseOpds: Boolean = true,
    val canUseApiTokens: Boolean = true,
    val canManageJournal: Boolean = true,
    val canManageReadingSessions: Boolean = true,
    val canViewStats: Boolean = true,
    val canEditProfile: Boolean = true,
    val canChangePassword: Boolean = true,
    val canChangeEmail: Boolean = true,
    val canUseSearchFilters: Boolean = true,
    val canViewAuditLog: Boolean = false,
    val canManageNotebooks: Boolean = true,
    val canManageFonts: Boolean = true,
    val canAccessAdminPanel: Boolean = false,
)

class UserPermissionsService(
    private val jdbi: Jdbi,
) {
    private val cache =
        Caffeine
            .newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<UUID, UserPermissions>()

    /** Returns permissions for a user, creating default row if not yet set. */
    fun getPermissions(userId: UUID): UserPermissions = cache.get(userId) { loadPermissions(it) }

    private fun loadPermissions(userId: UUID): UserPermissions {
        val existing =
            jdbi.withHandle<UserPermissions?, Exception> { h ->
                h
                    .createQuery("SELECT * FROM user_permissions WHERE user_id = ?")
                    .bind(0, userId.toString())
                    .map { row -> mapPermissions(row) }
                    .firstOrNull()
            }
        if (existing != null) return existing

        // Create default row
        val defaults = UserPermissions(userId = userId.toString())
        insertPermissions(defaults)
        return defaults
    }

    /** Upserts the full permission set for a user. */
    fun setPermissions(
        userId: UUID,
        permissions: UserPermissions,
    ): UserPermissions {
        val existing =
            jdbi.withHandle<Boolean, Exception> { h ->
                h
                    .createQuery("SELECT COUNT(*) FROM user_permissions WHERE user_id = ?")
                    .bind(0, userId.toString())
                    .mapTo(Int::class.javaObjectType)
                    .firstOrNull()!! > 0
            }
        if (existing) {
            jdbi.useHandle<Exception> { h ->
                h
                    .createUpdate(
                        """
                    UPDATE user_permissions SET
                        can_manage_libraries = ?, can_upload_books = ?, can_download_books = ?,
                        can_delete_books = ?, can_edit_metadata = ?, can_manage_bookmarks = ?,
                        can_manage_annotations = ?, can_manage_reading_progress = ?, can_manage_shelves = ?,
                        can_export_books = ?, can_send_to_device = ?, can_use_kobo_sync = ?,
                        can_use_koreader_sync = ?, can_use_opds = ?, can_use_api_tokens = ?,
                        can_manage_journal = ?, can_manage_reading_sessions = ?, can_view_stats = ?,
                        can_edit_profile = ?, can_change_password = ?, can_change_email = ?,
                        can_use_search_filters = ?, can_view_audit_log = ?,
                        can_manage_notebooks = ?, can_manage_fonts = ?, can_access_admin_panel = ?,
                        updated_at = ?
                    WHERE user_id = ?
                """,
                    ).bindAll(permissions, userId)
                    .execute()
            }
        } else {
            insertPermissions(permissions.copy(userId = userId.toString()))
        }
        cache.invalidate(userId)
        return getPermissions(userId)
    }

    private fun insertPermissions(p: UserPermissions) {
        jdbi.useHandle<Exception> { h ->
            h
                .createUpdate(
                    """
                INSERT INTO user_permissions (
                    user_id, can_manage_libraries, can_upload_books, can_download_books,
                    can_delete_books, can_edit_metadata, can_manage_bookmarks,
                    can_manage_annotations, can_manage_reading_progress, can_manage_shelves,
                    can_export_books, can_send_to_device, can_use_kobo_sync,
                    can_use_koreader_sync, can_use_opds, can_use_api_tokens,
                    can_manage_journal, can_manage_reading_sessions, can_view_stats,
                    can_edit_profile, can_change_password, can_change_email,
                    can_use_search_filters, can_view_audit_log,
                    can_manage_notebooks, can_manage_fonts, can_access_admin_panel, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
                ).bind(0, p.userId)
                .bind(1, p.canManageLibraries)
                .bind(2, p.canUploadBooks)
                .bind(3, p.canDownloadBooks)
                .bind(4, p.canDeleteBooks)
                .bind(5, p.canEditMetadata)
                .bind(6, p.canManageBookmarks)
                .bind(7, p.canManageAnnotations)
                .bind(8, p.canManageReadingProgress)
                .bind(9, p.canManageShelves)
                .bind(10, p.canExportBooks)
                .bind(11, p.canSendToDevice)
                .bind(12, p.canUseKoboSync)
                .bind(13, p.canUseKoreaderSync)
                .bind(14, p.canUseOpds)
                .bind(15, p.canUseApiTokens)
                .bind(16, p.canManageJournal)
                .bind(17, p.canManageReadingSessions)
                .bind(18, p.canViewStats)
                .bind(19, p.canEditProfile)
                .bind(20, p.canChangePassword)
                .bind(21, p.canChangeEmail)
                .bind(22, p.canUseSearchFilters)
                .bind(23, p.canViewAuditLog)
                .bind(24, p.canManageNotebooks)
                .bind(25, p.canManageFonts)
                .bind(26, p.canAccessAdminPanel)
                .bind(27, Instant.now().toString())
                .execute()
        }
    }

    private fun mapPermissions(row: RowView): UserPermissions {
        fun bool(col: String) = row.getColumn(col, java.lang.Boolean::class.java)?.booleanValue() ?: false

        fun boolD(
            col: String,
            default: Boolean,
        ): Boolean =
            try {
                row.getColumn(col, java.lang.Boolean::class.java)?.booleanValue() ?: default
            } catch (_: Exception) {
                default
            }
        return UserPermissions(
            userId = row.getColumn("user_id", String::class.java),
            canManageLibraries = boolD("can_manage_libraries", true),
            canUploadBooks = boolD("can_upload_books", true),
            canDownloadBooks = boolD("can_download_books", true),
            canDeleteBooks = bool("can_delete_books"),
            canEditMetadata = boolD("can_edit_metadata", true),
            canManageBookmarks = boolD("can_manage_bookmarks", true),
            canManageAnnotations = boolD("can_manage_annotations", true),
            canManageReadingProgress = boolD("can_manage_reading_progress", true),
            canManageShelves = boolD("can_manage_shelves", true),
            canExportBooks = boolD("can_export_books", true),
            canSendToDevice = boolD("can_send_to_device", true),
            canUseKoboSync = boolD("can_use_kobo_sync", true),
            canUseKoreaderSync = boolD("can_use_koreader_sync", true),
            canUseOpds = boolD("can_use_opds", true),
            canUseApiTokens = boolD("can_use_api_tokens", true),
            canManageJournal = boolD("can_manage_journal", true),
            canManageReadingSessions = boolD("can_manage_reading_sessions", true),
            canViewStats = boolD("can_view_stats", true),
            canEditProfile = boolD("can_edit_profile", true),
            canChangePassword = boolD("can_change_password", true),
            canChangeEmail = boolD("can_change_email", true),
            canUseSearchFilters = boolD("can_use_search_filters", true),
            canViewAuditLog = bool("can_view_audit_log"),
            canManageNotebooks = boolD("can_manage_notebooks", true),
            canManageFonts = boolD("can_manage_fonts", true),
            canAccessAdminPanel = bool("can_access_admin_panel"),
        )
    }
}

// Extension for clean UPDATE binding
private fun org.jdbi.v3.core.statement.Update.bindAll(
    p: UserPermissions,
    userId: UUID,
): org.jdbi.v3.core.statement.Update =
    this
        .bind(0, p.canManageLibraries)
        .bind(1, p.canUploadBooks)
        .bind(2, p.canDownloadBooks)
        .bind(3, p.canDeleteBooks)
        .bind(4, p.canEditMetadata)
        .bind(5, p.canManageBookmarks)
        .bind(6, p.canManageAnnotations)
        .bind(7, p.canManageReadingProgress)
        .bind(8, p.canManageShelves)
        .bind(9, p.canExportBooks)
        .bind(10, p.canSendToDevice)
        .bind(11, p.canUseKoboSync)
        .bind(12, p.canUseKoreaderSync)
        .bind(13, p.canUseOpds)
        .bind(14, p.canUseApiTokens)
        .bind(15, p.canManageJournal)
        .bind(16, p.canManageReadingSessions)
        .bind(17, p.canViewStats)
        .bind(18, p.canEditProfile)
        .bind(19, p.canChangePassword)
        .bind(20, p.canChangeEmail)
        .bind(21, p.canUseSearchFilters)
        .bind(22, p.canViewAuditLog)
        .bind(23, p.canManageNotebooks)
        .bind(24, p.canManageFonts)
        .bind(25, p.canAccessAdminPanel)
        .bind(26, Instant.now().toString())
        .bind(27, userId.toString())
