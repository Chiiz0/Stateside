package app.stateside.env.model

/**
 * 内置美国目标区域与运营商显示预设。
 * 默认使用「保持原运营商显示（推荐）」：仅将电话卡国家码（SIM Country ISO）覆盖为美国 `us`（与 Simlet 默认行为一致），
 * 完全不修改状态栏与通知栏原有的「中国电信 / 中国联通 / 中国移动」显示；同时保留可选模拟 T-Mobile / AT&T / Verizon 名称。
 */
enum class UsCarrierPreset(
    val id: String,
    val displayName: String,
    val chipLabel: String,
    val carrierNameOverride: String,
    val countryIso: String,
    val sampleMccMnc: String
) {
    US_KEEP_CARRIER(
        id = "us_keep_carrier",
        displayName = "美国 · 保持原状态栏运营商（默认推荐）",
        chipLabel = "保持原运营商（推荐）",
        carrierNameOverride = "",
        countryIso = "us",
        sampleMccMnc = "310260"
    ),
    CUSTOM(
        id = "us_custom_carrier",
        displayName = "美国 · 自定义运营商显示（如 X）",
        chipLabel = "自定义（如 X）",
        carrierNameOverride = "X",
        countryIso = "us",
        sampleMccMnc = "310260"
    ),
    TMOBILE(
        id = "tmobile_us",
        displayName = "美国 · T-Mobile（同时覆盖运营商名）",
        chipLabel = "T-Mobile",
        carrierNameOverride = "T-Mobile",
        countryIso = "us",
        sampleMccMnc = "310260"
    ),
    ATT(
        id = "att_us",
        displayName = "美国 · AT&T（同时覆盖运营商名）",
        chipLabel = "AT&T",
        carrierNameOverride = "AT&T",
        countryIso = "us",
        sampleMccMnc = "310410"
    ),
    VERIZON(
        id = "verizon_us",
        displayName = "美国 · Verizon（同时覆盖运营商名）",
        chipLabel = "Verizon",
        carrierNameOverride = "Verizon",
        countryIso = "us",
        sampleMccMnc = "311480"
    );

    val keepsOriginalCarrierName: Boolean
        get() = carrierNameOverride.isBlank()

    companion object {
        val OVERRIDE_BRAND_NAMES: Set<String> = entries
            .mapNotNull { it.carrierNameOverride.takeIf(String::isNotBlank) }
            .toSet()

        fun fromId(id: String?): UsCarrierPreset =
            entries.firstOrNull { it.id == id } ?: US_KEEP_CARRIER
    }
}

/**
 * 开箱默认自动勾选的「美境核心英语应用组」：
 * 涵盖 TikTok、X、Claude、ChatGPT 四大高频海外与 AI 工具，
 * 安装后无需用户在几百个系统应用里翻找，默认全部勾选并驱动为纯正美式英语（en-US），同时支持按需单独取消。
 */
enum class CoreSuiteApp(
    val id: String,
    val title: String,
    val roleDesc: String,
    val packageNames: List<String>
) {
    TIKTOK(
        id = "suite_tiktok",
        title = "TikTok",
        roleDesc = "短视频 · 解除国内卡锁区并设为美式英语",
        packageNames = listOf(
            "com.zhiliaoapp.musically",    // 欧美/全球版包名
            "com.ss.android.ugc.trill",    // 亚太版包名
            "com.zhiliaoapp.musically.go"  // TikTok Lite
        )
    ),
    X_TWITTER(
        id = "suite_x",
        title = "X",
        roleDesc = "全球实时动态 · 强制纯正美式英语界面",
        packageNames = listOf("com.twitter.android")
    ),
    CLAUDE(
        id = "suite_claude",
        title = "Claude",
        roleDesc = "Anthropic AI · 美区卡环境与英文原生交互",
        packageNames = listOf("com.anthropic.claude")
    ),
    CHATGPT(
        id = "suite_chatgpt",
        title = "ChatGPT",
        roleDesc = "OpenAI 助手 · 美区卡环境与英文原生交互",
        packageNames = listOf("com.openai.chatgpt")
    );

    companion object {
        val ALL_BUILTIN_PACKAGES: Set<String> =
            entries.flatMap { it.packageNames }.toSet()
    }
}

object StatesideDefaults {
    const val US_ENGLISH_TAG = "en-US"

    /**
     * 计算当前应当被驱动为美式英语（en-US）的全部目标包名：
     * 默认启用的核心应用组（排除用户手动关掉的项） + 用户从抽屉额外添加的其他应用。
     */
    fun buildTargetEnglishPackages(
        disabledSuiteIds: Set<String>,
        extraPackages: Set<String>
    ): List<String> {
        val suitePkgs = CoreSuiteApp.entries
            .filter { it.id !in disabledSuiteIds }
            .flatMap { it.packageNames }
        return (suitePkgs + extraPackages)
            .map { it.trim() }
            .filter { it.isNotEmpty() && isValidPackageName(it) }
            .distinct()
    }

    fun isValidPackageName(pkg: String): Boolean =
        pkg.length in 3..180 && pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' }
}

/**
 * 解析通知栏或向导输入的无线调试配对信息：
 * - 普通家庭 Wi-Fi（mDNS 已自动捕获端口）：直接输入 `123456`
 * - 校园网 AP 隔离 / 严格 VPN 拦截组播时：支持输入 `38941 123456` 或 `192.168.1.8:38941 123456`
 */
data class ParsedPairingInput(
    val port: Int,
    val sixDigitCode: String
)

object PairingInputParser {
    private val ipv4WithPortRegex = Regex("""(?:\d{1,3}\.){3}\d{1,3}:(\d{2,5})""")
    private val numberTokensRegex = Regex("""\d+""")

    fun parse(rawInput: String, fallbackPort: Int): ParsedPairingInput? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val ipPortMatch = ipv4WithPortRegex.find(trimmed)
        if (ipPortMatch != null) {
            val port = ipPortMatch.groupValues[1].toIntOrNull() ?: 0
            val remainder = trimmed.removeRange(ipPortMatch.range)
            val code = numberTokensRegex.findAll(remainder)
                .map { it.value }
                .firstOrNull { it.length == 6 }
            if (port in 1024..65535 && code != null) {
                return ParsedPairingInput(port = port, sixDigitCode = code)
            }
        }

        val tokens = numberTokensRegex.findAll(trimmed).map { it.value }.toList()
        if (tokens.size >= 2) {
            val firstAsPort = tokens[0].toIntOrNull()
            if (firstAsPort != null && firstAsPort in 1024..65535 && tokens[1].length == 6) {
                return ParsedPairingInput(port = firstAsPort, sixDigitCode = tokens[1])
            }
            val secondAsPort = tokens[1].toIntOrNull()
            if (tokens[0].length == 6 && secondAsPort != null && secondAsPort in 1024..65535) {
                return ParsedPairingInput(port = secondAsPort, sixDigitCode = tokens[0])
            }
        }

        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length == 6 && fallbackPort in 1..65535) {
            return ParsedPairingInput(port = fallbackPort, sixDigitCode = digitsOnly)
        }
        return null
    }
}

/**
 * 基于 SIM 卡硬件只读 MCC/MNC（TelephonyManager.getSimOperator）反推真实归属地 ISO 与默认运营商名称，
 * 确保执行「恢复国内默认状态」时连空白 EF_SPN 的国产 SIM 卡也能干净还原 gsm.sim.operator.alpha。
 */
object MccIsoResolver {
    private val mccToIso = mapOf(
        "460" to "cn", // 中国大陆（移动/联通/电信/广电）
        "461" to "cn",
        "454" to "hk", // 中国香港
        "455" to "mo", // 中国澳门
        "466" to "tw", // 中国台湾
        "310" to "us",
        "311" to "us",
        "312" to "us",
        "313" to "us",
        "316" to "us",
        "440" to "jp",
        "441" to "jp",
        "450" to "kr",
        "234" to "gb",
        "235" to "gb",
        "525" to "sg",
        "505" to "au",
        "302" to "ca",
        "262" to "de",
        "208" to "fr"
    )

    private val mccMncToCarrierName = mapOf(
        "46000" to "中国移动",
        "46002" to "中国移动",
        "46004" to "中国移动",
        "46007" to "中国移动",
        "46008" to "中国移动",
        "46001" to "中国联通",
        "46006" to "中国联通",
        "46009" to "中国联通",
        "46003" to "中国电信",
        "46005" to "中国电信",
        "46011" to "中国电信",
        "46015" to "中国广电",
        "45400" to "CSL",
        "45403" to "3 HK",
        "45406" to "SmarTone",
        "45412" to "CMHK",
        "45502" to "CTM",
        "46601" to "FarEasTone",
        "46692" to "Chunghwa Telecom",
        "46697" to "Taiwan Mobile",
        "310260" to "T-Mobile",
        "310410" to "AT&T",
        "311480" to "Verizon"
    )

    fun resolveRealIsoFromMccMnc(mccMnc: String?): String {
        val clean = mccMnc?.trim().orEmpty()
        if (clean.length >= 3) {
            mccToIso[clean.substring(0, 3)]?.let { return it }
        }
        return "cn"
    }

    fun resolveCarrierDisplayName(mccMnc: String?): String {
        val clean = mccMnc?.trim().orEmpty()
        if (clean.length >= 5) {
            mccMncToCarrierName[clean.substring(0, 5)]?.let { return it }
        }
        return when (resolveRealIsoFromMccMnc(clean)) {
            "cn" -> "中国运营商"
            "hk" -> "HK Carrier"
            "mo" -> "MO Carrier"
            "tw" -> "TW Carrier"
            "us" -> "US Carrier"
            else -> ""
        }
    }

    fun formatChineseCountryLabel(iso: String): String = when (iso.lowercase()) {
        "cn" -> "中国大陆 (CN)"
        "hk" -> "中国香港 (HK)"
        "mo" -> "中国澳门 (MO)"
        "tw" -> "中国台湾 (TW)"
        "us" -> "美国 (US)"
        "jp" -> "日本 (JP)"
        "kr" -> "韩国 (KR)"
        "gb" -> "英国 (GB)"
        "sg" -> "新加坡 (SG)"
        else -> iso.uppercase()
    }
}
