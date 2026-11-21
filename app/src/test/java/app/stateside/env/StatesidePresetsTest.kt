package app.stateside.env

import app.stateside.env.data.UserSettings
import app.stateside.env.model.CoreSuiteApp
import app.stateside.env.model.MccIsoResolver
import app.stateside.env.model.PairingInputParser
import app.stateside.env.model.StatesideDefaults
import app.stateside.env.model.UsCarrierPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatesidePresetsTest {

    @Test
    fun allFourCoreAppsAreEnabledByDefault() {
        val targets = StatesideDefaults.buildTargetEnglishPackages(
            disabledSuiteIds = emptySet(),
            extraPackages = emptySet()
        )
        assertTrue(targets.contains("com.zhiliaoapp.musically"))
        assertTrue(targets.contains("com.zhiliaoapp.musically.go"))
        assertTrue(targets.contains("com.twitter.android"))
        assertTrue(targets.contains("com.anthropic.claude"))
        assertTrue(targets.contains("com.openai.chatgpt"))
    }

    @Test
    fun disablingOneCoreAppKeepsOthersActiveAndSanitizesShellChars() {
        val targets = StatesideDefaults.buildTargetEnglishPackages(
            disabledSuiteIds = setOf(CoreSuiteApp.X_TWITTER.id),
            extraPackages = setOf("com.reddit.frontpage", "bad;rm -rf")
        )
        assertFalse(targets.contains("com.twitter.android"))
        assertTrue(targets.contains("com.anthropic.claude"))
        assertTrue(targets.contains("com.openai.chatgpt"))
        assertTrue(targets.contains("com.reddit.frontpage"))
        assertFalse(targets.contains("bad;rm -rf"))
    }

    @Test
    fun chinaMccCarrierNameAndUsPresetVerifyCleanly() {
        assertEquals("cn", MccIsoResolver.resolveRealIsoFromMccMnc("46000"))
        assertEquals("中国移动", MccIsoResolver.resolveCarrierDisplayName("46000"))
        assertEquals("中国联通", MccIsoResolver.resolveCarrierDisplayName("46001"))
        assertEquals("中国电信", MccIsoResolver.resolveCarrierDisplayName("46011"))
        assertEquals("中国广电", MccIsoResolver.resolveCarrierDisplayName("46015"))
        val preset = UsCarrierPreset.fromId(null)
        assertEquals(UsCarrierPreset.US_KEEP_CARRIER, preset)
        assertTrue(preset.keepsOriginalCarrierName)
        assertEquals("", preset.carrierNameOverride)
        assertEquals("us", preset.countryIso)
        assertEquals(UsCarrierPreset.TMOBILE, UsCarrierPreset.fromId("tmobile_us"))
        assertFalse(UsCarrierPreset.TMOBILE.keepsOriginalCarrierName)
        assertEquals("en-US", StatesideDefaults.US_ENGLISH_TAG)
    }

    @Test
    fun pairingInputParserSupportsMdnsAndManualPortFormats() {
        val autoMdns = PairingInputParser.parse(" 654321 ", fallbackPort = 39812)
        assertNotNull(autoMdns)
        assertEquals(39812, autoMdns!!.port)
        assertEquals("654321", autoMdns.sixDigitCode)

        val portAndCode = PairingInputParser.parse("38941 123456", fallbackPort = 0)
        assertNotNull(portAndCode)
        assertEquals(38941, portAndCode!!.port)
        assertEquals("123456", portAndCode.sixDigitCode)

        val ipPortAndCode = PairingInputParser.parse("192.168.1.25:41234 908172", fallbackPort = 0)
        assertNotNull(ipPortAndCode)
        assertEquals(41234, ipPortAndCode!!.port)
        assertEquals("908172", ipPortAndCode.sixDigitCode)

        assertNull(PairingInputParser.parse("12345", fallbackPort = 39812))
        assertNull(PairingInputParser.parse("123456", fallbackPort = 0))
    }

    @Test
    fun sessionReadinessNeverFalsePositivesWithoutPhonePermission() {
        val unapplied = UserSettings(
            isAdbPaired = true,
            isStatesideDesired = false,
            appliedBootCount = -1
        )
        // 未授予电话状态权限且本次开机未执行过写入时，绝不能因 simCardsCount==0 误判为已生效
        assertFalse(
            unapplied.isReadyForCurrentSession(
                hasPrivilegeChannel = true,
                hasPhonePermission = false,
                simCardsCount = 0,
                allSimsUsSpoofed = false,
                currentBootCount = 12
            )
        )

        val appliedThisBoot = unapplied.copy(
            isStatesideDesired = true,
            appliedBootCount = 12,
            lastAppliedTimestamp = 1000L
        )
        assertTrue(
            appliedThisBoot.isReadyForCurrentSession(
                hasPrivilegeChannel = true,
                hasPhonePermission = false,
                simCardsCount = 0,
                allSimsUsSpoofed = false,
                currentBootCount = 12
            )
        )
        // 手机重启后 BOOT_COUNT 自增为 13，必须判定为待恢复
        assertFalse(
            appliedThisBoot.isReadyForCurrentSession(
                hasPrivilegeChannel = true,
                hasPhonePermission = false,
                simCardsCount = 0,
                allSimsUsSpoofed = false,
                currentBootCount = 13
            )
        )
    }
}
