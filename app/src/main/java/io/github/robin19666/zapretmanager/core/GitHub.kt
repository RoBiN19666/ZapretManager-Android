package io.github.robin19666.zapretmanager.core

import org.json.JSONArray

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

data class Release(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val version: String get() = tag.trim().removePrefix("v").removePrefix("V")

    fun assetBySuffix(vararg suffixes: String): ReleaseAsset? {
        for (suffix in suffixes) {
            assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }?.let { return it }
        }
        return null
    }
}

object GitHub {
    private const val API = "https://api.github.com"

    fun latestRelease(repo: String, token: String? = null): Release? {
        val url = "$API/repos/$repo/releases/latest"
        return try {
            parseRelease(Net.getJson(url, token))
        } catch (e: HttpException) {
            if (e.code == 404) null else throw e
        }
    }

    fun releases(repo: String, token: String? = null, count: Int = 10): List<Release> {
        val array: JSONArray = Net.getJsonArray("$API/repos/$repo/releases?per_page=$count", token)
        return (0 until array.length()).mapNotNull { i ->
            runCatching { parseRelease(array.getJSONObject(i)) }.getOrNull()
        }
    }

    /** Последний коммит, затрагивающий путь: используется как версия файла-компонента. */
    fun pathCommit(repo: String, path: String, token: String? = null): Pair<String, String>? {
        val url = "$API/repos/$repo/commits?path=$path&per_page=1"
        return try {
            val array = Net.getJsonArray(url, token)
            if (array.length() == 0) return null
            val commit = array.getJSONObject(0)
            val sha = commit.getString("sha")
            val date = commit.getJSONObject("commit").getJSONObject("committer").optString("date")
            sha to date
        } catch (e: Exception) {
            LogBus.w("GitHub", "Не удалось получить коммит для $path: ${e.message}")
            null
        }
    }

    fun repoHeadSha(repo: String, branch: String = "main", token: String? = null): String? {
        return try {
            Net.getJson("$API/repos/$repo/commits/$branch", token).getString("sha")
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRelease(json: org.json.JSONObject): Release {
        val assetsJson = json.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until assetsJson.length()).map { i ->
            val a = assetsJson.getJSONObject(i)
            ReleaseAsset(
                name = a.optString("name"),
                url = a.optString("browser_download_url"),
                size = a.optLong("size"),
            )
        }
        return Release(
            tag = json.optString("tag_name"),
            name = json.optString("name").ifBlank { json.optString("tag_name") },
            body = json.optString("body"),
            htmlUrl = json.optString("html_url"),
            publishedAt = json.optString("published_at"),
            prerelease = json.optBoolean("prerelease"),
            assets = assets,
        )
    }
}
