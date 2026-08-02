package th.in.midnight_network.greenchunk.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public record PluginConfig(ItemSettings item, DialogSettings dialog,
                           Visualization visualization, Messages messages) {
    public record ItemSettings(boolean enabled, Material material) {}

    public record DialogSettings(int width, int height, Component title, String cellSymbol,
                                 Component closeButton, Component closeTooltip) {}

    public record Visualization(int radius, long refreshTicks, Material blockMaterial,
                                float viewRange) {}

    public record Messages(String inSlimeChunk, String notInSlimeChunk,
                           Component noPermission, Component playerOnly, Component mapDisabled,
                           Component reloadSuccess, Component reloadFailure,
                           Component slimeChunk, Component normalChunk, Component currentChunk,
                           Component visualizationEnabled, Component visualizationDisabled) {}
}
