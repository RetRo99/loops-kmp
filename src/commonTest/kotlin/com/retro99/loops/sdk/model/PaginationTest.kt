package com.retro99.loops.sdk.model

import com.retro99.loops.sdk.sdkJson
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaginationTest {

    @Test
    fun `Page decodes a representative envelope`() {
        val body = """{"pagination":{"totalResults":50,"perPage":20,"totalPages":3,"nextCursor":"abc"},"data":[{"id":"c1","name":"Campaign 1"}]}"""
        val page = sdkJson.decodeFromString<Page<CampaignItem>>(body)
        assertEquals(50, page.pagination.totalResults)
        assertEquals(20, page.pagination.perPage)
        assertEquals(3, page.pagination.totalPages)
        assertEquals("abc", page.pagination.nextCursor)
        assertNull(page.pagination.returnedResults)
        assertNull(page.pagination.nextPage)
        assertEquals(1, page.data.size)
        assertEquals("c1", page.data[0].id)
        assertEquals("Campaign 1", page.data[0].name)
    }

    @Test
    fun `Pagination accepts empty fields`() {
        val body = """{"pagination":{},"data":[]}"""
        val page = sdkJson.decodeFromString<Page<CampaignItem>>(body)
        assertNotNull(page.pagination)
        assertNull(page.pagination.totalResults)
        assertNull(page.pagination.perPage)
        assertEquals(0, page.data.size)
    }
}

@Serializable
private data class CampaignItem(
    val id: String,
    val name: String,
)
