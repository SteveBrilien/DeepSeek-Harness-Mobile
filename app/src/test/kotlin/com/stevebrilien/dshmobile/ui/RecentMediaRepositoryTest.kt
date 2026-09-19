package com.stevebrilien.dshmobile.ui

import android.database.MatrixCursor
import android.provider.MediaStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RecentMediaRepositoryTest {
    private val columns = arrayOf(
        MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE,
    )

    @Test fun deniedPermissionNeverQueriesAndNeverReturnsCachedPhotos() {
        var permission = true
        var calls = 0
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { permission }) { _, _, _, _ ->
            calls++
            MatrixCursor(columns).apply { addRow(arrayOf<Any>(9L, 100L, "image/jpeg", 800L)) }
        }
        assertEquals(1, (repo.page() as RecentMediaRepository.Result.Items).images.size)
        permission = false
        assertEquals(RecentMediaRepository.Result.PermissionRequired, repo.page())
        assertEquals(1, calls)
    }

    @Test fun limitIsBoundedAndInvalidDataNeverEntersPreview() {
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { true }) { _, _, _, order ->
            assertEquals("date_added DESC, _id DESC", order)
            MatrixCursor(columns).apply {
                addRow(arrayOf<Any>(22L, 200L, "image/png", 2000L))
                addRow(arrayOf<Any>(21L, 199L, "text/plain", 1800L))
                addRow(arrayOf<Any>(20L, 198L, "image/jpeg", 0L))
                addRow(arrayOf<Any>(19L, 197L, "image/webp", 450L))
            }
        }
        assertThrows(IllegalArgumentException::class.java) { repo.page(limit = 21) }
        val items = repo.page(limit = 2) as RecentMediaRepository.Result.Items
        assertEquals(listOf(22L, 19L), items.images.map { it.id })
        assertEquals("content://media/external/images/media/22", items.images[0].uri.toString())
        assertEquals(RecentMediaRepository.Anchor(197L, 19L), items.next)
    }

    @Test fun seekPagingUsesStableDateAndIdAndHandlesRevocationDuringQuery() {
        var expected = 0
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { true }) { _, where, args, _ ->
            expected++
            assertEquals("(date_added < ? OR (date_added = ? AND _id < ?))", where)
            assertArrayEquals(arrayOf("168", "168", "77"), args)
            throw SecurityException("provider permission revoked")
        }
        assertEquals(RecentMediaRepository.Result.PermissionRequired,
            repo.page(after = RecentMediaRepository.Anchor(168,77)))
        assertEquals(1, expected)
    }

    @Test fun missingProviderIsUnavailableNotAFalseEmptyGallery() {
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { true }) { _, _, _, _ -> null }
        assertEquals(RecentMediaRepository.Result.Unavailable, repo.page())
    }

    @Test fun unsupportedRowsHaveBoundedScanAndPaginationMakesProgress() {
        var calls = 0
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { true }) { _, where, args, _ ->
            calls++
            MatrixCursor(columns).apply {
                if (calls == 1) {
                    assertNull(where)
                    for (id in 100L downTo 1L) addRow(arrayOf<Any>(id, id, "text/plain", 400L))
                } else {
                    assertEquals("(date_added < ? OR (date_added = ? AND _id < ?))", where)
                    assertArrayEquals(arrayOf("93", "93", "93"), args)
                    addRow(arrayOf<Any>(92L, 92L, "image/jpeg", 400L))
                    addRow(arrayOf<Any>(91L, 91L, "image/png", 500L))
                }
            }
        }
        val first = repo.page(limit = 2) as RecentMediaRepository.Result.Items
        assertTrue(first.images.isEmpty())
        // The first 8 scanned invalid rows advance the cursor, not all 100.
        assertEquals(RecentMediaRepository.Anchor(93, 93), first.next)
        val second = repo.page(after = first.next, limit = 2) as RecentMediaRepository.Result.Items
        assertEquals(listOf(92L, 91L), second.images.map { it.id })
        assertEquals(RecentMediaRepository.Anchor(91, 91), second.next)
        assertEquals(2, calls)
    }

    @Test fun permissionRevokedAfterQueryNeverLeaksCollectedImages() {
        var permission = true
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { permission }) { _, _, _, _ ->
            MatrixCursor(columns).apply { addRow(arrayOf<Any>(5L, 7L, "image/jpeg", 500L)) }
                .also { permission = false }
        }
        assertEquals(RecentMediaRepository.Result.PermissionRequired, repo.page())
    }

    @Test fun corruptSeekKeyFailsClosedRatherThanReturningUnpageableImages() {
        val repo = RecentMediaRepository(RuntimeEnvironment.getApplication(), { true }) { _, _, _, _ ->
            MatrixCursor(columns).apply { addRow(arrayOf<Any>(0L, 9L, "image/jpeg", 500L)) }
        }
        assertEquals(RecentMediaRepository.Result.Unavailable, repo.page())
    }
}
