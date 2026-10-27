package com.moyue.reader.data.repo

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 书籍分组标签：实体内以 JSON 数组字符串存储（兼容 Room 单列 + 备份序列化） */
object BookTags {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun parse(raw: String): List<String> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    fun serialize(tags: List<String>): String =
        json.encodeToString(serializer, tags.filter { it.isNotBlank() }.distinct())
}
