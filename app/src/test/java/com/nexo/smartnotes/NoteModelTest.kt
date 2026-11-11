package com.nexo.smartnotes

import com.nexo.smartnotes.domain.Attachment
import com.nexo.smartnotes.domain.ChecklistItem
import com.nexo.smartnotes.domain.Note
import com.nexo.smartnotes.domain.NoteFilter
import com.nexo.smartnotes.domain.NoteType
import com.nexo.smartnotes.domain.ShareInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 领域模型单测。
 *
 * 这些是纯 Kotlin 逻辑（不依赖 Android / Room / 网络），
 * 覆盖搜索、权限、进度、校验等最容易被改坏的部分。
 */
class NoteModelTest {

    private fun note(
        id: String = "n1",
        type: NoteType = NoteType.TEXT,
        title: String = "",
        content: String = "",
        tags: List<String> = emptyList(),
        checklist: List<ChecklistItem> = emptyList(),
        shareRole: String? = null,
        location: String? = null,
        mood: String? = null,
        weather: String? = null,
    ) = Note(
        id = id,
        type = type,
        title = title,
        content = content,
        tags = tags,
        checklist = checklist,
        shareRole = shareRole,
        location = location,
        mood = mood,
        weather = weather,
    )

    /* ---------------------------------------------------- 搜索匹配 */

    @Test
    fun `空查询匹配所有笔记`() {
        val filter = NoteFilter()
        assertTrue(filter.isEmpty)
        assertTrue(filter.matches(note(title = "任意")))
    }

    @Test
    fun `标题命中`() {
        val f = NoteFilter(query = "课程")
        assertTrue(f.matches(note(title = "课程笔记")))
        assertFalse(f.matches(note(title = "别的")))
    }

    @Test
    fun `正文命中`() {
        val f = NoteFilter(query = "Room")
        assertTrue(f.matches(note(content = "用 Room 做持久化")))
    }

    @Test
    fun `标签命中`() {
        val f = NoteFilter(query = "期末")
        assertTrue(f.matches(note(tags = listOf("期末", "项目"))))
    }

    @Test
    fun `清单条目命中`() {
        val f = NoteFilter(query = "买牛奶")
        assertTrue(
            f.matches(
                note(
                    type = NoteType.CHECKLIST,
                    checklist = listOf(ChecklistItem(text = "买牛奶")),
                )
            )
        )
    }

    @Test
    fun `地点_心情_天气也能被搜到`() {
        assertTrue(NoteFilter(query = "杭州").matches(note(location = "杭州")))
        assertTrue(NoteFilter(query = "开心").matches(note(mood = "开心")))
        assertTrue(NoteFilter(query = "晴").matches(note(weather = "晴")))
    }

    @Test
    fun `多关键词需要全部命中`() {
        val f = NoteFilter(query = "课程 项目")
        assertTrue(f.matches(note(title = "课程项目总结")))
        assertFalse(f.matches(note(title = "课程笔记"))) // 缺「项目」
    }

    @Test
    fun `搜索大小写不敏感`() {
        assertTrue(NoteFilter(query = "room").matches(note(content = "ROOM")))
        assertTrue(NoteFilter(query = "Room").matches(note(content = "room")))
    }

    /* ---------------------------------------------------- 类型筛选 */

    @Test
    fun `按类型筛选`() {
        val f = NoteFilter(types = setOf(NoteType.DIARY))
        assertTrue(f.matches(note(type = NoteType.DIARY)))
        assertFalse(f.matches(note(type = NoteType.TEXT)))
    }

    @Test
    fun `多类型为或关系`() {
        val f = NoteFilter(types = setOf(NoteType.DIARY, NoteType.CHECKLIST))
        assertTrue(f.matches(note(type = NoteType.DIARY)))
        assertTrue(f.matches(note(type = NoteType.CHECKLIST)))
        assertFalse(f.matches(note(type = NoteType.TODO)))
    }

    @Test
    fun `只显示置顶`() {
        val f = NoteFilter(onlyPinned = true)
        assertTrue(f.matches(note().copy(pinned = true)))
        assertFalse(f.matches(note().copy(pinned = false)))
    }

    @Test
    fun `只显示他人分享给我的`() {
        val f = NoteFilter(onlyShared = true)
        assertTrue(f.matches(note(shareRole = ShareInfo.EDIT)))
        assertFalse(f.matches(note(shareRole = null)))
    }

    /* ---------------------------------------------------- 权限 */

    @Test
    fun `自己创建的笔记可编辑`() {
        val n = note(shareRole = null)
        assertTrue(n.isOwner)
        assertFalse(n.isSharedWithMe)
        assertTrue(n.canEdit)
    }

    @Test
    fun `被分享为可编辑时可以编辑`() {
        val n = note(shareRole = ShareInfo.EDIT)
        assertFalse(n.isOwner)
        assertTrue(n.isSharedWithMe)
        assertTrue(n.canEdit)
    }

    @Test
    fun `被分享为只读时不能编辑`() {
        val n = note(shareRole = ShareInfo.VIEW)
        assertFalse(n.canEdit)
    }

    /* ---------------------------------------------------- 进度 */

    @Test
    fun `清单进度计算`() {
        val n = note(
            type = NoteType.CHECKLIST,
            checklist = listOf(
                ChecklistItem(text = "a", done = true),
                ChecklistItem(text = "b", done = true),
                ChecklistItem(text = "c", done = false),
                ChecklistItem(text = "d", done = false),
            ),
        )
        assertEquals(2, n.completedCount)
        assertNotNull(n.progress)
        assertEquals(0.5f, n.progress!!, 0.001f)
    }

    @Test
    fun `普通笔记没有进度`() {
        assertNull(note(type = NoteType.TEXT).progress)
    }

    @Test
    fun `空清单没有进度`() {
        assertNull(note(type = NoteType.CHECKLIST, checklist = emptyList()).progress)
    }

    /* ---------------------------------------------------- 展示 */

    @Test
    fun `无标题时按类型兜底`() {
        assertEquals("无题日记", note(type = NoteType.DIARY).displayTitle)
        assertEquals("待办清单", note(type = NoteType.CHECKLIST).displayTitle)
        assertEquals("待办事项", note(type = NoteType.TODO).displayTitle)
        assertEquals("日程安排", note(type = NoteType.SCHEDULE).displayTitle)
        assertEquals("图文笔记", note(type = NoteType.IMAGE).displayTitle)
        assertEquals("无标题笔记", note(type = NoteType.TEXT).displayTitle)
    }

    @Test
    fun `有标题时优先显示标题`() {
        assertEquals("我的日记", note(type = NoteType.DIARY, title = "我的日记").displayTitle)
    }

    @Test
    fun `清单预览带勾选符号`() {
        val n = note(
            type = NoteType.CHECKLIST,
            checklist = listOf(
                ChecklistItem(text = "已完成", done = true),
                ChecklistItem(text = "未完成", done = false),
            ),
        )
        assertTrue(n.preview.contains("☑"))
        assertTrue(n.preview.contains("☐"))
    }

    @Test
    fun `图文无正文时显示图片数量`() {
        val n = note(type = NoteType.IMAGE).copy(
            attachments = listOf(Attachment(localPath = "/a.jpg"), Attachment(localPath = "/b.jpg"))
        )
        assertTrue(n.preview.contains("2"))
    }

    /* ---------------------------------------------------- 类型解析 */

    @Test
    fun `未知类型回退为普通笔记`() {
        assertEquals(NoteType.TEXT, NoteType.from("NOT_EXIST"))
        assertEquals(NoteType.TEXT, NoteType.from(null))
        assertEquals(NoteType.DIARY, NoteType.from("DIARY"))
    }

    @Test
    fun `六种类型都已注册`() {
        assertEquals(6, NoteType.entries.size)
        assertEquals(6, NoteType.creatable.size)
    }

    @Test
    fun `新建清单自带一个空条目`() {
        val n = Note.blank(NoteType.CHECKLIST)
        assertEquals(1, n.checklist.size)
    }

    @Test
    fun `新建日程自带起止时间`() {
        val n = Note.blank(NoteType.SCHEDULE)
        assertNotNull(n.startAt)
        assertNotNull(n.endAt)
        assertTrue(n.endAt!! > n.startAt!!)
    }

    @Test
    fun `新建普通笔记无清单无日程`() {
        val n = Note.blank(NoteType.TEXT)
        assertTrue(n.checklist.isEmpty())
        assertNull(n.startAt)
    }

    @Test
    fun `修改会把笔记标记为待同步`() {
        val n = note()
        val touched = n.touched(now = 123456L)
        assertEquals(123456L, touched.updatedAt)
        assertTrue(touched.dirty)
    }
}
