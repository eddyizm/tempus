package com.cappielloantonio.tempo.repository

import com.cappielloantonio.tempo.database.dao.DownloadDao
import com.cappielloantonio.tempo.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DownloadRepositoryTest {

    private lateinit var dao: DownloadDao
    private lateinit var repo: DownloadRepository

    @Before
    fun setup() {
        dao = mock()
        repo = DownloadRepository(dao)
    }

    // ── Regression guard: getDownload must surface finished/visible downloads ──
    // In this app state 0 = downloading, state 1 = completed/visible (see
    // DownloadDao.update which sets download_state = 1 on completion, and
    // getAll which filters WHERE download_state = 1). Dropping state 1 here
    // silently breaks local playback and export of downloaded files.
    @Test
    fun getDownload_returnsRowForCompletedState() {
        val download = Download("track-1").apply { downloadState = 1 }
        whenever(dao.getOne("track-1")).thenReturn(download)

        val result = repo.getDownload("track-1")

        assertNotNull(result)
        assertEquals("track-1", result!!.id)
        assertEquals(1, result.downloadState)
        verify(dao).getOne("track-1")
    }

    @Test
    fun getDownload_returnsRowForActiveDownloadingState() {
        val download = Download("track-2").apply { downloadState = 0 }
        whenever(dao.getOne("track-2")).thenReturn(download)

        assertNotNull(repo.getDownload("track-2"))
    }

    @Test
    fun getDownload_returnsNullWhenMissing() {
        whenever(dao.getOne("nope")).thenReturn(null)
        assertNull(repo.getDownload("nope"))
    }

    // getDownloadById is the unfiltered accessor used by the notification path;
    // it must return the row regardless of state (including post-completion).
    @Test
    fun getDownloadById_returnsRowRegardlessOfState() {
        val download = Download("track-3").apply { downloadState = 2 }
        whenever(dao.getOne("track-3")).thenReturn(download)

        val result = repo.getDownloadById("track-3")
        assertNotNull(result)
        assertEquals(2, result!!.downloadState)
    }

    @Test
    fun getAllDownloads_delegatesToGetAllSync() {
        val list = listOf(Download("a"), Download("b"))
        whenever(dao.getAllSync()).thenReturn(list)

        assertEquals(2, repo.getAllDownloads().size)
        verify(dao).getAllSync()
    }

    // The async thread-safe wrappers start a thread and do not join, so verify
    // with a timeout to wait for the background DAO call to land.
    @Test
    fun insert_delegatesToDao() {
        val d = Download("x")
        repo.insert(d)
        verify(dao, timeout(2000)).insert(d)
    }

    @Test
    fun update_delegatesToDao() {
        repo.update("x")
        verify(dao, timeout(2000)).update("x")
    }

    @Test
    fun delete_delegatesToDao() {
        repo.delete("x")
        verify(dao, timeout(2000)).delete("x")
    }

    @Test
    fun deleteAll_delegatesToDao() {
        repo.deleteAll()
        verify(dao, timeout(2000)).deleteAll()
    }

    @Test
    fun updateDownloadUri_delegatesToDao() {
        repo.updateDownloadUri("x", "uri")
        verify(dao, timeout(2000)).updateDownloadUri("x", "uri")
    }

    @Test
    fun delete_withIdList_delegatesToDao() {
        repo.delete(listOf("a", "b"))
        verify(dao, timeout(2000)).deleteByIds(listOf("a", "b"))
    }
}
