package at.hannibal2.skyhanni.config.features.mining.glacite

import at.hannibal2.skyhanni.config.FeatureToggle
import com.google.gson.annotations.Expose
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.lwjgl.glfw.GLFW

class MineshaftWaypointsConfig {
    @Expose
    @ConfigOption(name = "Enabled", desc = "Enable features related to the Glacite Mineshaft.")
    @ConfigEditorBoolean
    @FeatureToggle
    var enabled: Boolean = false

    @Expose
    @ConfigOption(name = "Entrance Location", desc = "Mark the location of the entrance with a waypoint.")
    @ConfigEditorBoolean
    var entranceLocation: Boolean = false

    @Expose
    @ConfigOption(
        name = "Ladder Location",
        desc = "Mark the location of the ladders at the bottom of the entrance with a waypoint."
    )
    @ConfigEditorBoolean
    var ladderLocation: Boolean = false

    @Expose
    @ConfigOption(
        name = "Entrance Debug Capture",
        desc = "Press before entering a Mineshaft to start recording entrance diagnostics. " +
            "Press again to stop and get a copyable report."
    )
    @ConfigEditorKeybind(defaultKey = GLFW.GLFW_KEY_UNKNOWN)
    var entranceDebugKey: Int = GLFW.GLFW_KEY_UNKNOWN
}
