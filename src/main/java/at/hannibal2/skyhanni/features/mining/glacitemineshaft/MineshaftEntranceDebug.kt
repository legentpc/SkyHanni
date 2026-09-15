package at.hannibal2.skyhanni.features.mining.glacitemineshaft

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.api.hypixelapi.HypixelLocationApi
import at.hannibal2.skyhanni.events.minecraft.KeyDownEvent
import at.hannibal2.skyhanni.events.minecraft.packet.PacketReceivedEvent
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ChatUtils
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SkyHanniLogger
import at.hannibal2.skyhanni.utils.compat.MinecraftCompat
import at.hannibal2.skyhanni.utils.system.PlatformUtils
import at.hannibal2.skyhanni.utils.toLorenzVec
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import org.lwjgl.glfw.GLFW

@SkyHanniModule
object MineshaftEntranceDebug {

    private const val MAX_ENTRIES = 2_000

    private val config get() = SkyHanniMod.feature.mining.glaciteMineshaft.mineshaftWaypoints
    private val captureLock = Any()
    private val logger = SkyHanniLogger("debug/mineshaft_entrance")
    private var activeCapture: Capture? = null

    private class Capture(
        val startedAt: SimpleTimeMark,
        val startState: String,
    ) {
        val entries = mutableListOf<String>()
        var droppedEntries = 0
    }

    fun matchesKey(keyCode: Int): Boolean {
        val configuredKey = config.entranceDebugKey
        return configuredKey != GLFW.GLFW_KEY_UNKNOWN && keyCode == configuredKey
    }

    fun record(message: () -> String) {
        synchronized(captureLock) {
            val capture = activeCapture ?: return

            if (capture.entries.size >= MAX_ENTRIES) {
                capture.droppedEntries++
                return
            }

            val sequence = capture.entries.size + 1
            val elapsed = capture.startedAt.passedSince().inWholeMilliseconds
            val thread = Thread.currentThread().name
            capture.entries.add("$sequence +${elapsed}ms [$thread] ${message()}")
        }
    }

    @HandleEvent
    private fun onKeyDown(event: KeyDownEvent) {
        if (MinecraftCompat.screen != null) return
        if (!matchesKey(event.keyCode)) return

        val now = SimpleTimeMark.now()
        val state = currentState()

        val completedCapture = synchronized(captureLock) {
            val current = activeCapture
            activeCapture = if (current == null) Capture(now, state) else null
            current
        }

        if (completedCapture == null) {
            ChatUtils.chat(
                "Entrance debug recording started. Enter the Mineshaft, " +
                    "then press the same key again to stop."
            )
            return
        }

        val report = buildList {
            add("Mineshaft entrance debug")
            add("SkyHanni=${SkyHanniMod.VERSION} Minecraft=${PlatformUtils.MC_VERSION}")
            add("START time=${completedCapture.startedAt} ${completedCapture.startState}")
            addAll(completedCapture.entries)
            add("STOP time=$now $state")

            if (completedCapture.droppedEntries > 0) {
                add("TRUNCATED droppedEntries=${completedCapture.droppedEntries} limit=$MAX_ENTRIES")
            }
        }

        logger.log(report.joinToString("\n"))

        ChatUtils.clickToClipboard(
            "Entrance debug stopped. Click to copy the report.",
            report,
        )

        if (completedCapture.droppedEntries > 0) {
            ChatUtils.chat(
                "§cEntrance debug reached its $MAX_ENTRIES-entry limit. " +
                    "The report is incomplete; retry with a shorter recording."
            )
        }
    }

    @HandleEvent(priority = HandleEvent.HIGHEST)
    private fun onPositionPacket(event: PacketReceivedEvent) {
        val packet = event.packet as? ClientboundPlayerPositionPacket ?: return

        record {
            "POSITION_RECEIVED packetRef=${System.identityHashCode(packet)} " +
                "position=${packet.change.position.toLorenzVec()} " +
                "yaw=${packet.change.yRot} relatives=${packet.relatives}"
        }
    }

    private fun currentState(): String {
        val playerLocation = LocationUtils.playerLocationOrNull()
        val world = MinecraftCompat.localWorldOrNull

        return "island=${HypixelLocationApi.island} " +
            "server=${HypixelLocationApi.serverId} " +
            "world=${System.identityHashCode(world)} " +
            "player=$playerLocation " +
            "waypointsEnabled=${config.enabled} " +
            "entranceEnabled=${config.entranceLocation} " +
            "ladderEnabled=${config.ladderLocation}"
    }
}
