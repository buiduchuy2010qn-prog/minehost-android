package com.mineserve.mobile.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import com.mineserve.mobile.data.BoundedInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Bộ nạp biểu tượng Mod & Plugin thông minh:
 * 1. Bộ nhớ đệm RAM (LruCache 8MB) + Bộ nhớ đệm đĩa (.png) phản hồi <1ms.
 * 2. Trích xuất trực tiếp tệp ảnh biểu tượng nhúng bên trong tệp .jar (fabric.mod.json, quilt.mod.json,
 *    META-INF/mods.toml, META-INF/neoforge.mods.toml, mcmod.info, assets/<modid>/icon.png, logo.png, pack.png).
 * 3. Bảng ánh xạ sẵn 70+ Mod & Plugin thông dụng trên Modrinth + tra cứu tự động theo mã băm SHA-1 / Slug Modrinth.
 */
internal object AddonIconLoader {
    private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private val slots = Semaphore(6)
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val failures = LruCache<String, Long>(128)
    private val runtimeIconUrls = ConcurrentHashMap<String, String>()

    private val KNOWN_MODRINTH_ICON_URLS: Map<String, String> = mapOf(
        "ledger" to "https://cdn.modrinth.com/data/LVN9ygNV/919c0c39af397c4e62b6980018e31e9f763bf6d5_96.webp",
        "macaws-holidays" to "https://cdn.modrinth.com/data/rH20L2Lp/b6075bf7698016577b1329d4b048462ff6f9b0d3_96.webp",
        "shuttfup" to "https://cdn.modrinth.com/data/Rg9WdvvR/e63d193687394d5a9bedba0dcc55638ca70050f9_96.webp",
        "more-delight" to "https://cdn.modrinth.com/data/znHQQtuU/a0b97c0306ac9507bca7b81059fb146051596e08.gif",
        "architectury-api" to "https://cdn.modrinth.com/data/lhGA9TYQ/05fe3a61c28faaccaec3533b92e1b321edde7bf6_96.webp",
        "macaws-furniture" to "https://cdn.modrinth.com/data/dtWC90iB/2d2fdc39e9ab4c1cc56c6ce2785b6d60343bb86d_96.webp",
        "macaws-bridges" to "https://cdn.modrinth.com/data/GURcjz8O/5cd0895fdcde03f1d970ef910dd9287fb881bdc6_96.webp",
        "macaws-lights-and-lamps" to "https://cdn.modrinth.com/data/w4an97C2/23b3786d03ab6ed8b8df056d1123f20129175b6b_96.webp",
        "3d-placeable-food" to "https://cdn.modrinth.com/data/MFgnFY8Z/0e88dbd6d9f876dc8af760ba6bde1e54470b285a_96.webp",
        "clumps" to "https://cdn.modrinth.com/data/Wnxd13zP/6a965bb7974c3e759a53a1c89c35de4acd4cf86a_96.webp",
        "rustic-delight" to "https://cdn.modrinth.com/data/foa4fGIH/eecc99e281522f2291081c48176f0faa84c107bc.png",
        "modernfix" to "https://cdn.modrinth.com/data/nmDcB62a/2af94de5e08ae54567ee86b968fc7ce076d9fee5_96.webp",
        "simple-voice-chat" to "https://cdn.modrinth.com/data/9eGKb6K1/icon.png",
        "krypton" to "https://cdn.modrinth.com/data/fQEb0iXm/3ea60899d060a9286e03b87bfa9e71d0cbe2dde7_96.webp",
        "spark" to "https://cdn.modrinth.com/data/l6YH9Als/61a777dd08a8447ac93e8b6372e6d27d48cd1e1a_96.webp",
        "macaws-trapdoors" to "https://cdn.modrinth.com/data/n2fvCDlM/e764bf33a0a9d94fd33af84a63d8a47da7c85561_96.webp",
        "emotecraft" to "https://cdn.modrinth.com/data/pZ2wrerK/eed7e2c9851392e5879c7d7cb763f142f124e6d2_96.webp",
        "polymer" to "https://cdn.modrinth.com/data/xGdtZczs/icon.png",
        "macaws-roofs" to "https://cdn.modrinth.com/data/B8jaH3P1/e6652e3ddfe1cc21188c7bf354a4570292791864_96.webp",
        "vmp-fabric" to "https://cdn.modrinth.com/data/wnEe9KBa/0bb4b66e80c6cca9c37f6f1021a94670d6f820dc_96.webp",
        "sodium" to "https://cdn.modrinth.com/data/AANobbMI/295862f4724dc3f78df3447ad6072b2dcd3ef0c9_96.webp",
        "worldedit" to "https://cdn.modrinth.com/data/1u6JkXh5/30698991048ced77e60c4e8284007d3782f2e6a3_96.webp",
        "debugify" to "https://cdn.modrinth.com/data/QwxR6Gcd/d16ec025a7f2d9908248bb4d1477e3e624a2f17f_96.webp",
        "viaversion" to "https://cdn.modrinth.com/data/P1OZGk5p/ad14260a7308dc9e4c3385f3f6b5bdabfe17f295_96.webp",
        "macaws-paintings" to "https://cdn.modrinth.com/data/okE6QVAY/c3899cf3bd5b780839e98257492a6b68372eca3d_96.webp",
        "macaws-doors" to "https://cdn.modrinth.com/data/kNxa8z3e/ced8fa00264400cbe06d622b92a6d1106cedc65d_96.webp",
        "macaws-fences-and-walls" to "https://cdn.modrinth.com/data/GmwLse2I/ebe4d2375e0a402e5587665ba9da5eb50f37422a_96.webp",
        "litematica" to "https://cdn.modrinth.com/data/bEpr0Arc/25b5529d7a3b030ac136a6ce879d8ed2a1aa4a8d.png",
        "quickleafdecay" to "https://cdn.modrinth.com/data/3Puj7Mln/0a7715807c7a204069b822a06ed1b4f31d800c38_96.webp",
        "geyser" to "https://cdn.modrinth.com/data/wKkoqHrH/6e8e781d5669ecef9e19a73b5c2eed060d6f73e7_96.webp",
        "modernfix-mvus" to "https://cdn.modrinth.com/data/TjSm1wrD/e1d2433476995be1b1a94ff1afaf6167752274ae_96.webp",
        "viabackwards" to "https://cdn.modrinth.com/data/NpvuJQoq/c9fd86f343657c62206d5b82fee1d2b24f0b278d_96.webp",
        "cooking-for-blockheads" to "https://cdn.modrinth.com/data/vJnhuDde/811d0c55230d84d9e182df1b0bd631ddebecc0ad_96.webp",
        "chunky" to "https://cdn.modrinth.com/data/fALzjamp/e1954413665e57b7bae1feef44eda530270c7d47_96.webp",
        "waystones" to "https://cdn.modrinth.com/data/LOpKHB2A/d56c030a875582aff5bab477ddcfec317a1610f0_96.webp",
        "entityculling" to "https://cdn.modrinth.com/data/NNAgCjsB/7873452d6cede4daed12da3d7d8c193ab88b4fd6_96.webp",
        "macaws-stairs" to "https://cdn.modrinth.com/data/iP3wH1ha/7760df8839c395ee42099387cd9cb0fe34b2626e_96.webp",
        "macaws-windows" to "https://cdn.modrinth.com/data/C7I0BCni/7eb5e7816f887154e69a4001bee5bcc143242b32_96.webp",
        "floodgate" to "https://cdn.modrinth.com/data/bWrNNfkb/851eeb9b5daf37baaebe7e8f25e8437735897c9c_96.webp",
        "furnish-furniture" to "https://cdn.modrinth.com/data/5cWCtJmf/627b54435aa23cbe284617851baf9c55af21e0f6_96.webp",
        "alternate-current" to "https://cdn.modrinth.com/data/r0v8vy1s/fbb5a094676807df4b5ca1686033d5eb5fc389fd.png",
        "rightclickharvest" to "https://cdn.modrinth.com/data/Cnejf5xM/b6e535989217836c2adc49b06f4b3c8f429afda3_96.webp",
        "noisiumforked" to "https://cdn.modrinth.com/data/hasdd01q/6217fb8bc818c19fdd461a5d4e13ffbbbc95db6b_96.webp",
        "ferrite-core" to "https://cdn.modrinth.com/data/uXXizFIs/222a126f26f8f9ae1eb339f3b767677f18bff31f_96.webp",
        "c2me-fabric" to "https://cdn.modrinth.com/data/VSNURh3q/3c2ce471054466712a44c8758a03e03bb868f93b_96.webp",
        "cloth-config" to "https://cdn.modrinth.com/data/9s6osm5g/ed8a2316cbb6f4fc5f510e8e13a59a85cbbbff4d_96.webp",
        "farmers-delight-refabricated" to "https://cdn.modrinth.com/data/7vxePowz/26e8448993e9bda4dba92b6e7a1a13d9c4333138.png",
        "jamlib" to "https://cdn.modrinth.com/data/IYY9Siz8/7585f4d35b18467c2d0d9c496e3b7cd4919c413a_96.webp",
        "ubes-delight" to "https://cdn.modrinth.com/data/PYjpoAys/747de679aefe6e668c153745f441ff1e16597907.png",
        "luckperms" to "https://cdn.modrinth.com/data/Vebnzrzj/90943902cc650e95b167265b8f2d7c893f82c8f4_96.webp",
        "fabric-api" to "https://cdn.modrinth.com/data/P7dR8mSH/icon.png",
        "lithium" to "https://cdn.modrinth.com/data/gvQqBUqZ/bcc8686c13af0143adf4285d741256af824f70b7_96.webp",
        "appleskin" to "https://cdn.modrinth.com/data/EsAfCjCV/icon.png",
        "balm" to "https://cdn.modrinth.com/data/MBAkmtvl/285b7bcfd6e525c043e640b08f3efc0cde90f7dd_96.webp",
        "forge-config-api-port" to "https://cdn.modrinth.com/data/ohNO6lps/cb3b942cc18a66a0f35f802e004713f134e46cc2_96.webp",
        "yacl" to "https://cdn.modrinth.com/data/1eAoo2KR/08c0cd32515e260f4bb20bbc0696510041523f9a_96.webp",
        "noisium" to "https://cdn.modrinth.com/data/KuNKN7d2/7490b4d8e08bde8df44b2a71774edf2fe3176c83_96.webp",
        "crate-delight" to "https://cdn.modrinth.com/data/9rlXSyLg/1451f1af5cc41240ae8bbb166727bd0bdab5c5ab.png",
        "ksyxis" to "https://cdn.modrinth.com/data/2ecVyZ49/e7c3867d7a7da57f987bc0d6d37de88020c626a5_96.webp",
        "animal-garden-mouse" to "https://cdn.modrinth.com/data/Mm8Kzdc6/3fc2f59ab1cf9db4fb1b0e3a745a0cc1b9d8c807_96.webp",
        "servercore" to "https://cdn.modrinth.com/data/4WWQxlQP/5207e4657a1d0e929506a8c07491a42fa73b0a29_96.webp",
        "threadtweak" to "https://cdn.modrinth.com/data/vSEH1ERy/a23fc8c2cbeadbea6df1957d3712eac571a56fcb_96.webp",
        "chefs-delight" to "https://cdn.modrinth.com/data/pvcsfne4/4c31bd820651aaea721c87186a1007a415f1311e_96.webp",
        "animal-garden-manatee" to "https://cdn.modrinth.com/data/XzTcXQrj/997f1d33462ab49da04cf52eca9e3133c043cd4e_96.webp",
        "puzzles-lib" to "https://cdn.modrinth.com/data/QAGBst4M/c78216c61f65b6ce82593e4e92e9c358402bb524_96.webp",
        "toms-mobs" to "https://cdn.modrinth.com/data/PXaH6GuW/17d08c4e1d65e1add8d492fc3ede9e02a5bcc023_96.webp",
        "player-animation-library" to "https://cdn.modrinth.com/data/ha1mEyJS/c1fae260a9377cfb05692f01135b6ca2b7b682c7_96.webp",
        "malilib" to "https://cdn.modrinth.com/data/GcWjdA9I/a530ae55df5e0c405f5cf1b3e4fd6163a398bdc3.png",
        "macaws-paths-and-pavings" to "https://cdn.modrinth.com/data/VRLhWB91/4564a530eaf62d3704796e407f700dc1f1fc22f8_96.webp",
        "iris" to "https://cdn.modrinth.com/data/YL57xq9U/18d0e7f076d3d6ed5bedd472b853909aac5da202_96.webp"
    )

    private val PREFIX_TO_KNOWN_SLUG: List<Pair<String, String>> = listOf(
        "3d-placeable-food" to "3d-placeable-food",
        "alternate-current" to "alternate-current",
        "alternate_current" to "alternate-current",
        "animalgarden_manatee" to "animal-garden-manatee",
        "animal-garden-manatee" to "animal-garden-manatee",
        "animalgarden_mouse" to "animal-garden-mouse",
        "animal-garden-mouse" to "animal-garden-mouse",
        "appleskin" to "appleskin",
        "architectury" to "architectury-api",
        "balm" to "balm",
        "c2me" to "c2me-fabric",
        "chefs-delight" to "chefs-delight",
        "chefsdelight" to "chefs-delight",
        "chunky" to "chunky",
        "cloth-config" to "cloth-config",
        "cloth_config" to "cloth-config",
        "cookingforblockheads" to "cooking-for-blockheads",
        "cooking-for-blockheads" to "cooking-for-blockheads",
        "cratedelight" to "crate-delight",
        "crate-delight" to "crate-delight",
        "debugify" to "debugify",
        "emotecraft" to "emotecraft",
        "entityculling" to "entityculling",
        "fabric-api" to "fabric-api",
        "farmersdelight" to "farmers-delight-refabricated",
        "farmers-delight" to "farmers-delight-refabricated",
        "ferritecore" to "ferrite-core",
        "ferrite-core" to "ferrite-core",
        "forgeconfigapiport" to "forge-config-api-port",
        "forge-config-api-port" to "forge-config-api-port",
        "furnish" to "furnish-furniture",
        "jamlib" to "jamlib",
        "ksyxis" to "ksyxis",
        "litematica" to "litematica",
        "lithium" to "lithium",
        "malilib" to "malilib",
        "mcw-bridges" to "macaws-bridges",
        "macaws-bridges" to "macaws-bridges",
        "mcw-doors" to "macaws-doors",
        "macaws-doors" to "macaws-doors",
        "mcw-fences" to "macaws-fences-and-walls",
        "macaws-fences" to "macaws-fences-and-walls",
        "mcw-furniture" to "macaws-furniture",
        "macaws-furniture" to "macaws-furniture",
        "mcw-holidays" to "macaws-holidays",
        "macaws-holidays" to "macaws-holidays",
        "mcw-lights" to "macaws-lights-and-lamps",
        "macaws-lights" to "macaws-lights-and-lamps",
        "mcw-paintings" to "macaws-paintings",
        "macaws-paintings" to "macaws-paintings",
        "mcw-paths" to "macaws-paths-and-pavings",
        "macaws-paths" to "macaws-paths-and-pavings",
        "mcw-roofs" to "macaws-roofs",
        "macaws-roofs" to "macaws-roofs",
        "mcw-stairs" to "macaws-stairs",
        "macaws-stairs" to "macaws-stairs",
        "mcw-trapdoors" to "macaws-trapdoors",
        "macaws-trapdoors" to "macaws-trapdoors",
        "mcw-windows" to "macaws-windows",
        "macaws-windows" to "macaws-windows",
        "modernfix" to "modernfix-mvus",
        "moredelight" to "more-delight",
        "more-delight" to "more-delight",
        "noisium" to "noisiumforked",
        "playeranimationlib" to "player-animation-library",
        "player-animation-library" to "player-animation-library",
        "polymer" to "polymer",
        "puzzleslib" to "puzzles-lib",
        "puzzles-lib" to "puzzles-lib",
        "quickleafdecay" to "quickleafdecay",
        "rightclickharvest" to "rightclickharvest",
        "rusticdelight" to "rustic-delight",
        "rustic-delight" to "rustic-delight",
        "servercore" to "servercore",
        "sodium" to "sodium",
        "stfu" to "shuttfup",
        "shuttfup" to "shuttfup",
        "threadtweak" to "threadtweak",
        "toms_mobs" to "toms-mobs",
        "toms-mobs" to "toms-mobs",
        "ubesdelight" to "ubes-delight",
        "ubes-delight" to "ubes-delight",
        "voicechat" to "simple-voice-chat",
        "simple-voice-chat" to "simple-voice-chat",
        "waystones" to "waystones",
        "yet_another_config_lib" to "yacl",
        "yacl" to "yacl",
        "krypton" to "krypton",
        "vmp" to "vmp-fabric",
        "clumps" to "clumps",
        "letmedespawn" to "letmedespawn",
        "spark" to "spark",
        "ledger" to "ledger",
        "geyser" to "geyser",
        "floodgate" to "floodgate",
        "viaversion" to "viaversion",
        "viabackwards" to "viabackwards",
        "luckperms" to "luckperms",
        "worldedit" to "worldedit",
        "iris" to "iris"
    )

    fun registerKnownIcon(slug: String, title: String, iconUrl: String) {
        if (iconUrl.isBlank()) return
        if (slug.isNotBlank()) runtimeIconUrls[slug.lowercase(Locale.ROOT)] = iconUrl
        if (title.isNotBlank()) runtimeIconUrls[title.lowercase(Locale.ROOT)] = iconUrl
    }

    private fun diskCacheDir(): File {
        val base = System.getProperty("java.io.tmpdir") ?: "/data/local/tmp"
        return File(base, "minehost_addon_icons_v2").apply { mkdirs() }
    }

    private fun diskCacheFileFor(key: String): File {
        val safe = key.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_").take(96)
        val hash = key.hashCode().toUInt().toString(16)
        return File(diskCacheDir(), "${safe}_$hash.png")
    }

    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }
        val diskFile = diskCacheFileFor("url_$url")
        if (diskFile.isFile && diskFile.length() > 64L) {
            val diskBmp = withContext(Dispatchers.IO) {
                runCatching { decode(diskFile.readBytes()) }.getOrNull()
            }
            if (diskBmp != null) {
                cache.put(url, diskBmp)
                return diskBmp
            }
        }
        val failedAt = failures.get(url)
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < FAILURE_COOLDOWN_MS) return null
        return slots.withPermit {
            cache.get(url)?.let { return@withPermit it }
            val recentFailure = failures.get(url)
            if (recentFailure != null && SystemClock.elapsedRealtime() - recentFailure < FAILURE_COOLDOWN_MS) return@withPermit null
            val bitmap = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    coroutineContext.ensureActive()
                    val address = URL(url)
                    if (address.protocol != "https") return@withContext null
                    connection = address.openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "MineHost/2.5 (Android)")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return@withContext null
                    val context = coroutineContext
                    val bytes = connection.inputStream.use { input ->
                        BoundedInput.read(input, MAX_DOWNLOAD_BYTES) { context.ensureActive() }
                    }
                    context.ensureActive()
                    val decoded = decode(bytes)
                    if (decoded != null) {
                        runCatching { saveBitmapToDisk(decoded, diskFile) }
                    }
                    decoded
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            if (bitmap != null) {
                cache.put(url, bitmap)
                failures.remove(url)
            } else {
                failures.put(url, SystemClock.elapsedRealtime())
            }
            bitmap
        }
    }

    /**
     * Tải biểu tượng thực tế cho Mod hoặc Plugin đã cài đặt (hoặc trong danh mục Gói Mod):
     * 1. Đọc từ bộ nhớ đệm RAM / Đĩa.
     * 2. Giải nén trực tiếp tệp PNG biểu tượng nhúng bên trong tệp .jar (hoạt động 100% offline, <1ms).
     * 3. Tra cứu bảng 70+ Modrinth icon URL đã biết hoặc truy vấn Modrinth API theo SHA-1 / Slug.
     */
    suspend fun loadForAddon(
        jarPath: String? = null,
        baseName: String = "",
        slugHint: String? = null,
        modIdHint: String? = null,
        iconUrlHint: String? = null
    ): Bitmap? {
        val cacheKey = "addon:${jarPath.orEmpty()}:$baseName:${slugHint.orEmpty()}:${modIdHint.orEmpty()}:${iconUrlHint.orEmpty()}"
        cache.get(cacheKey)?.let { return it }

        if (!iconUrlHint.isNullOrBlank()) {
            load(iconUrlHint)?.let { bmp ->
                cache.put(cacheKey, bmp)
                return bmp
            }
        }

        val diskKey = (slugHint?.takeIf { it.isNotBlank() }
            ?: modIdHint?.takeIf { it.isNotBlank() }
            ?: baseName.ifBlank { jarPath.orEmpty() })
        val diskFile = diskCacheFileFor("addon_$diskKey")
        if (diskFile.isFile && diskFile.length() > 64L) {
            val diskBmp = withContext(Dispatchers.IO) {
                runCatching { decode(diskFile.readBytes()) }.getOrNull()
            }
            if (diskBmp != null) {
                cache.put(cacheKey, diskBmp)
                return diskBmp
            }
        }

        // Bước 1: Trích xuất trực tiếp icon PNG nhúng bên trong file .jar trên máy (0 tốn mạng, <1ms)
        if (!jarPath.isNullOrBlank()) {
            val jarBmp = withContext(Dispatchers.IO) {
                extractIconFromJarFile(File(jarPath), diskFile)
            }
            if (jarBmp != null) {
                cache.put(cacheKey, jarBmp)
                return jarBmp
            }
        }

        // Bước 2: Kiểm tra bảng ánh xạ nhanh Modrinth Icon URL (cho 56 Mod, 14 Mod tối ưu & Plugin phổ biến)
        val knownUrl = resolveKnownIconUrl(slugHint, modIdHint, baseName)
        if (!knownUrl.isNullOrBlank()) {
            val bmp = load(knownUrl)
            if (bmp != null) {
                withContext(Dispatchers.IO) { runCatching { saveBitmapToDisk(bmp, diskFile) } }
                cache.put(cacheKey, bmp)
                return bmp
            }
        }

        // Bước 3: Tra cứu động trên Modrinth theo SHA-1 của file .jar hoặc theo tên/slug
        val failedAt = failures.get(cacheKey)
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < FAILURE_COOLDOWN_MS) return null

        val remoteUrl = withContext(Dispatchers.IO) {
            resolveModrinthIconUrlRemotely(jarPath, slugHint, modIdHint, baseName)
        }
        if (!remoteUrl.isNullOrBlank()) {
            val bmp = load(remoteUrl)
            if (bmp != null) {
                withContext(Dispatchers.IO) { runCatching { saveBitmapToDisk(bmp, diskFile) } }
                cache.put(cacheKey, bmp)
                failures.remove(cacheKey)
                return bmp
            }
        }
        failures.put(cacheKey, SystemClock.elapsedRealtime())
        return null
    }

    private fun extractIconFromJarFile(jarFile: File, diskCacheTarget: File): Bitmap? {
        if (!jarFile.isFile || jarFile.length() <= 128L) return null
        return runCatching {
            ZipFile(jarFile).use { zip ->
                val candidatePaths = mutableListOf<String>()

                // 1. Đọc đường dẫn "icon" từ fabric.mod.json hoặc quilt.mod.json
                listOf("fabric.mod.json", "quilt.mod.json").forEach { metaName ->
                    val entry = zip.getEntry(metaName) ?: return@forEach
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    Regex(""""icon"\s*:\s*"([^"]+)"""").findAll(text).forEach { m ->
                        candidatePaths += m.groupValues[1].trim().removePrefix("/")
                    }
                    Regex(""""\d+"\s*:\s*"([^"]+\.png)"""").findAll(text).forEach { m ->
                        candidatePaths += m.groupValues[1].trim().removePrefix("/")
                    }
                }

                // 2. Đọc logoFile từ META-INF/neoforge.mods.toml, META-INF/mods.toml, mcmod.info
                listOf("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "mcmod.info").forEach { tomlName ->
                    val entry = zip.getEntry(tomlName) ?: return@forEach
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    Regex("""logoFile\s*[=:]\s*"([^"]+)"""").findAll(text).forEach { m ->
                        candidatePaths += m.groupValues[1].trim().removePrefix("/")
                    }
                }

                // 3. Các vị trí chuẩn thường gặp trong JAR
                candidatePaths += listOf("icon.png", "logo.png", "pack.png", "plugin-icon.png")

                for (path in candidatePaths.distinct()) {
                    if (path.isBlank()) continue
                    val zipEntry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("./")) ?: continue
                    if (zipEntry.isDirectory || zipEntry.size > MAX_DOWNLOAD_BYTES) continue
                    val bytes = zip.getInputStream(zipEntry).use { ins ->
                        BoundedInput.read(ins, MAX_DOWNLOAD_BYTES) {}
                    }
                    val bmp = decode(bytes)
                    if (bmp != null) {
                        runCatching { saveBitmapToDisk(bmp, diskCacheTarget) }
                        return@use bmp
                    }
                }

                // 4. Quét nhanh các entry dạng assets/*/icon.png hoặc assets/*/logo.png
                val entries = zip.entries()
                var fallbackEntryName: String? = null
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val lower = e.name.lowercase(Locale.ROOT).removePrefix("/")
                    if (!lower.endsWith(".png")) continue
                    if (lower.contains("/textures/item/") || lower.contains("/textures/block/") || lower.contains("/textures/gui/") || lower.contains("/textures/entity/")) {
                        continue
                    }
                    if (lower.endsWith("/icon.png") || lower.endsWith("/logo.png") || lower == "icon.png" || lower == "logo.png") {
                        fallbackEntryName = e.name
                        break
                    }
                }
                if (fallbackEntryName != null) {
                    val e = zip.getEntry(fallbackEntryName)
                    if (e != null && e.size <= MAX_DOWNLOAD_BYTES) {
                        val bytes = zip.getInputStream(e).use { ins ->
                            BoundedInput.read(ins, MAX_DOWNLOAD_BYTES) {}
                        }
                        val bmp = decode(bytes)
                        if (bmp != null) {
                            runCatching { saveBitmapToDisk(bmp, diskCacheTarget) }
                            return@use bmp
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun resolveKnownIconUrl(slugHint: String?, modIdHint: String?, baseName: String): String? {
        val candidates = listOfNotNull(slugHint, modIdHint, baseName)
        for (raw in candidates) {
            val lower = raw.trim().lowercase(Locale.ROOT).removePrefix("-")
            if (lower.isEmpty()) continue
            runtimeIconUrls[lower]?.let { return it }
            KNOWN_MODRINTH_ICON_URLS[lower]?.let { return it }
            val norm = lower.replace("_", "-")
            KNOWN_MODRINTH_ICON_URLS[norm]?.let { return it }
            for ((prefix, targetSlug) in PREFIX_TO_KNOWN_SLUG) {
                if (norm == prefix || norm.startsWith("$prefix-") || norm.startsWith("${prefix}_") || norm.startsWith(prefix)) {
                    KNOWN_MODRINTH_ICON_URLS[targetSlug]?.let { return it }
                }
            }
        }
        return null
    }

    private fun resolveModrinthIconUrlRemotely(
        jarPath: String?,
        slugHint: String?,
        modIdHint: String?,
        baseName: String
    ): String? {
        // 1. Nếu có file .jar thực tế, thử tra cứu chính xác bằng SHA-1 trên Modrinth
        if (!jarPath.isNullOrBlank()) {
            val f = File(jarPath)
            if (f.isFile && f.length() in 256L..(64L * 1024 * 1024)) {
                val sha1 = runCatching { computeSha1Hex(f) }.getOrNull()
                if (!sha1.isNullOrBlank()) {
                    val verBody = fetchTextQuick("https://api.modrinth.com/v2/version_file/$sha1?algorithm=sha1")
                    val projId = verBody?.let { Regex(""""project_id"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
                    if (!projId.isNullOrBlank()) {
                        val projBody = fetchTextQuick("https://api.modrinth.com/v2/project/$projId")
                        val iconUrl = projBody?.let { Regex(""""icon_url"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
                        if (!iconUrl.isNullOrBlank()) return iconUrl
                    }
                }
            }
        }

        // 2. Thử truy vấn trực tiếp theo slug/modId hoặc tìm kiếm tên Mod/Plugin trên Modrinth
        val cleanQuery = cleanAddonQueryName(slugHint ?: modIdHint ?: baseName)
        if (cleanQuery.isNotBlank()) {
            val slugCandidate = cleanQuery.lowercase(Locale.ROOT).replace(" ", "-")
            val projBody = fetchTextQuick("https://api.modrinth.com/v2/project/${URLEncoder.encode(slugCandidate, "UTF-8")}")
            val directIcon = projBody?.let { Regex(""""icon_url"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
            if (!directIcon.isNullOrBlank()) return directIcon

            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchBody = fetchTextQuick("https://api.modrinth.com/v2/search?query=$encoded&limit=1")
            val searchIcon = searchBody?.let { Regex(""""icon_url"\s*:\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }
            if (!searchIcon.isNullOrBlank()) return searchIcon
        }
        return null
    }

    private fun cleanAddonQueryName(raw: String): String {
        val noExt = raw.removePrefix("-")
            .removeSuffix(".jar.disabled")
            .removeSuffix(".disabled")
            .removeSuffix(".jar")
        val stripped = noExt
            .replace(Regex("(?i)[-_](fabric|forge|neoforge|quilt|paper|spigot|bukkit|bundled).*$"), "")
            .replace(Regex("(?i)[-_]?mc?\\d+\\.\\d+.*$"), "")
            .replace(Regex("[-_]v?\\d+\\.\\d+.*$"), "")
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
        return stripped.ifBlank { noExt.take(32) }
    }

    private fun fetchTextQuick(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MineHost/2.5 (Android)")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun computeSha1Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { ins ->
            val buf = ByteArray(16384)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun saveBitmapToDisk(bitmap: Bitmap, target: File) {
        val out = ByteArrayOutputStream()
        if (bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)) {
            target.parentFile?.mkdirs()
            target.writeBytes(out.toByteArray())
        }
    }

    suspend fun loadPlayerHead(playerName: String): Bitmap? {
        val clean = playerName.trim().removePrefix(".").replace(Regex("[^a-zA-Z0-9_]"), "")
        if (clean.isBlank()) return null
        val primaryUrl = "https://mc-heads.net/avatar/$clean/64"
        load(primaryUrl)?.let { return it }
        val fallbackUrl = "https://minotar.net/helm/$clean/64.png"
        return load(fallbackUrl)
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 16384 || bounds.outHeight > 16384) return null
        var sample = 1
        while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }
}